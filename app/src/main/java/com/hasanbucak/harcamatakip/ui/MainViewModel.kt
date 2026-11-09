package com.hasanbucak.harcamatakip.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hasanbucak.harcamatakip.HarcamaApp
import com.hasanbucak.harcamatakip.data.IngestResult
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
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

data class CurrencySummary(
    val currency: String,
    val grossExpense: Double,
    val totalRefund: Double,
    val netExpense: Double
)

data class CategoryBreakdownItem(
    val category: String,
    val amount: Double,
    val ratio: Float,
    val count: Int
)

data class DashboardUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedBankCode: String? = null, // null = All banks
    val selectedCardKey: String? = null,  // null = All cards
    val useStatementCycle: Boolean = false,
    val searchQuery: String = "",
    val periodLabel: String = "",
    val cards: List<CardEntity> = emptyList(),
    val cardPeriodTotalsTl: Map<String, Double> = emptyMap(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val currencySummaries: List<CurrencySummary> = emptyList(),
    val tlCategoryBreakdown: List<CategoryBreakdownItem> = emptyList(),
    val recentLogs: List<RawNotificationLogEntity> = emptyList(),
    val lastTestFeedback: String? = null
)

private data class FilterConfig(
    val month: YearMonth,
    val bankCode: String?,
    val cardKey: String?,
    val useStatementCycle: Boolean,
    val searchQuery: String
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

    private val selectedMonthFlow = MutableStateFlow(YearMonth.now())
    private val selectedBankFlow = MutableStateFlow<String?>(null)
    private val selectedCardFlow = MutableStateFlow<String?>(null)
    private val useStatementCycleFlow = MutableStateFlow(false)
    private val searchQueryFlow = MutableStateFlow("")
    private val testFeedbackFlow = MutableStateFlow<String?>(null)

    private var sampleIndex = 0

    private val sampleMessages: List<SampleNotification> = listOf(
        SampleNotification(
            bank = BankProvider.YAPI_KREDI,
            channel = "Simülasyon (Yapı Kredi)",
            tag = "PUSH_YKB",
            title = "Harcama Bilgisi",
            body = "4543****4821 nolu kartınızla 09.11.2026 14:32 tarihinde MİGROS TİCARET A.Ş. işyerinden 645,50 TL tutarında harcama yapılmıştır. Güncel kullanılabilir limitiniz 84.354,50 TL'dir."
        ),
        SampleNotification(
            bank = BankProvider.VAKIFBANK,
            channel = "Simülasyon (VakıfBank)",
            tag = "SMS",
            title = "VAKIFBANK",
            body = "6251 ile biten Worldcard'ınızla 09.11.2026 15:10'da OPET İSTASYONU işyerinden 1.450,00 TL harcama yapılmıştır."
        ),
        SampleNotification(
            bank = BankProvider.TOM_BANK,
            channel = "Simülasyon (TOM Bank)",
            tag = "SMS",
            title = "TOMBank",
            body = "*1907 nolu Hadi Kredi Kartınızla TRENDYOL.COM işyerinden 3 taksitli 2.490,00 TL harcama yapılmıştır."
        ),
        SampleNotification(
            bank = BankProvider.VAKIFBANK,
            channel = "Simülasyon (VakıfBank Diğer Kart)",
            tag = "SMS",
            title = "VAKIFBANK",
            body = "9988 ile biten kartınızla A101 işyerinden 210,00 TL harcama yapılmıştır."
        ),
        SampleNotification(
            bank = BankProvider.YAPI_KREDI,
            channel = "Simülasyon (World Mobil)",
            tag = "PUSH_WORLD",
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile STEAM GAMES işyerinden 14.99 USD harcama yapılmıştır."
        )
    )

    private val filterConfigFlow = combine(
        selectedMonthFlow,
        selectedBankFlow,
        selectedCardFlow,
        useStatementCycleFlow,
        searchQueryFlow
    ) { month, bank, card, useCycle, query ->
        FilterConfig(month, bank, card, useCycle, query)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        filterConfigFlow,
        repository.transactionsFlow,
        repository.cardsFlow,
        repository.recentLogsFlow,
        testFeedbackFlow
    ) { filter, allTx, cards, logs, feedback ->
        val zone = ZoneId.systemDefault()
        val selectedCardEntity = cards.find { it.cardKey == filter.cardKey }
        val statementDay = selectedCardEntity?.statementDay ?: 1

        val (startMillis, endMillis, periodLabel) = computePeriodBounds(
            month = filter.month,
            useStatementCycle = filter.useStatementCycle && selectedCardEntity != null && statementDay > 1,
            statementDay = statementDay,
            zone = zone
        )

        val txInPeriod = allTx.filter { tx ->
            tx.timestamp in startMillis until endMillis
        }

        val cardTotalsTl = txInPeriod
            .filter { it.currency == "TL" }
            .groupBy { it.cardKey }
            .mapValues { (_, list) ->
                list.sumOf { tx ->
                    if (tx.transactionType == ParsedTransactionType.REFUND.name) -tx.amount else tx.amount
                }
            }

        val queryTrimmed = filter.searchQuery.trim()
        val filtered = txInPeriod.filter { tx ->
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

        val tlExpenses = filtered.filter {
            it.currency == "TL" && it.transactionType == ParsedTransactionType.EXPENSE.name
        }
        val totalTlExpense = tlExpenses.sumOf { it.amount }.coerceAtLeast(0.0)
        val breakdown = if (totalTlExpense > 0.0) {
            tlExpenses
                .groupBy { it.category }
                .map { (cat, list) ->
                    val sum = list.sumOf { it.amount }
                    CategoryBreakdownItem(
                        category = cat,
                        amount = sum,
                        ratio = (sum / totalTlExpense).toFloat().coerceIn(0f, 1f),
                        count = list.size
                    )
                }
                .sortedByDescending { it.amount }
        } else {
            emptyList()
        }

        DashboardUiState(
            selectedMonth = filter.month,
            selectedBankCode = filter.bankCode,
            selectedCardKey = filter.cardKey,
            useStatementCycle = filter.useStatementCycle,
            searchQuery = filter.searchQuery,
            periodLabel = periodLabel,
            cards = cards,
            cardPeriodTotalsTl = cardTotalsTl,
            filteredTransactions = filtered,
            currencySummaries = summaries,
            tlCategoryBreakdown = breakdown,
            recentLogs = logs,
            lastTestFeedback = feedback
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

    fun previousMonth() {
        selectedMonthFlow.value = selectedMonthFlow.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonthFlow.value = selectedMonthFlow.value.plusMonths(1)
    }

    fun selectBankFilter(bankCode: String?) {
        selectedBankFlow.value = bankCode
        selectedCardFlow.value = null
        useStatementCycleFlow.value = false
    }

    fun selectCardFilter(cardKey: String?) {
        selectedCardFlow.value = cardKey
        if (cardKey == null) {
            useStatementCycleFlow.value = false
        }
    }

    fun toggleStatementCycle(enabled: Boolean) {
        useStatementCycleFlow.value = enabled
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun simulateNextSample() {
        val sample = sampleMessages[sampleIndex % sampleMessages.size]
        sampleIndex++
        viewModelScope.launch {
            val outcome = repository.ingestIncomingMessage(
                sourceChannel = sample.channel,
                sourceTag = sample.tag,
                bankHint = sample.bank,
                title = sample.title,
                body = sample.body,
                timestamp = Instant.now().toEpochMilli(),
                isDemo = true
            )
            testFeedbackFlow.value = formatOutcomeFeedback(outcome)
        }
    }

    fun testCustomNotificationText(bank: BankProvider, rawText: String) {
        if (rawText.isBlank()) return
        viewModelScope.launch {
            val outcome = repository.ingestIncomingMessage(
                sourceChannel = "Manuel Test (${bank.displayName})",
                sourceTag = "MANUAL_TEST",
                bankHint = bank,
                title = bank.displayName,
                body = rawText.trim(),
                timestamp = Instant.now().toEpochMilli(),
                isDemo = true
            )
            testFeedbackFlow.value = formatOutcomeFeedback(outcome)
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
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installmentCount: Int,
        note: String,
        rawText: String = "Manuel kayıt",
        sourceLogId: Long? = null
    ) {
        viewModelScope.launch {
            repository.addManualTransaction(
                bank = bank,
                cardLast4 = cardLast4,
                cardType = cardType,
                groupId = null,
                merchant = merchant,
                amount = amount,
                currency = currency,
                category = category,
                transactionType = if (isRefund) ParsedTransactionType.REFUND else ParsedTransactionType.EXPENSE,
                installmentCount = installmentCount,
                note = note,
                rawText = rawText,
                sourceLogId = sourceLogId
            )
        }
    }

    fun updateTransaction(
        entity: TransactionEntity,
        newCategory: String,
        newNote: String,
        rememberForMerchant: Boolean
    ) {
        viewModelScope.launch {
            repository.updateTransaction(entity, newCategory, entity.groupId, newNote, rememberForMerchant)
        }
    }

    fun deleteTransaction(entity: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(entity)
        }
    }

    fun saveCard(card: CardEntity) {
        viewModelScope.launch {
            repository.saveCard(card)
        }
    }

    fun clearRawLogs() {
        viewModelScope.launch {
            repository.clearRawLogs()
        }
    }

    fun exportCsvAndShare(context: Context) {
        viewModelScope.launch {
            val csvContent = withContext(Dispatchers.IO) {
                repository.buildCsvExport()
            }
            val file = withContext(Dispatchers.IO) {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(exportDir, "hb_banka_harcamalar.csv")
                outFile.writeText("\uFEFF" + csvContent, Charsets.UTF_8)
                outFile
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Banka Harcama Kayıtları (CSV)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "CSV Harcama Dökümünü Paylaş / Kaydet")
            )
        }
    }
}
