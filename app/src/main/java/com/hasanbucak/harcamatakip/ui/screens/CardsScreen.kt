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
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCardDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Kart Tanımla")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Kartlarım (Yapı Kredi • VakıfBank • TOM Bank)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Yapı Kredi ve TOM Bank için tüm kartlar, VakıfBank için yalnızca 6251 ile biten kart takip edilir. Karta dokunarak takma adını, kart türünü (Kredi / Dijital / Banka Kartı) ve hesap kesim gününü düzenleyebilirsin.",
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
                                text = "İlk harcama bildirimi veya SMS geldiğinde kart otomatik tanımlanacak veya sağ alttan şimdiden ekleyebilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.cards, key = { it.cardKey }) { card ->
                    val netTl = state.cardPeriodTotalsTl[card.cardKey] ?: 0.0
                    CardItemRow(
                        card = card,
                        periodNetTl = netTl,
                        periodLabel = state.periodLabel,
                        onClick = { editingCard = card }
                    )
                }
            }
        }
    }

    editingCard?.let { card ->
        CardEditorDialog(
            initial = card,
            isNew = false,
            onDismiss = { editingCard = null },
            onSave = { updated ->
                viewModel.saveCard(updated)
                editingCard = null
            }
        )
    }

    if (showNewCardDialog) {
        CardEditorDialog(
            initial = CardEntity(
                cardKey = "",
                bankCode = BankProvider.YAPI_KREDI.code,
                bankName = BankProvider.YAPI_KREDI.displayName,
                last4 = "",
                alias = "",
                cardType = DetectedCardType.CREDIT.name,
                statementDay = 1,
                colorHex = BankProvider.YAPI_KREDI.defaultColorHex
            ),
            isNew = true,
            onDismiss = { showNewCardDialog = false },
            onSave = { created ->
                viewModel.saveCard(created)
                showNewCardDialog = false
            }
        )
    }
}

@Composable
private fun CardItemRow(
    card: CardEntity,
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
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = card.alias,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (CardEntity) -> Unit
) {
    var selectedBank by remember(initial) {
        mutableStateOf(BankProvider.fromCode(initial.bankCode))
    }
    var last4 by remember(initial) { mutableStateOf(initial.last4) }
    var alias by remember(initial) { mutableStateOf(initial.alias) }
    var cardType by remember(initial) { mutableStateOf(initial.cardType) }
    var statementDayText by remember(initial) { mutableStateOf(initial.statementDay.toString()) }

    val colorChoices = listOf(
        0xFF1E3A8A,
        0xFFB45309,
        0xFF0F766E,
        0xFF7C3AED,
        0xFFBE123C
    )
    var selectedColor by remember(initial) { mutableStateOf(initial.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Yeni Kart Tanımla" else "Kartı Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isNew) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            BankProvider.YAPI_KREDI,
                            BankProvider.VAKIFBANK,
                            BankProvider.TOM_BANK
                        ).forEach { b ->
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
                    onValueChange = { if (isNew) last4 = it.take(10) },
                    enabled = isNew,
                    label = { Text("Kart Son 4 Hane (ör: 6251)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(40) },
                    label = { Text("Kart Takma Adı (ör: Ana World, Vakıf 6251, Hadi Kart)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = statementDayText,
                    onValueChange = { statementDayText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Hesap Kesim Günü (1 - 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanLast4 = last4.trim()
                    if (cleanLast4.isNotEmpty()) {
                        val day = (statementDayText.toIntOrNull() ?: 1).coerceIn(1, 31)
                        val cleanAlias = alias.trim().ifEmpty { "${selectedBank.displayName} •••• $cleanLast4" }
                        val key = if (isNew) {
                            CardEntity.buildKey(selectedBank.code, cleanLast4)
                        } else {
                            initial.cardKey
                        }
                        onSave(
                            initial.copy(
                                cardKey = key,
                                bankCode = selectedBank.code,
                                bankName = selectedBank.displayName,
                                last4 = cleanLast4,
                                alias = cleanAlias,
                                cardType = cardType,
                                statementDay = day,
                                colorHex = selectedColor
                            )
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
