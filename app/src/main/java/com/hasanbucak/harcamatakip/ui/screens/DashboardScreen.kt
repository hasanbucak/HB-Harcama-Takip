package com.hasanbucak.harcamatakip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.CategoryClassifier
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import com.hasanbucak.harcamatakip.ui.DashboardUiState
import com.hasanbucak.harcamatakip.ui.Formatters
import com.hasanbucak.harcamatakip.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showCategoryBreakdown by remember { mutableStateOf(true) }

    val cardsMap = remember(state.cards) {
        state.cards.associateBy { it.cardKey }
    }
    val selectedCard = state.selectedCardKey?.let { cardsMap[it] }
    val visibleCardsForBank = remember(state.cards, state.selectedBankCode) {
        if (state.selectedBankCode == null) {
            state.cards
        } else {
            state.cards.filter { it.bankCode == state.selectedBankCode }
        }
    }

    val groupedByDay = remember(state.filteredTransactions) {
        state.filteredTransactions.groupBy { Formatters.formatDayHeader(it.timestamp) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Manuel Harcama Ekle")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month switcher + CSV export
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.previousMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Ay")
                        }
                        Text(
                            text = state.periodLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Ay")
                        }
                    }
                    AssistChip(
                        onClick = { viewModel.exportCsvAndShare(context) },
                        label = { Text("CSV Dışa Aktar") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Bank filter row + Card filter row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedBankCode == null,
                            onClick = { viewModel.selectBankFilter(null) },
                            label = { Text("Tüm Bankalar") }
                        )
                        listOf(
                            BankProvider.YAPI_KREDI,
                            BankProvider.VAKIFBANK,
                            BankProvider.TOM_BANK
                        ).forEach { bank ->
                            FilterChip(
                                selected = state.selectedBankCode == bank.code,
                                onClick = { viewModel.selectBankFilter(bank.code) },
                                label = { Text(bank.displayName) }
                            )
                        }
                    }

                    if (visibleCardsForBank.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.selectedCardKey == null,
                                onClick = { viewModel.selectCardFilter(null) },
                                label = { Text("Tüm Kartlar") }
                            )
                            visibleCardsForBank.forEach { card ->
                                FilterChip(
                                    selected = state.selectedCardKey == card.cardKey,
                                    onClick = { viewModel.selectCardFilter(card.cardKey) },
                                    label = { Text(card.alias) }
                                )
                            }
                        }
                    }

                    if (selectedCard != null && selectedCard.statementDay > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hesap kesim gününe göre filtrele (Ayın ${selectedCard.statementDay}'i)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = state.useStatementCycle,
                                onCheckedChange = { viewModel.toggleStatementCycle(it) }
                            )
                        }
                    }
                }
            }

            // Summary Card
            item {
                SummaryHeroCard(
                    state = state,
                    txCount = state.filteredTransactions.size
                )
            }

            // Category Breakdown
            if (state.tlCategoryBreakdown.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCategoryBreakdown = !showCategoryBreakdown },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kategori Dağılımı (TL)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (showCategoryBreakdown) "Gizle" else "Göster",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (showCategoryBreakdown) {
                                state.tlCategoryBreakdown.forEach { item ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${item.category} (${item.count})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = "${Formatters.formatMoney(item.amount)} (%${(item.ratio * 100).toInt()})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { item.ratio },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("İşyeri, banka, kart son 4, kategori ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Temizle")
                            }
                        }
                    }
                )
            }

            // Empty state or day-grouped transactions
            if (state.filteredTransactions.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bu dönemde henüz harcama kaydı yok",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Yapı Kredi, VakıfBank (6251) veya TOM Bank harcama bildirimi ya da SMS düştüğünde kart ve kart türü bilgisiyle otomatik buraya eklenecek.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                groupedByDay.forEach { (dayTitle, dayList) ->
                    item(key = "header_$dayTitle") {
                        Text(
                            text = dayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                    items(items = dayList, key = { it.id }) { tx ->
                        TransactionRowCard(
                            tx = tx,
                            card = cardsMap[tx.cardKey],
                            onClick = { editingTx = tx }
                        )
                    }
                }
            }
        }
    }

    editingTx?.let { tx ->
        val card = cardsMap[tx.cardKey]
        val cardDisplay = buildCardBadgeLabel(tx, card)
        val typeDisplay = DetectedCardType.fromCode(card?.cardType ?: tx.cardType).labelTr
        TransactionEditSheet(
            tx = tx,
            cardBadgeLabel = "$cardDisplay • $typeDisplay",
            onDismiss = { editingTx = null },
            onSave = { cat, note, rememberRule ->
                viewModel.updateTransaction(tx, cat, note, rememberRule)
                editingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx)
                editingTx = null
            }
        )
    }

    if (showAddDialog) {
        AddManualTransactionDialog(
            cards = state.cards,
            onDismiss = { showAddDialog = false },
            onSave = { bank, last4, cardType, merchant, amount, currency, category, isRefund, installments, note ->
                viewModel.addManualTransaction(
                    bank = bank,
                    cardLast4 = last4,
                    cardType = cardType,
                    merchant = merchant,
                    amount = amount,
                    currency = currency,
                    category = category,
                    isRefund = isRefund,
                    installmentCount = installments,
                    note = note
                )
                showAddDialog = false
            }
        )
    }
}

private fun buildCardBadgeLabel(tx: TransactionEntity, card: CardEntity?): String {
    if (card != null && card.alias.isNotBlank()) {
        return if (card.alias.contains(tx.bankName, ignoreCase = true)) {
            card.alias
        } else {
            "${tx.bankName} • ${card.alias}"
        }
    }
    return when (tx.cardLast4) {
        "NAKIT" -> "Nakit"
        "GENEL" -> tx.bankName
        else -> "${tx.bankName} •••• ${tx.cardLast4}"
    }
}

@Composable
private fun SummaryHeroCard(
    state: DashboardUiState,
    txCount: Int
) {
    val tlSummary = state.currencySummaries.find { it.currency == "TL" }
    val foreignSummaries = state.currencySummaries.filter { it.currency != "TL" }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Net Dönem Harcaması",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = Formatters.formatMoney(tlSummary?.netExpense ?: 0.0, "TL"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Toplam: ${Formatters.formatMoney(tlSummary?.grossExpense ?: 0.0, "TL")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                if ((tlSummary?.totalRefund ?: 0.0) > 0.0) {
                    Text(
                        text = "İade: -${Formatters.formatMoney(tlSummary?.totalRefund ?: 0.0, "TL")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF15803D),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "$txCount işlem",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }

            if (foreignSummaries.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                foreignSummaries.forEach { foreign ->
                    Text(
                        text = "Döviz Harcaması (${foreign.currency}): ${Formatters.formatMoney(foreign.netExpense, foreign.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    tx: TransactionEntity,
    card: CardEntity?,
    onClick: () -> Unit
) {
    val isRefund = tx.transactionType == ParsedTransactionType.REFUND.name
    val cardColor = Color(card?.colorHex ?: BankProvider.fromCode(tx.bankCode).defaultColorHex)
    val cardLabel = buildCardBadgeLabel(tx, card)
    val cardTypeLabel = DetectedCardType.fromCode(card?.cardType ?: tx.cardType).labelTr

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cardColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tx.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (if (isRefund) "+" else "-") + Formatters.formatMoney(tx.amount, tx.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRefund) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                )
            }

            // Explicit Bank/Card badge + Card Type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = cardColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = cardLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cardColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = cardTypeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            val installmentBadge = if (tx.installmentCount > 1) " • ${tx.installmentCount} Taksit" else ""
            Text(
                text = "${tx.category}$installmentBadge • ${Formatters.formatTime(tx.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tx.note.isNotBlank()) {
                Text(
                    text = tx.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEditSheet(
    tx: TransactionEntity,
    cardBadgeLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var selectedCategory by remember(tx) { mutableStateOf(tx.category) }
    var note by remember(tx) { mutableStateOf(tx.note) }
    var rememberForMerchant by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tx.merchant,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$cardBadgeLabel\n${Formatters.formatMoney(tx.amount, tx.currency)} • ${Formatters.formatDateTime(tx.timestamp)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategori") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    CategoryClassifier.ALL_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategory = cat
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { rememberForMerchant = !rememberForMerchant }
            ) {
                Checkbox(
                    checked = rememberForMerchant,
                    onCheckedChange = { rememberForMerchant = it }
                )
                Text(
                    text = "Bu işyeri (${tx.merchant}) için her zaman bu kategoriyi kullan",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Not (isteğe bağlı)") },
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Ham Bildirim Kaynağı (${tx.source}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = tx.rawText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sil")
                }

                Button(onClick = { onSave(selectedCategory, note, rememberForMerchant) }) {
                    Text("Kaydet")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualTransactionDialog(
    cards: List<CardEntity>,
    initialRawText: String = "Manuel kayıt",
    onDismiss: () -> Unit,
    onSave: (
        bank: BankProvider,
        last4: String,
        cardType: DetectedCardType,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installments: Int,
        note: String
    ) -> Unit
) {
    val firstCard = cards.firstOrNull()
    var selectedBank by remember {
        mutableStateOf(
            firstCard?.let { BankProvider.fromCode(it.bankCode) } ?: BankProvider.YAPI_KREDI
        )
    }
    var selectedCardType by remember {
        mutableStateOf(
            firstCard?.let { DetectedCardType.fromCode(it.cardType) } ?: DetectedCardType.CREDIT
        )
    }
    var cardLast4 by remember { mutableStateOf(firstCard?.last4 ?: "") }
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("TL") }
    var category by remember { mutableStateOf(CategoryClassifier.OTHER) }
    var isRefund by remember { mutableStateOf(false) }
    var installmentsText by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var catExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manuel İşlem Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialRawText != "Manuel kayıt") {
                    Text(
                        text = initialRawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bank selector
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        BankProvider.YAPI_KREDI,
                        BankProvider.VAKIFBANK,
                        BankProvider.TOM_BANK,
                        BankProvider.OTHER
                    ).forEach { b ->
                        FilterChip(
                            selected = selectedBank == b,
                            onClick = {
                                selectedBank = b
                                if (b == BankProvider.VAKIFBANK) {
                                    cardLast4 = YkbTransactionParser.VAKIFBANK_ALLOWED_LAST4
                                } else if (b == BankProvider.OTHER) {
                                    selectedCardType = DetectedCardType.CASH
                                    cardLast4 = "NAKIT"
                                }
                            },
                            label = { Text(if (b == BankProvider.OTHER) "Nakit" else b.displayName) }
                        )
                    }
                }

                // Card type selector
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetectedCardType.entries.forEach { ct ->
                        FilterChip(
                            selected = selectedCardType == ct,
                            onClick = { selectedCardType = ct },
                            label = { Text(ct.labelTr) }
                        )
                    }
                }

                OutlinedTextField(
                    value = merchant,
                    onValueChange = {
                        merchant = it
                        category = CategoryClassifier.classify(it)
                    },
                    label = { Text("İşyeri / Açıklama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Tutar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(4) },
                        label = { Text("Birim") },
                        singleLine = true,
                        modifier = Modifier.width(88.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cardLast4,
                        onValueChange = { cardLast4 = it.take(10) },
                        label = { Text("Kart Son 4") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = installmentsText,
                        onValueChange = { installmentsText = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Taksit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(88.dp)
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        CategoryClassifier.ALL_CATEGORIES.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    category = c
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Not") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isRefund = !isRefund }
                ) {
                    Checkbox(checked = isRefund, onCheckedChange = { isRefund = it })
                    Text("İade / İptal işlemi", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = YkbTransactionParser.parseFlexibleAmount(amountText) ?: 0.0
                    if (parsedAmount > 0.0) {
                        onSave(
                            selectedBank,
                            cardLast4,
                            selectedCardType,
                            merchant,
                            parsedAmount,
                            currency.ifBlank { "TL" },
                            category,
                            isRefund,
                            installmentsText.toIntOrNull() ?: 1,
                            note
                        )
                    }
                }
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
