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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.UnknownCardPolicy
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.ui.DashboardUiState
import com.hasanbucak.harcamatakip.ui.Formatters
import com.hasanbucak.harcamatakip.ui.MainViewModel

@Composable
fun CardsScreen(
    state: DashboardUiState,
    viewModel: MainViewModel
) {
    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var showNewCardDialog by remember { mutableStateOf(false) }
    var deletingCard by remember { mutableStateOf<CardEntity?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<CardGroupEntity?>(null) }
    var policyBank by remember { mutableStateOf(BankProvider.YAPI_KREDI) }

    val groupsById = remember(state.cardGroups) {
        state.cardGroups.associateBy { it.groupId }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCardDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Yeni Kart Ekle")
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
            // 1. Groups / Folders Management Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Harcama Bölümleri (Klasörler)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            AssistChip(
                                onClick = {
                                    editingGroup = null
                                    showGroupDialog = true
                                },
                                label = { Text("+ Bölüm Ekle") }
                            )
                        }
                        Text(
                            text = "Kartlarını Ev, İş / Şirket, Kişisel gibi ayrı bölümlere bağlayabilir; ana ekranda hem ortak toplamı (Tümü) hem de her bölümün ayrı analiz ve PDF/Excel raporunu alabilirsin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.cardGroups.forEach { grp ->
                                val grpNet = state.groupPeriodTotalsTl[grp.groupId] ?: 0.0
                                Surface(
                                    color = Color(grp.colorHex).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable {
                                        editingGroup = grp
                                        showGroupDialog = true
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = grp.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(grp.colorHex)
                                        )
                                        Text(
                                            text = Formatters.formatMoney(grpNet, "TL"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Cards Header
            item {
                Text(
                    text = "Kayıtlı Kartlarım (${state.cards.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Kartın son 4 hanesini, bankasını, kart türünü ve ait olduğu bölümü tanımla. Bildirimden gelen son 4 hane bu kartlarla otomatik eşleşir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.cards.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Henüz kayıtlı kart yok",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sağ alttaki (+) butonuyla takip etmek istediğin kartların son 4 hanesini hemen ekleyebilir veya aşağıdaki banka politikasından otomatik kart keşfini açabilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.cards, key = { it.cardKey }) { card ->
                    val netTl = state.cardPeriodTotalsTl[card.cardKey] ?: 0.0
                    val grp = card.defaultGroupId?.let { groupsById[it] }
                    CardItemRow(
                        card = card,
                        group = grp,
                        periodNetTl = netTl,
                        periodLabel = state.periodLabel,
                        onClick = { editingCard = card }
                    )
                }
            }

            // 3. Per-Bank UnknownCardPolicy Configuration
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Banka Bazlı Kart Eşleştirme Kuralı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Örnek: Tüm sanal/ek kartlarını almak istediğin bankada 'Otomatik Ekle', yalnız eklediğin tek kartı (ör. sonu 6251) almak istediğin bankada 'Kayıtlı Olmayanı Yoksay' seç.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BankProvider.selectableBanks.forEach { bank ->
                                FilterChip(
                                    selected = policyBank == bank,
                                    onClick = { policyBank = bank },
                                    label = { Text(bank.displayName) }
                                )
                            }
                        }

                        val activePolicy = state.bankRules[policyBank.code] ?: UnknownCardPolicy.ASK
                        UnknownCardPolicy.entries.forEach { option ->
                            val isSelected = activePolicy == option
                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setBankUnknownCardPolicy(policyBank.code, option)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${if (isSelected) "✓ " else ""}${option.labelTr}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = option.descriptionTr,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCard?.let { card ->
        CardEditorDialog(
            initial = card,
            groups = state.cardGroups,
            isNew = false,
            onDismiss = { editingCard = null },
            onSave = { updated, applyGroupToPast ->
                viewModel.saveCard(updated, applyGroupToPast)
                editingCard = null
            },
            onRequestDelete = {
                deletingCard = card
                editingCard = null
            }
        )
    }

    if (showNewCardDialog) {
        val newCardTemplate = remember {
            CardEntity(
                cardKey = "",
                bankCode = BankProvider.YAPI_KREDI.code,
                bankName = BankProvider.YAPI_KREDI.displayName,
                last4 = "",
                alias = "",
                cardType = DetectedCardType.CREDIT.name,
                defaultGroupId = state.cardGroups.firstOrNull()?.groupId,
                statementDay = 1,
                colorHex = BankProvider.YAPI_KREDI.defaultColorHex,
                createdAt = 0L
            )
        }
        CardEditorDialog(
            initial = newCardTemplate,
            groups = state.cardGroups,
            isNew = true,
            onDismiss = { showNewCardDialog = false },
            onSave = { created, _ ->
                viewModel.saveCard(created, applyGroupToPastTransactions = false)
                showNewCardDialog = false
            },
            onRequestDelete = null
        )
    }

    deletingCard?.let { card ->
        DeleteCardConfirmDialog(
            card = card,
            onDismiss = { deletingCard = null },
            onArchiveOnly = {
                viewModel.removeCard(card.cardKey, deleteTransactionsToo = false)
                deletingCard = null
            },
            onDeleteWithTransactions = {
                viewModel.removeCard(card.cardKey, deleteTransactionsToo = true)
                deletingCard = null
            }
        )
    }

    if (showGroupDialog) {
        GroupEditorDialog(
            initial = editingGroup,
            onDismiss = {
                showGroupDialog = false
                editingGroup = null
            },
            onSave = { name, colorHex ->
                viewModel.saveCardGroup(name, colorHex, editingGroup?.groupId)
                showGroupDialog = false
                editingGroup = null
            },
            onDelete = editingGroup?.let { grp ->
                {
                    viewModel.deleteCardGroup(grp.groupId, moveToGroupId = null)
                    showGroupDialog = false
                    editingGroup = null
                }
            }
        )
    }
}

@Composable
private fun CardItemRow(
    card: CardEntity,
    group: CardGroupEntity?,
    periodNetTl: Double,
    periodLabel: String,
    onClick: () -> Unit
) {
    val typeLabel = DetectedCardType.fromCode(card.cardType).labelTr

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(card.colorHex)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = card.alias,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        color = Color(card.colorHex).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = card.bankName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(card.colorHex),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Son 4: ${card.last4} • Kesim: Ayın ${card.statementDay}'i",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$periodLabel: ${Formatters.formatMoney(periodNetTl, "TL")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Kartı Düzenle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardEditorDialog(
    initial: CardEntity,
    groups: List<CardGroupEntity>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CardEntity, Boolean) -> Unit,
    onRequestDelete: (() -> Unit)?
) {
    var selectedBank by remember(initial.cardKey, isNew) {
        mutableStateOf(
            BankProvider.fromCode(initial.bankCode).let {
                if (isNew && it == BankProvider.OTHER) BankProvider.YAPI_KREDI else it
            }
        )
    }
    var last4 by remember(initial.cardKey, isNew) { mutableStateOf(initial.last4) }
    var alias by remember(initial.cardKey, isNew) { mutableStateOf(initial.alias) }
    var cardType by remember(initial.cardKey, isNew) { mutableStateOf(initial.cardType) }
    var selectedGroupId by remember(initial.cardKey, isNew) { mutableStateOf(initial.defaultGroupId) }
    var applyGroupToPast by remember(initial.cardKey, isNew) { mutableStateOf(false) }
    var statementDayText by remember(initial.cardKey, isNew) { mutableStateOf(initial.statementDay.toString()) }

    val colorChoices = listOf(
        0xFF1E3A8A,
        0xFFB45309,
        0xFF0F766E,
        0xFF15803D,
        0xFF7C3AED,
        0xFFDC2626,
        0xFF0284C7
    )
    var selectedColor by remember(initial) { mutableStateOf(initial.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Yeni Kart Ekle" else "Kartı Düzenle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isNew) {
                    Text("Banka Seç:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BankProvider.selectableBanks.forEach { b ->
                            FilterChip(
                                selected = selectedBank == b,
                                onClick = {
                                    selectedBank = b
                                    selectedColor = b.defaultColorHex
                                },
                                label = { Text(b.displayName) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = last4,
                    onValueChange = { if (isNew) last4 = it.filter { ch -> ch.isLetterOrDigit() }.take(8) },
                    enabled = isNew,
                    label = { Text("Kart Son 4 Hanesi (ör: 6251, 4821)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(40) },
                    label = { Text("Kart Takma Adı (ör: Şirket Kartı, Ev Market)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Bölüm / Klasör:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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

                if (!isNew && selectedGroupId != initial.defaultGroupId) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { applyGroupToPast = !applyGroupToPast }
                    ) {
                        Checkbox(checked = applyGroupToPast, onCheckedChange = { applyGroupToPast = it })
                        Text(
                            text = "Bu kartın geçmiş harcamalarını da bu bölüme taşı (elle taşınanlar korunur)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text("Kart Türü:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        DetectedCardType.CREDIT,
                        DetectedCardType.VIRTUAL,
                        DetectedCardType.DEBIT
                    ).forEach { ct ->
                        FilterChip(
                            selected = cardType == ct.name,
                            onClick = { cardType = ct.name },
                            label = { Text(ct.labelTr) }
                        )
                    }
                }

                OutlinedTextField(
                    value = statementDayText,
                    onValueChange = { statementDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Hesap Kesim Günü (1 - 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorChoices.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(if (selectedColor == hex) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                if (!isNew && onRequestDelete != null) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = onRequestDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kartı Arşivle / Sil")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanLast4 = last4.trim()
                    if (cleanLast4.isNotEmpty()) {
                        val day = (statementDayText.toIntOrNull() ?: 1).coerceIn(1, 31)
                        val effectiveBankCode = if (isNew) selectedBank.code else initial.bankCode
                        val effectiveBankName = if (isNew) selectedBank.displayName else initial.bankName
                        val cleanAlias = alias.trim().ifEmpty { "$effectiveBankName •••• $cleanLast4" }
                        val key = if (isNew) {
                            CardEntity.buildKey(effectiveBankCode, cleanLast4)
                        } else {
                            initial.cardKey
                        }
                        onSave(
                            initial.copy(
                                cardKey = key,
                                bankCode = effectiveBankCode,
                                bankName = effectiveBankName,
                                last4 = cleanLast4,
                                alias = cleanAlias,
                                cardType = cardType,
                                defaultGroupId = selectedGroupId,
                                statementDay = day,
                                colorHex = selectedColor
                            ),
                            applyGroupToPast
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

@Composable
private fun DeleteCardConfirmDialog(
    card: CardEntity,
    onDismiss: () -> Unit,
    onArchiveOnly: () -> Unit,
    onDeleteWithTransactions: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${card.alias} Silinsin mi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Kartı arşivleyip engellersen geçmiş harcamaların raporlarda korunur ve bu karttan yeni bildirim gelirse tekrar eklenmez.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = onArchiveOnly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sadece Kartı Engelle (Geçmiş Kalsın)")
                }
                Button(
                    onClick = onDeleteWithTransactions,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kartı ve Tüm Harcamalarını Sil")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
fun GroupEditorDialog(
    initial: CardGroupEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    val colors = listOf(0xFF1D4ED8L, 0xFF15803DL, 0xFFB45309L, 0xFF7C3AEDL, 0xFFBE123CL, 0xFF0F766EL)
    var selectedColor by remember(initial) { mutableStateOf(initial?.colorHex ?: colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Bölüm / Klasör Ekle" else "Bölümü Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("Bölüm Adı (ör: İş / Şirket, Ev, Araç, Tatil)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(if (selectedColor == hex) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                if (initial != null && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Bölümü Sil (Kayıtlar Bölümsüz'e Taşınır)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), selectedColor)
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
