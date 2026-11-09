package com.hasanbucak.harcamatakip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.CategoryClassifier
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import com.hasanbucak.harcamatakip.ui.DashboardUiState
import com.hasanbucak.harcamatakip.ui.Formatters
import com.hasanbucak.harcamatakip.ui.GroupFilter
import com.hasanbucak.harcamatakip.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var editingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var includeRawInBackup by remember { mutableStateOf(false) }
    var showBankBreakdown by remember { mutableStateOf(true) }
    var showCategoryBreakdown by remember { mutableStateOf(true) }

    val jsonSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.writeJsonBackupToUri(context, uri, includeRawInBackup)
        }
    }

    val jsonOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreJsonBackupFromUri(context, uri)
        }
    }

    val cardsMap = remember(state.cards) { state.cards.associateBy { it.cardKey } }
    val groupsMap = remember(state.cardGroups) { state.cardGroups.associateBy { it.groupId } }
    val selectedCard = state.selectedCardKey?.let { cardsMap[it] }

    val visibleBanks = remember(state.cards, state.tlBankBreakdown, state.selectedBankCode) {
        val codes = LinkedHashSet<String>()
        state.tlBankBreakdown.forEach { codes.add(it.bankCode) }
        state.cards.forEach { codes.add(it.bankCode) }
        state.selectedBankCode?.let { codes.add(it) }
        if (codes.isEmpty()) {
            listOf(BankProvider.YAPI_KREDI, BankProvider.VAKIFBANK, BankProvider.TOM_BANK)
        } else {
            codes.map { BankProvider.fromCode(it) }.filter { it != BankProvider.OTHER }
        }
    }

    val visibleCards = remember(state.cards, state.selectedBankCode, state.selectedGroupFilter) {
        state.cards.filter { c ->
            val bankOk = state.selectedBankCode == null || c.bankCode == state.selectedBankCode
            val grpOk = when (val gf = state.selectedGroupFilter) {
                is GroupFilter.All -> true
                is GroupFilter.Ungrouped -> c.defaultGroupId == null
                is GroupFilter.Group -> c.defaultGroupId == gf.groupId
            }
            bankOk && grpOk
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
            // 1. Month switcher + Export/Backup button
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
                        onClick = { showExportSheet = true },
                        label = { Text("CSV / PDF / Yedek") },
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

            // 2. Folder / Workspace (Bölüm) Selector + Bank & Card Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedGroupFilter is GroupFilter.All,
                            onClick = { viewModel.selectGroupFilter(GroupFilter.All) },
                            label = { Text("Tümü (Ortak)") }
                        )
                        state.cardGroups.forEach { grp ->
                            FilterChip(
                                selected = (state.selectedGroupFilter as? GroupFilter.Group)?.groupId == grp.groupId,
                                onClick = { viewModel.selectGroupFilter(GroupFilter.Group(grp.groupId)) },
                                label = { Text(grp.name) }
                            )
                        }
                        if (state.groupPeriodTotalsTl.containsKey(null) || state.selectedGroupFilter is GroupFilter.Ungrouped) {
                            FilterChip(
                                selected = state.selectedGroupFilter is GroupFilter.Ungrouped,
                                onClick = { viewModel.selectGroupFilter(GroupFilter.Ungrouped) },
                                label = { Text("Bölümsüz") }
                            )
                        }
                        AssistChip(
                            onClick = { showNewGroupDialog = true },
                            label = { Text("+ Bölüm") }
                        )
                    }

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
                        visibleBanks.forEach { bank ->
                            FilterChip(
                                selected = state.selectedBankCode == bank.code,
                                onClick = { viewModel.selectBankFilter(bank.code) },
                                label = { Text(bank.displayName) }
                            )
                        }
                    }

                    if (visibleCards.isNotEmpty()) {
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
                            visibleCards.forEach { card ->
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

            // Demo banner if demo records exist
            if (state.demoTransactionCount > 0) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.demoTransactionCount} adet Demo kaydı ${if (state.includeDemoRecords) "gösteriliyor" else "gizlendi"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { viewModel.toggleIncludeDemoRecords(!state.includeDemoRecords) }) {
                                    Text(if (state.includeDemoRecords) "Gizle" else "Göster")
                                }
                                TextButton(onClick = { viewModel.clearDemoTransactions() }) {
                                    Text("Temizle")
                                }
                            }
                        }
                    }
                }
            }

            // 3. Summary Hero Card
            item {
                val activeGroupTitle = when (val gf = state.selectedGroupFilter) {
                    is GroupFilter.All -> "Tümü (Ortak Görünüm)"
                    is GroupFilter.Ungrouped -> "Bölümsüz Kayıtlar"
                    is GroupFilter.Group -> groupsMap[gf.groupId]?.name ?: gf.groupId
                }
                SummaryHeroCard(
                    state = state,
                    activeGroupTitle = activeGroupTitle,
                    txCount = state.filteredTransactions.size
                )
            }

            // 4. Bank Breakdown Card (Bankalara Göre Dağılım)
            if (state.tlBankBreakdown.isNotEmpty()) {
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
                                    .clickable { showBankBreakdown = !showBankBreakdown },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bankalara Göre Dağılım (TL)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (showBankBreakdown) "Gizle" else "Göster",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            if (showBankBreakdown) {
                                state.tlBankBreakdown.forEach { bItem ->
                                    val isSelected = state.selectedBankCode == bItem.bankCode
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.selectBankFilter(bItem.bankCode) }
                                            .padding(vertical = 3.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${if (isSelected) "● " else ""}${bItem.bankName} (${bItem.count})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "${Formatters.formatMoney(bItem.netExpense)} (%${(bItem.ratio * 100).toInt()})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { bItem.ratio },
                                            color = Color(bItem.colorHex),
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

            // 5. Category Breakdown Card (Kategorilere Göre Dağılım)
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
                                    text = "Kategorilere Göre Dağılım (TL)",
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
                                    val isCatSelected = state.selectedCategory == item.category
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.selectCategoryFilter(item.category) }
                                            .padding(vertical = 3.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${if (isCatSelected) "● " else ""}${item.category} (${item.count})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
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

            // 6. Search bar
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

            // 7. Day-grouped transactions
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
                                text = "Bu görünümde henüz harcama kaydı yok",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Kartlar & Bölüm sekmesinden kartlarının son 4 hanesini ve bölümünü (Ev, İş, Kişisel) tanımlayabilir; banka bildirimi düştüğünde harcamaları otomatik görebilirsin.",
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
                            group = tx.groupId?.let { groupsMap[it] },
                            onClick = { editingTx = tx }
                        )
                    }
                }
            }
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Dışa / İçe Aktar (CSV • PDF • JSON)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aktif bölüm (ör. İş / Ev) ve dönem filtresine göre rapor üretebilir veya tüm veritabanını JSON olarak yedekleyip geri yükleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        showExportSheet = false
                        viewModel.exportPdfAndShare(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF Dönem Raporu Oluştur (Seçili Bölüm)")
                }

                OutlinedButton(
                    onClick = {
                        showExportSheet = false
                        viewModel.exportCsvAndShare(context, onlyFilteredScope = true)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Excel (CSV) Dışa Aktar — Seçili Görünüm")
                }

                OutlinedButton(
                    onClick = {
                        showExportSheet = false
                        viewModel.exportCsvAndShare(context, onlyFilteredScope = false)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Excel (CSV) Dışa Aktar — Tüm Kayıtlar")
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { includeRawInBackup = !includeRawInBackup }
                ) {
                    Checkbox(
                        checked = includeRawInBackup,
                        onCheckedChange = { includeRawInBackup = it }
                    )
                    Text(
                        text = "JSON yedeğine ham bildirim metinlerini de dahil et",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(
                    onClick = {
                        showExportSheet = false
                        jsonSaveLauncher.launch("hb_harcama_yedek_${state.selectedMonth}.json")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("JSON Tam Yedek Kaydet")
                }

                OutlinedButton(
                    onClick = {
                        showExportSheet = false
                        jsonOpenLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("JSON Yedekten Geri Yükle")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    editingTx?.let { tx ->
        val card = cardsMap[tx.cardKey]
        val cardDisplay = buildCardBadgeLabel(tx, card)
        val typeDisplay = DetectedCardType.fromCode(card?.cardType ?: tx.cardType).labelTr
        TransactionEditSheet(
            tx = tx,
            groups = state.cardGroups,
            cardBadgeLabel = "$cardDisplay • $typeDisplay",
            onDismiss = { editingTx = null },
            onSave = { cat, grpId, note, rememberRule ->
                viewModel.updateTransaction(tx, cat, grpId, note, rememberRule)
                editingTx = null
            },
            onRequestDelete = {
                deletingTx = tx
                editingTx = null
            }
        )
    }

    deletingTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { deletingTx = null },
            title = { Text("İşlem Silinsin mi?") },
            text = {
                Text("${tx.merchant} (${Formatters.formatMoney(tx.amount, tx.currency)}) kaydı kalıcı olarak silinecek.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        deletingTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTx = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    if (showAddDialog) {
        val defaultGroup = (state.selectedGroupFilter as? GroupFilter.Group)?.groupId
        AddManualTransactionDialog(
            cards = state.cards,
            groups = state.cardGroups,
            initialGroupId = defaultGroup,
            onDismiss = { showAddDialog = false },
            onSave = { bank, last4, cardType, grpId, merchant, amount, currency, category, isRefund, installments, ts, note ->
                viewModel.addManualTransaction(
                    bank = bank,
                    cardLast4 = last4,
                    cardType = cardType,
                    groupId = grpId,
                    merchant = merchant,
                    amount = amount,
                    currency = currency,
                    category = category,
                    isRefund = isRefund,
                    installmentCount = installments,
                    timestamp = ts,
                    note = note
                )
                showAddDialog = false
            }
        )
    }

    if (showNewGroupDialog) {
        GroupEditorDialog(
            initial = null,
            onDismiss = { showNewGroupDialog = false },
            onSave = { name, hex ->
                viewModel.saveCardGroup(name, hex)
                showNewGroupDialog = false
            },
            onDelete = null
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
    activeGroupTitle: String,
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
                text = "Net Dönem Harcaması • $activeGroupTitle",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
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
                        text = "Döviz (${foreign.currency}): ${Formatters.formatMoney(foreign.netExpense, foreign.currency)}",
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
    group: CardGroupEntity?,
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
                        text = (if (tx.isDemo) "[DEMO] " else "") + tx.merchant,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (group != null) {
                    Surface(
                        color = Color(group.colorHex).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(group.colorHex),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

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
    groups: List<CardGroupEntity>,
    cardBadgeLabel: String,
    onDismiss: () -> Unit,
    onSave: (String, String?, String, Boolean) -> Unit,
    onRequestDelete: () -> Unit
) {
    val isGenericMerchant = tx.merchant.endsWith(" İşlemi")
    var selectedCategory by remember(tx) { mutableStateOf(tx.category) }
    var selectedGroupId by remember(tx) { mutableStateOf(tx.groupId) }
    var note by remember(tx) { mutableStateOf(tx.note) }
    var rememberForMerchant by remember(tx) { mutableStateOf(!isGenericMerchant) }
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

            Text("Bölüm / Klasör (Tekil Taşıma):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedGroupId == null,
                    onClick = { selectedGroupId = null },
                    label = { Text("Bölümsüz") }
                )
                groups.forEach { grp ->
                    FilterChip(
                        selected = selectedGroupId == grp.groupId,
                        onClick = { selectedGroupId = grp.groupId },
                        label = { Text(grp.name) }
                    )
                }
            }

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

            if (!isGenericMerchant) {
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
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val channels = tx.seenPackages.ifBlank { tx.source }
                    Text(
                        text = "Doğrulanan Kanallar: $channels",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tx.rawText.isNotBlank()) {
                        Text(
                            text = tx.rawText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onRequestDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sil")
                }

                Button(onClick = { onSave(selectedCategory, selectedGroupId, note, rememberForMerchant) }) {
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
    groups: List<CardGroupEntity>,
    initialGroupId: String? = null,
    initialBank: BankProvider? = null,
    initialLast4: String? = null,
    initialCardType: DetectedCardType? = null,
    initialMerchant: String = "",
    initialAmount: Double? = null,
    initialCurrency: String = "TL",
    initialIsRefund: Boolean = false,
    initialInstallments: Int = 1,
    initialTimestamp: Long = System.currentTimeMillis(),
    initialRawText: String = "Manuel kayıt",
    dialogTitle: String = "Manuel İşlem Ekle",
    onDismiss: () -> Unit,
    onSave: (
        bank: BankProvider,
        last4: String,
        cardType: DetectedCardType,
        groupId: String?,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installments: Int,
        timestamp: Long,
        note: String
    ) -> Unit
) {
    val matchedInitialCard = remember(cards, initialBank, initialLast4) {
        if (initialBank != null && !initialLast4.isNullOrBlank()) {
            cards.find { it.bankCode == initialBank.code && it.last4 == initialLast4 }
        } else if (initialBank == null && initialLast4 == null) {
            cards.firstOrNull()
        } else {
            null
        }
    }
    var selectedBank by remember {
        mutableStateOf(
            initialBank ?: matchedInitialCard?.let { BankProvider.fromCode(it.bankCode) } ?: BankProvider.YAPI_KREDI
        )
    }
    var selectedCardType by remember {
        mutableStateOf(
            initialCardType ?: matchedInitialCard?.let { DetectedCardType.fromCode(it.cardType) } ?: DetectedCardType.CREDIT
        )
    }
    var selectedGroupId by remember {
        mutableStateOf(initialGroupId ?: matchedInitialCard?.defaultGroupId)
    }
    var cardLast4 by remember { mutableStateOf(initialLast4 ?: matchedInitialCard?.last4 ?: "") }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var amountText by remember {
        mutableStateOf(
            if (initialAmount != null && initialAmount > 0.0) String.format(Locale.US, "%.2f", initialAmount) else ""
        )
    }
    var currency by remember { mutableStateOf(initialCurrency) }
    var category by remember {
        mutableStateOf(
            if (initialMerchant.isNotBlank()) CategoryClassifier.classify(initialMerchant) else CategoryClassifier.OTHER
        )
    }
    var isRefund by remember { mutableStateOf(initialIsRefund) }
    var installmentsText by remember { mutableStateOf(initialInstallments.toString()) }
    var dateText by remember { mutableStateOf(Formatters.formatDateInput(initialTimestamp)) }
    var note by remember { mutableStateOf("") }
    var catExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (initialRawText != "Manuel kayıt" && initialRawText.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = initialRawText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Quick pick from saved cards
                if (cards.isNotEmpty()) {
                    Text("Kayıtlı Karttan Hızlı Seç:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cards.forEach { c ->
                            FilterChip(
                                selected = selectedBank.code == c.bankCode && cardLast4 == c.last4,
                                onClick = {
                                    selectedBank = BankProvider.fromCode(c.bankCode)
                                    cardLast4 = c.last4
                                    selectedCardType = DetectedCardType.fromCode(c.cardType)
                                    selectedGroupId = c.defaultGroupId
                                },
                                label = { Text(c.alias) }
                            )
                        }
                    }
                }

                // Bank selector (13 banks + Cash)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (BankProvider.selectableBanks + BankProvider.OTHER).forEach { b ->
                        FilterChip(
                            selected = selectedBank == b,
                            onClick = {
                                selectedBank = b
                                if (b == BankProvider.OTHER) {
                                    selectedCardType = DetectedCardType.CASH
                                    cardLast4 = "NAKIT"
                                }
                            },
                            label = { Text(if (b == BankProvider.OTHER) "Nakit" else b.displayName) }
                        )
                    }
                }

                // Group selector
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("Bölümsüz") }
                    )
                    groups.forEach { g ->
                        FilterChip(
                            selected = selectedGroupId == g.groupId,
                            onClick = { selectedGroupId = g.groupId },
                            label = { Text(g.name) }
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
                val isDateValid = isValidDateInput(dateText)
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    isError = !isDateValid,
                    supportingText = if (!isDateValid) {
                        { Text("Geçerli tarih girin: gg.aa.yyyy (ör: 10.11.2026)") }
                    } else null,
                    label = { Text("Tarih (gg.aa.yyyy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                enabled = isValidDateInput(dateText),
                onClick = {
                    val parsedAmount = YkbTransactionParser.parseFlexibleAmount(amountText) ?: 0.0
                    if (parsedAmount > 0.0 && isValidDateInput(dateText)) {
                        val resolvedTs = parseUserDateOrFallback(dateText, initialTimestamp)
                        onSave(
                            selectedBank,
                            cardLast4,
                            selectedCardType,
                            selectedGroupId,
                            merchant,
                            parsedAmount,
                            currency.ifBlank { "TL" },
                            category,
                            isRefund,
                            installmentsText.toIntOrNull() ?: 1,
                            resolvedTs,
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

private val STRICT_DATE_REGEX = Regex("""^\d{2}\.\d{2}\.\d{4}$""")

private fun isValidDateInput(input: String): Boolean {
    val trimmed = input.trim()
    if (!STRICT_DATE_REGEX.matches(trimmed)) return false
    return try {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("tr-TR")).apply {
            isLenient = false
        }
        fmt.parse(trimmed) != null
    } catch (_: Throwable) {
        false
    }
}

private fun parseUserDateOrFallback(input: String, fallbackMillis: Long): Long {
    val trimmed = input.trim()
    if (trimmed == Formatters.formatDateInput(fallbackMillis)) {
        return fallbackMillis
    }
    if (!STRICT_DATE_REGEX.matches(trimmed)) {
        return fallbackMillis
    }
    return try {
        val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("tr-TR")).apply {
            isLenient = false
        }
        fmt.parse(trimmed)?.time ?: fallbackMillis
    } catch (_: Throwable) {
        fallbackMillis
    }
}
