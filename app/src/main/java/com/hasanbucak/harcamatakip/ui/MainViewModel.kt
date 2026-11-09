package com.hasanbucak.harcamatakip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hasanbucak.harcamatakip.HarcamaApp
import com.hasanbucak.harcamatakip.data.IngestResult
import com.hasanbucak.harcamatakip.data.local.BankRuleEntity
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.PendingReviewEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.data.local.UnknownCardPolicy
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.util.ListenerHealthSnapshot
import com.hasanbucak.harcamatakip.util.ListenerHealthTracker
import com.hasanbucak.harcamatakip.util.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

sealed interface GroupFilter {
    data object All : GroupFilter
    data object Ungrouped : GroupFilter
    data class Group(val groupId: String) : GroupFilter
}

data class CurrencySummary(
    val currency: String,
    val grossExpense: Double,
    val totalRefund: Double,
    val netExpense: Double
)

data class BankBreakdownItem(
    val bankCode: String,
    val bankName: String,
    val colorHex: Long,
    val grossExpense: Double,
    val totalRefund: Double,
    val netExpense: Double,
    val ratio: Float,
    val count: Int
)

data class CategoryBreakdownItem(
    val category: String,
    val grossExpense: Double,
    val totalRefund: Double,
    val amount: Double, // netExpense
    val ratio: Float,
    val count: Int
)

data class DashboardUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedGroupFilter: GroupFilter = GroupFilter.All,
    val selectedBankCode: String? = null,
    val selectedCardKey: String? = null,
    val selectedCategory: String? = null,
    val useStatementCycle: Boolean = false,
    val includeDemoRecords: Boolean = false,
    val searchQuery: String = "",
    val periodLabel: String = "",
    val cardGroups: List<CardGroupEntity> = emptyList(),
    val bankRules: Map<String, UnknownCardPolicy> = emptyMap(),
    val cards: List<CardEntity> = emptyList(),
    val cardPeriodTotalsTl: Map<String, Double> = emptyMap(),
    val groupPeriodTotalsTl: Map<String?, Double> = emptyMap(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val demoTransactionCount: Int = 0,
    val currencySummaries: List<CurrencySummary> = emptyList(),
    val tlBankBreakdown: List<BankBreakdownItem> = emptyList(),
    val tlCategoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val pendingReviews: List<PendingReviewEntity> = emptyList(),
    val recentLogs: List<RawNotificationLogEntity> = emptyList(),
    val listenerHealth: ListenerHealthSnapshot = ListenerHealthSnapshot(),
    val lastFeedbackMessage: String? = null
)

private data class FilterState(
    val month: YearMonth = YearMonth.now(),
    val groupFilter: GroupFilter = GroupFilter.All,
    val bankCode: String? = null,
    val cardKey: String? = null,
    val category: String? = null,
    val useStatementCycle: Boolean = false,
    val includeDemoRecords: Boolean = false,
    val searchQuery: String = ""
)

private data class DataStreamsBundle(
    val allTx: List<TransactionEntity>,
    val cards: List<CardEntity>,
    val groups: List<CardGroupEntity>,
    val rules: List<BankRuleEntity>,
    val pending: List<PendingReviewEntity>
)

private data class SampleNotification(
    val bank: BankProvider,
    val channel: String,
    val tag: String,
    val title: String,
    val body: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as HarcamaApp).repository

    private val filterStateFlow = MutableStateFlow(FilterState())
    private val feedbackFlow = MutableStateFlow<String?>(null)

    private var sampleIndex = 0

    init {
        ListenerHealthTracker.load(application)
    }

    private val sampleMessages: List<SampleNotification> = listOf(
        SampleNotification(
            bank = BankProvider.YAPI_KREDI,
            channel = "Demo (Yapı Kredi)",
            tag = "DEMO_YKB",
            title = "Harcama Bilgisi",
            body = "4543****4821 nolu kartınızla 09.11.2026 14:32 tarihinde MİGROS TİCARET A.Ş. işyerinden 645,50 TL tutarında harcama yapılmıştır. Güncel kullanılabilir limitiniz 84.354,50 TL'dir."
        ),
        SampleNotification(
            bank = BankProvider.GARANTI,
            channel = "Demo (Garanti BBVA)",
            tag = "DEMO_GARANTI",
            title = "Garanti BBVA",
            body = "Bonus kartınızla 1234 ile biten karttan OPET İSTASYONU işyerinden 1.450,00 TL harcama yaptınız."
        ),
        SampleNotification(
            bank = BankProvider.TOM_BANK,
            channel = "Demo (TOM Bank)",
            tag = "DEMO_TOM",
            title = "TOMBank",
            body = "*1907 nolu Hadi Kredi Kartınızla TRENDYOL.COM işyerinden 3 taksitli 2.490,00 TL harcama yapılmıştır."
        ),
        SampleNotification(
            bank = BankProvider.AKBANK,
            channel = "Demo (Akbank)",
            tag = "DEMO_AKBANK",
            title = "Jüzdan",
            body = "8812 ile biten Axess kartınızla STARBUCKS işyerinden 210,00 TL harcama yaptınız, 5,00 TL chip-para kazandınız."
        ),
        SampleNotification(
            bank = BankProvider.YAPI_KREDI,
            channel = "Demo (World Mobil)",
            tag = "DEMO_WORLD",
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile STEAM GAMES işyerinden 14.99 USD harcama yapılmıştır."
        )
    )

    private val dbBundleFlow = combine(
        repository.transactionsFlow,
        repository.cardsFlow,
        repository.cardGroupsFlow,
        repository.bankRulesFlow,
        repository.pendingReviewsFlow
    ) { allTx, cards, groups, rules, pending ->
        DataStreamsBundle(allTx, cards, groups, rules, pending)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        filterStateFlow,
        dbBundleFlow,
        repository.recentLogsFlow,
        ListenerHealthTracker.state,
        feedbackFlow
    ) { filter, bundle, logs, health, feedback ->
        val zone = ZoneId.systemDefault()
        val selectedCardEntity = bundle.cards.find { it.cardKey == filter.cardKey }
        val statementDay = selectedCardEntity?.statementDay ?: 1

        val (startMillis, endMillis, periodLabel) = computePeriodBounds(
            month = filter.month,
            useStatementCycle = filter.useStatementCycle && selectedCardEntity != null && statementDay > 1,
            statementDay = statementDay,
            zone = zone
        )

        val demoCount = bundle.allTx.count { it.isDemo }
        val baseVisibleTx = if (filter.includeDemoRecords) {
            bundle.allTx
        } else {
            bundle.allTx.filter { !it.isDemo }
        }

        val txInPeriod = baseVisibleTx.filter { tx ->
            tx.timestamp in startMillis until endMillis
        }

        // Totals per group in period (TL net) — satisfies All = Sum(Groups) + Ungrouped
        val groupTotalsTl = txInPeriod
            .filter { it.currency == "TL" }
            .groupBy { it.groupId }
            .mapValues { (_, list) ->
                list.sumOf { tx ->
                    if (tx.transactionType == ParsedTransactionType.REFUND.name) -tx.amount else tx.amount
                }
            }

        val txMatchingGroup = txInPeriod.filter { tx ->
            when (val gf = filter.groupFilter) {
                is GroupFilter.All -> true
                is GroupFilter.Ungrouped -> tx.groupId == null
                is GroupFilter.Group -> tx.groupId == gf.groupId
            }
        }

        val cardTotalsTl = txMatchingGroup
            .filter { it.currency == "TL" }
            .groupBy { it.cardKey }
            .mapValues { (_, list) ->
                list.sumOf { tx ->
                    if (tx.transactionType == ParsedTransactionType.REFUND.name) -tx.amount else tx.amount
                }
            }

        // Bank breakdown reflects active Period + Group (before single-bank drilldown so user can switch banks easily)
        val tlInGroup = txMatchingGroup.filter { it.currency == "TL" }
        val rawBankItems = tlInGroup
            .groupBy { it.bankCode }
            .map { (bCode, list) ->
                val gross = list.filter { it.transactionType == ParsedTransactionType.EXPENSE.name }.sumOf { it.amount }
                val refund = list.filter { it.transactionType == ParsedTransactionType.REFUND.name }.sumOf { it.amount }
                val net = gross - refund
                val provider = BankProvider.fromCode(bCode)
                BankBreakdownItem(
                    bankCode = bCode,
                    bankName = list.firstOrNull()?.bankName ?: provider.displayName,
                    colorHex = provider.defaultColorHex,
                    grossExpense = gross,
                    totalRefund = refund,
                    netExpense = net,
                    ratio = 0f,
                    count = list.size
                )
            }
            .sortedByDescending { it.netExpense }

        val positiveBankSum = rawBankItems.sumOf { it.netExpense.coerceAtLeast(0.0) }
        val bankBreakdown = rawBankItems.map { item ->
            val r = if (positiveBankSum > 0.0) {
                (item.netExpense.coerceAtLeast(0.0) / positiveBankSum).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            item.copy(ratio = r)
        }

        // Apply Bank + Card + Search filters for Category breakdown
        val queryTrimmed = filter.searchQuery.trim()
        val txBeforeCategoryFilter = txMatchingGroup.filter { tx ->
            val bankOk = filter.bankCode == null || tx.bankCode == filter.bankCode
            val cardOk = filter.cardKey == null || tx.cardKey == filter.cardKey
            val queryOk = queryTrimmed.isEmpty() ||
                tx.merchant.contains(queryTrimmed, ignoreCase = true) ||
                tx.category.contains(queryTrimmed, ignoreCase = true) ||
                tx.bankName.contains(queryTrimmed, ignoreCase = true) ||
                tx.note.contains(queryTrimmed, ignoreCase = true) ||
                tx.cardLast4.contains(queryTrimmed, ignoreCase = true)
            bankOk && cardOk && queryOk
        }

        val tlForCategories = txBeforeCategoryFilter.filter { it.currency == "TL" }
        val rawCategoryItems = tlForCategories
            .groupBy { it.category }
            .map { (cat, list) ->
                val gross = list.filter { it.transactionType == ParsedTransactionType.EXPENSE.name }.sumOf { it.amount }
                val refund = list.filter { it.transactionType == ParsedTransactionType.REFUND.name }.sumOf { it.amount }
                val net = gross - refund
                CategoryBreakdownItem(
                    category = cat,
                    grossExpense = gross,
                    totalRefund = refund,
                    amount = net,
                    ratio = 0f,
                    count = list.size
                )
            }
            .filter { it.grossExpense > 0.0 || it.totalRefund > 0.0 }
            .sortedByDescending { it.amount }

        val positiveCatSum = rawCategoryItems.sumOf { it.amount.coerceAtLeast(0.0) }
        val categoryBreakdown = rawCategoryItems.map { item ->
            val r = if (positiveCatSum > 0.0) {
                (item.amount.coerceAtLeast(0.0) / positiveCatSum).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            item.copy(ratio = r)
        }

        val filtered = if (filter.category == null) {
            txBeforeCategoryFilter
        } else {
            txBeforeCategoryFilter.filter { it.category == filter.category }
        }

        val summaries = filtered
            .groupBy { it.currency }
            .map { (currency, list) ->
                val gross = list
                    .filter { it.transactionType == ParsedTransactionType.EXPENSE.name }
                    .sumOf { it.amount }
                val refund = list
                    .filter { it.transactionType == ParsedTransactionType.REFUND.name }
                    .sumOf { it.amount }
                CurrencySummary(
                    currency = currency,
                    grossExpense = gross,
                    totalRefund = refund,
                    netExpense = gross - refund
                )
            }
            .sortedBy { if (it.currency == "TL") 0 else 1 }

        val ruleMap = bundle.rules.associate {
            it.bankCode to UnknownCardPolicy.fromCode(it.unknownCardPolicy)
        }

        DashboardUiState(
            selectedMonth = filter.month,
            selectedGroupFilter = filter.groupFilter,
            selectedBankCode = filter.bankCode,
            selectedCardKey = filter.cardKey,
            selectedCategory = filter.category,
            useStatementCycle = filter.useStatementCycle,
            includeDemoRecords = filter.includeDemoRecords,
            searchQuery = filter.searchQuery,
            periodLabel = periodLabel,
            cardGroups = bundle.groups,
            bankRules = ruleMap,
            cards = bundle.cards,
            cardPeriodTotalsTl = cardTotalsTl,
            groupPeriodTotalsTl = groupTotalsTl,
            filteredTransactions = filtered,
            demoTransactionCount = demoCount,
            currencySummaries = summaries,
            tlBankBreakdown = bankBreakdown,
            tlCategoryBreakdown = categoryBreakdown,
            pendingReviews = bundle.pending,
            recentLogs = logs,
            listenerHealth = health,
            lastFeedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    private fun computePeriodBounds(
        month: YearMonth,
        useStatementCycle: Boolean,
        statementDay: Int,
        zone: ZoneId
    ): Triple<Long, Long, String> {
        val monthNames = arrayOf(
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
        )
        val monthTitle = "${monthNames[month.monthValue - 1]} ${month.year}"

        if (!useStatementCycle) {
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return Triple(start, end, monthTitle)
        }

        val prevMonth = month.minusMonths(1)
        val startDay = statementDay.coerceAtMost(prevMonth.lengthOfMonth())
        val endDay = statementDay.coerceAtMost(month.lengthOfMonth())

        val startDate: LocalDate = prevMonth.atDay(startDay)
        val endDateExclusive: LocalDate = month.atDay(endDay)

        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val label = "$monthTitle Dönemi (${startDate.dayOfMonth}.${startDate.monthValue} - ${endDateExclusive.dayOfMonth}.${endDateExclusive.monthValue})"
        return Triple(start, end, label)
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }

    fun previousMonth() {
        filterStateFlow.value = filterStateFlow.value.copy(month = filterStateFlow.value.month.minusMonths(1))
    }

    fun nextMonth() {
        filterStateFlow.value = filterStateFlow.value.copy(month = filterStateFlow.value.month.plusMonths(1))
    }

    fun selectGroupFilter(groupFilter: GroupFilter) {
        filterStateFlow.value = filterStateFlow.value.copy(
            groupFilter = groupFilter,
            cardKey = null,
            useStatementCycle = false
        )
    }

    fun selectBankFilter(bankCode: String?) {
        val current = filterStateFlow.value
        val toggled = if (current.bankCode == bankCode && bankCode != null) null else bankCode
        filterStateFlow.value = current.copy(
            bankCode = toggled,
            cardKey = null,
            useStatementCycle = false
        )
    }

    fun selectCardFilter(cardKey: String?) {
        val current = filterStateFlow.value
        filterStateFlow.value = current.copy(
            cardKey = cardKey,
            useStatementCycle = if (cardKey == null) false else current.useStatementCycle
        )
    }

    fun selectCategoryFilter(category: String?) {
        val current = filterStateFlow.value
        val toggled = if (current.category == category && category != null) null else category
        filterStateFlow.value = current.copy(category = toggled)
    }

    fun toggleStatementCycle(enabled: Boolean) {
        filterStateFlow.value = filterStateFlow.value.copy(useStatementCycle = enabled)
    }

    fun toggleIncludeDemoRecords(enabled: Boolean) {
        filterStateFlow.value = filterStateFlow.value.copy(includeDemoRecords = enabled)
    }

    fun updateSearchQuery(query: String) {
        filterStateFlow.value = filterStateFlow.value.copy(searchQuery = query)
    }

    fun simulateNextSample() {
        val sample = sampleMessages[sampleIndex % sampleMessages.size]
        sampleIndex++
        viewModelScope.launch {
            val outcome = repository.ingestIncomingMessage(
                sourceChannel = sample.channel,
                sourceTag = sample.tag,
                sourcePackage = sample.tag,
                bankHint = sample.bank,
                title = sample.title,
                body = sample.body,
                timestamp = Instant.now().toEpochMilli(),
                isDemo = true
            )
            filterStateFlow.value = filterStateFlow.value.copy(includeDemoRecords = true)
            feedbackFlow.value = "[DEMO] " + formatOutcomeFeedback(outcome)
        }
    }

    fun testCustomNotificationText(bank: BankProvider, rawText: String, asRealRecord: Boolean = false) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            val outcome = repository.ingestIncomingMessage(
                sourceChannel = if (asRealRecord) "Manuel Test (${bank.displayName})" else "Demo Test (${bank.displayName})",
                sourceTag = if (asRealRecord) "MANUAL_TEST" else "DEMO_TEST",
                sourcePackage = "MANUAL_TEST_${System.nanoTime()}",
                bankHint = bank,
                title = bank.displayName,
                body = rawText.trim(),
                timestamp = Instant.now().toEpochMilli(),
                isDemo = !asRealRecord
            )
            if (!asRealRecord && outcome is IngestResult.Saved) {
                filterStateFlow.value = filterStateFlow.value.copy(includeDemoRecords = true)
            }
            feedbackFlow.value = formatOutcomeFeedback(outcome)
        }
    }

    fun clearDemoTransactions() {
        viewModelScope.launch {
            repository.clearDemoTransactions()
            filterStateFlow.value = filterStateFlow.value.copy(includeDemoRecords = false)
            feedbackFlow.value = "Demo kayıtları temizlendi."
        }
    }

    private fun formatOutcomeFeedback(result: IngestResult): String {
        return when (result) {
            is IngestResult.Saved -> {
                val t = result.transaction
                "Kaydedildi [${result.cardAlias}]: ${t.merchant} • ${t.amount} ${t.currency}"
            }
            is IngestResult.MergedDuplicate -> "Mükerrer birleştirildi: ${result.detail}"
            is IngestResult.AlreadyProcessed -> "Zaten işlendi: ${result.detail}"
            is IngestResult.PendingReview -> "Bekleyenler kutusuna alındı: ${result.reason}"
            is IngestResult.Ignored -> "Atlandı: ${result.reason}"
        }
    }

    fun addManualTransaction(
        bank: BankProvider,
        cardLast4: String,
        cardType: DetectedCardType,
        groupId: String?,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installmentCount: Int,
        timestamp: Long = System.currentTimeMillis(),
        note: String,
        rawText: String = "Manuel kayıt",
        sourceLogId: Long? = null
    ) {
        viewModelScope.launch {
            repository.addManualTransaction(
                bank = bank,
                cardLast4 = cardLast4,
                cardType = cardType,
                groupId = groupId,
                merchant = merchant,
                amount = amount,
                currency = currency,
                category = category,
                transactionType = if (isRefund) ParsedTransactionType.REFUND else ParsedTransactionType.EXPENSE,
                installmentCount = installmentCount,
                timestamp = timestamp,
                note = note,
                rawText = rawText,
                sourceLogId = sourceLogId
            )
            feedbackFlow.value = "Harcama kaydedildi."
        }
    }

    fun approvePendingReview(
        pendingId: Long,
        bank: BankProvider,
        cardLast4: String,
        cardType: DetectedCardType,
        groupId: String?,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installmentCount: Int
    ) {
        viewModelScope.launch {
            repository.approvePendingReview(
                pendingId = pendingId,
                targetBank = bank,
                targetLast4 = cardLast4,
                targetCardType = cardType,
                targetGroupId = groupId,
                merchant = merchant,
                amount = amount,
                currency = currency,
                category = category,
                isRefund = isRefund,
                installmentCount = installmentCount
            )
            feedbackFlow.value = "Bekleyen işlem onaylandı ve kaydedildi."
        }
    }

    fun dismissOrBlockPendingReview(pendingId: Long, blockCard: Boolean) {
        viewModelScope.launch {
            repository.dismissOrBlockPendingReview(pendingId, blockCard)
            feedbackFlow.value = if (blockCard) "Kart engellendi ve bildirim kapatıldı." else "Bekleyen kayıt kapatıldı."
        }
    }

    fun updateTransaction(
        entity: TransactionEntity,
        newCategory: String,
        newGroupId: String?,
        newNote: String,
        rememberForMerchant: Boolean
    ) {
        viewModelScope.launch {
            repository.updateTransaction(entity, newCategory, newGroupId, newNote, rememberForMerchant)
        }
    }

    fun deleteTransaction(entity: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(entity)
        }
    }

    fun saveCard(card: CardEntity, applyGroupToPastTransactions: Boolean = false) {
        viewModelScope.launch {
            repository.saveCard(card, applyGroupToPastTransactions)
        }
    }

    fun removeCard(cardKey: String, deleteTransactionsToo: Boolean) {
        viewModelScope.launch {
            repository.removeCard(cardKey, deleteTransactionsToo)
            if (filterStateFlow.value.cardKey == cardKey) {
                filterStateFlow.value = filterStateFlow.value.copy(cardKey = null)
            }
        }
    }

    fun saveCardGroup(name: String, colorHex: Long, existingGroupId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val currentGroups = uiState.value.cardGroups
            val id = existingGroupId ?: ("GRP_" + UUID.randomUUID().toString().take(8).uppercase())
            val order = currentGroups.find { it.groupId == id }?.sortOrder ?: currentGroups.size
            repository.saveCardGroup(
                CardGroupEntity(
                    groupId = id,
                    name = trimmed,
                    colorHex = colorHex,
                    sortOrder = order
                )
            )
        }
    }

    fun deleteCardGroup(groupId: String, moveToGroupId: String? = null) {
        viewModelScope.launch {
            repository.deleteCardGroup(groupId, moveToGroupId)
            val currentGf = filterStateFlow.value.groupFilter
            if (currentGf is GroupFilter.Group && currentGf.groupId == groupId) {
                filterStateFlow.value = filterStateFlow.value.copy(groupFilter = GroupFilter.All)
            }
        }
    }

    fun setBankUnknownCardPolicy(bankCode: String, policy: UnknownCardPolicy) {
        viewModelScope.launch {
            repository.setBankUnknownCardPolicy(bankCode, policy)
        }
    }

    fun clearRawLogs() {
        viewModelScope.launch {
            repository.clearRawLogs()
        }
    }

    private fun prepareExportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) {
                f.delete()
            }
        }
        return dir
    }

    private fun currentScopeSlug(state: DashboardUiState): Pair<String, String> {
        val groupLabel = when (val gf = state.selectedGroupFilter) {
            is GroupFilter.All -> "Tüm Bölümler"
            is GroupFilter.Ungrouped -> "Bölümsüz"
            is GroupFilter.Group -> state.cardGroups.find { it.groupId == gf.groupId }?.name ?: gf.groupId
        }
        val bankLabel = state.selectedBankCode?.let { BankProvider.fromCode(it).displayName } ?: "Tüm Bankalar"
        val slug = groupLabel
            .lowercase()
            .replace(Regex("""[^a-z0-9çğıöşü]+"""), "_")
            .trim('_')
            .ifEmpty { "tumu" }
        return "$groupLabel • $bankLabel • ${state.periodLabel}" to slug
    }

    fun exportCsvAndShare(context: Context, onlyFilteredScope: Boolean = true) {
        viewModelScope.launch {
            val state = uiState.value
            val (_, slug) = currentScopeSlug(state)
            val listToExport = if (onlyFilteredScope) {
                state.filteredTransactions.filter { !it.isDemo }
            } else {
                null
            }
            val csvContent = withContext(Dispatchers.IO) {
                repository.buildCsvExport(listToExport)
            }
            val file = withContext(Dispatchers.IO) {
                val exportDir = prepareExportDir(context)
                val outFile = File(exportDir, "harcamalar_${slug}_${state.selectedMonth}.csv")
                outFile.writeText("\uFEFF" + csvContent, Charsets.UTF_8)
                outFile
            }
            shareFileViaProvider(
                context = context,
                file = file,
                mimeType = "text/csv",
                subject = "Harcama Dökümü (CSV)",
                chooserTitle = "CSV Dökümünü Paylaş / Kaydet"
            )
        }
    }

    fun exportPdfAndShare(context: Context) {
        viewModelScope.launch {
            val state = uiState.value
            val (scopeSubtitle, slug) = currentScopeSlug(state)
            val nonDemoTx = state.filteredTransactions.filter { !it.isDemo }
            val cardsByKey = state.cards.associateBy { it.cardKey }
            val groupsById = state.cardGroups.associateBy { it.groupId }

            val file = withContext(Dispatchers.IO) {
                val exportDir = prepareExportDir(context)
                val outFile = File(exportDir, "harcama_raporu_${slug}_${state.selectedMonth}.pdf")
                PdfReportGenerator.generateReportFile(
                    outFile = outFile,
                    reportTitle = "HB Harcama Takip — Dönem Raporu",
                    scopeSubtitle = scopeSubtitle,
                    currencySummaries = state.currencySummaries,
                    bankBreakdown = state.tlBankBreakdown,
                    categoryBreakdown = state.tlCategoryBreakdown,
                    transactions = nonDemoTx,
                    cardsByKey = cardsByKey,
                    groupsById = groupsById
                )
            }
            shareFileViaProvider(
                context = context,
                file = file,
                mimeType = "application/pdf",
                subject = "Harcama Dönem Raporu (PDF)",
                chooserTitle = "PDF Raporunu Paylaş / Kaydet"
            )
        }
    }

    fun writeJsonBackupToUri(context: Context, uri: Uri, includeRawText: Boolean = false) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    repository.buildJsonBackup(includeRawText = includeRawText)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Dosya yazılamadı")
                }
                feedbackFlow.value = "JSON yedeği başarıyla kaydedildi."
            } catch (e: Throwable) {
                feedbackFlow.value = "JSON yedekleme hatası: ${e.message}"
            }
        }
    }

    fun restoreJsonBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inp ->
                        inp.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Dosya okunamadı")
                }
                val (imported, skipped) = withContext(Dispatchers.IO) {
                    repository.restoreFromJsonBackup(jsonText)
                }
                feedbackFlow.value = "Yedek geri yüklendi: $imported yeni işlem eklendi ($skipped mevcut işlem korundu)."
            } catch (e: Throwable) {
                feedbackFlow.value = "Yedek geri yükleme hatası: ${e.message}"
            }
        }
    }

    private fun shareFileViaProvider(
        context: Context,
        file: File,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
