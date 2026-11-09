package com.hasanbucak.harcamatakip.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.ui.DashboardUiState
import com.hasanbucak.harcamatakip.ui.Formatters
import com.hasanbucak.harcamatakip.ui.MainViewModel
import com.hasanbucak.harcamatakip.util.SystemPermissionHelper

@Composable
fun SetupAndLogsScreen(
    state: DashboardUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifEnabled by remember { mutableStateOf(SystemPermissionHelper.isNotificationListenerEnabled(context)) }
    var batteryIgnored by remember { mutableStateOf(SystemPermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var customTestBank by remember { mutableStateOf(BankProvider.YAPI_KREDI) }
    var customTestText by remember { mutableStateOf("") }
    var convertingLog by remember { mutableStateOf<RawNotificationLogEntity?>(null) }

    fun refreshPermissions() {
        notifEnabled = SystemPermissionHelper.isNotificationListenerEnabled(context)
        batteryIgnored = SystemPermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Offline privacy guarantee banner
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "İnternet izni (INTERNET) manifest'te kapalıdır. Tüm Yapı Kredi, VakıfBank ve TOM Bank kayıtları yalnızca telefonun yerel SQLite veritabanında tutulur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Permissions checklist card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Android İzin Durumu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    PermissionRow(
                        title = "Bildirim Erişimi (Yapı Kredi • VakıfBank • TOM Bank)",
                        subtitle = if (notifEnabled) {
                            "Aktif • Banka Push ve RCS/Mesaj bildirimleri dinleniyor"
                        } else {
                            "Kapalı • Push ve mesaj bildirimlerini okumak için açılmalı"
                        },
                        granted = notifEnabled,
                        actionLabel = "Ayarları Aç",
                        onAction = {
                            context.startActivity(SystemPermissionHelper.buildNotificationListenerSettingsIntent())
                        }
                    )

                    HorizontalDivider()

                    PermissionRow(
                        title = "Uygulama Arka Plan Ayarları (İsteğe Bağlı)",
                        subtitle = if (batteryIgnored) {
                            "Aktif • Arka plan kısıtlaması yok"
                        } else {
                            "Xiaomi / Samsung cihazlarda gecikme olursa Uygulama Bilgisi ekranından Pil -> Kısıtlama Yok seçebilirsiniz"
                        },
                        granted = batteryIgnored,
                        actionLabel = "Uygulama Bilgisi",
                        onAction = {
                            context.startActivity(SystemPermissionHelper.buildAppDetailsSettingsIntent(context))
                        }
                    )

                    if (!notifEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Android 13/14/15 'Kısıtlı Ayar' Uyarısı Çıkarsa:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "APK elle kurulduğunda izin düğmesi gri görünebilir. Aşağıdaki 'Uygulama Bilgisi' ekranını açıp sağ üstteki (⋮) menüden 'Kısıtlı ayarlara izin ver' seçeneğine dokunun.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(
                                    onClick = {
                                        context.startActivity(SystemPermissionHelper.buildAppDetailsSettingsIntent(context))
                                    }
                                ) {
                                    Text("Uygulama Bilgisi Ekranını Aç")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active bank rules summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "2. Aktif Banka ve Kart Kuralları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Yapı Kredi: Yapı Kredi Mobil (com.ykb.android), World Mobil (com.ykb.avm) ve YAPIKREDI SMS -> Tüm kartlar kaydedilir.\n• VakıfBank: VakıfBank Mobil (com.vakifbank.mobile) ve VAKIFBANK SMS -> Yalnızca sonu 6251 olan kart kaydedilir.\n• TOM Bank: TOM Bank Hadi (com.tom.hadi) ve TOMBank / HADI SMS -> Tüm kartlar kaydedilir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Simulation & custom parser tester
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
                    Text(
                        text = "3. Canlı Ayrıştırıcı (Parser) Testi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { viewModel.simulateNextSample() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Örnek Harcama Simüle Et (YKB / Vakıf 6251 / TOMBank)")
                    }

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
                                selected = customTestBank == b,
                                onClick = { customTestBank = b },
                                label = { Text(b.displayName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customTestText,
                        onValueChange = { customTestText = it },
                        label = { Text("${customTestBank.displayName} SMS / Bildirim metnini yapıştırıp test et") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customTestText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testCustomNotificationText(customTestBank, customTestText)
                                customTestText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Metni İşle ve Kaydet")
                        }
                    }

                    state.lastTestFeedback?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Raw logs header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ham Bildirim Günlüğü (${state.recentLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.recentLogs.isNotEmpty()) {
                    AssistChip(
                        onClick = { viewModel.clearRawLogs() },
                        label = { Text("Temizle") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        if (state.recentLogs.isEmpty()) {
            item {
                Text(
                    text = "Henüz yakalanan bildirim yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recentLogs, key = { it.id }) { log ->
                RawLogCard(
                    log = log,
                    onConvertUnparsed = { convertingLog = log }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    convertingLog?.let { log ->
        AddManualTransactionDialog(
            cards = state.cards,
            initialRawText = log.body,
            onDismiss = { convertingLog = null },
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
                    note = note,
                    rawText = log.body,
                    sourceLogId = log.id
                )
                convertingLog = null
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (granted) Color(0xFF16A34A) else Color(0xFFD97706),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun RawLogCard(
    log: RawNotificationLogEntity,
    onConvertUnparsed: () -> Unit
) {
    val badgeColor = when (log.status) {
        "SAVED" -> Color(0xFF16A34A)
        "DUPLICATE" -> Color(0xFF0284C7)
        "UNPARSED" -> Color(0xFFDC2626)
        else -> Color(0xFF6B7280)
    }

    val statusText = when (log.status) {
        "SAVED" -> "KAYDEDİLDİ"
        "DUPLICATE" -> "MÜKERRER ENGELLENDİ"
        "UNPARSED" -> "AYRIŞTIRILAMADI"
        else -> "ATLANDI / HARCAMA DEĞİL"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Text(
                    text = "${log.sourceChannel} • ${Formatters.formatDateTime(log.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.body.ifBlank { log.title },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = log.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (log.status == "UNPARSED") {
                TextButton(onClick = onConvertUnparsed) {
                    Text("Elle Harcamaya Dönüştür")
                }
            }
        }
    }
}
