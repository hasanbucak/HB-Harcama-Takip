package com.hasanbucak.harcamatakip.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.hasanbucak.harcamatakip.data.local.PendingReviewEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.ui.DashboardUiState
import com.hasanbucak.harcamatakip.ui.Formatters
import com.hasanbucak.harcamatakip.ui.MainViewModel
import com.hasanbucak.harcamatakip.util.ListenerHealthTracker
import com.hasanbucak.harcamatakip.util.SystemPermissionHelper

@Composable
fun SetupAndLogsScreen(
    state: DashboardUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifEnabled by remember { mutableStateOf(SystemPermissionHelper.isNotificationListenerEnabled(context)) }
    var disclosureAccepted by remember { mutableStateOf(SystemPermissionHelper.hasAcceptedProminentDisclosure(context)) }
    var batteryIgnored by remember { mutableStateOf(SystemPermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var showDisclosureDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    var customTestBank by remember { mutableStateOf(BankProvider.YAPI_KREDI) }
    var customTestText by remember { mutableStateOf("") }
    var testAsRealRecord by remember { mutableStateOf(false) }

    var approvingPending by remember { mutableStateOf<PendingReviewEntity?>(null) }
    var convertingLog by remember { mutableStateOf<RawNotificationLogEntity?>(null) }

    fun refreshPermissions() {
        notifEnabled = SystemPermissionHelper.isNotificationListenerEnabled(context)
        disclosureAccepted = SystemPermissionHelper.hasAcceptedProminentDisclosure(context)
        batteryIgnored = SystemPermissionHelper.isIgnoringBatteryOptimizations(context)
        ListenerHealthTracker.load(context)
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
        // 1. Offline & Privacy Guarantee Banner
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "%100 Çevrimdışı • Sıfır İnternet ve SMS İzni",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "Uygulamada INTERNET ve RECEIVE_SMS izinleri bulunmaz. Tüm harcama kayıtları yalnızca telefonun yerel SQLite veritabanında işlenir; OTP/şifre mesajları veritabanına kaydedilmez. Uygulamanın adı geçen bankalarla resmi bir bağlantısı yoktur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(onClick = { showPrivacyDialog = true }) {
                        Text("Gizlilik Politikası ve Veri Güvenliği Detayı")
                    }
                }
            }
        }

        // 2. Pending Reviews Inbox (Bekleyenler Kutusu)
        if (state.pendingReviews.isNotEmpty()) {
            item {
                Text(
                    text = "Bekleyen İşlemler Kutusu (${state.pendingReviews.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(state.pendingReviews, key = { "pending_${it.id}" }) { pending ->
                PendingReviewCard(
                    item = pending,
                    onApprove = { approvingPending = pending },
                    onBlockCard = { viewModel.dismissOrBlockPendingReview(pending.id, blockCard = true) },
                    onDismiss = { viewModel.dismissOrBlockPendingReview(pending.id, blockCard = false) }
                )
            }
        }

        // 3. Listener Health & Android Setup Card
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
                        text = "Bildirim Dinleyici ve Sistem Sağlığı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    PermissionRow(
                        title = "Bildirim Erişimi (13 Banka Push + Mesajlar/RCS)",
                        subtitle = if (notifEnabled) {
                            "Aktif • Banka uygulama bildirimleri ve SMS/RCS başlıkları dinleniyor"
                        } else {
                            "Kapalı • Otomatik harcama yakalama için açabilir veya manuel kullanabilirsin"
                        },
                        granted = notifEnabled,
                        actionLabel = "Ayarları Aç",
                        onAction = {
                            if (disclosureAccepted) {
                                context.startActivity(SystemPermissionHelper.buildNotificationListenerSettingsIntent())
                            } else {
                                showDisclosureDialog = true
                            }
                        }
                    )

                    // Listener health telemetry
                    val health = state.listenerHealth
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val connStr = if (health.lastConnectedAt > 0L) {
                                Formatters.formatDateTime(health.lastConnectedAt)
                            } else {
                                "Henüz bağlanmadı"
                            }
                            val lastEventStr = if (health.lastBankEventAt > 0L) {
                                "${Formatters.formatDateTime(health.lastBankEventAt)} (${health.lastBankEventSource})"
                            } else {
                                "Henüz banka bildirimi düşmedi"
                            }
                            Text(
                                text = "Son Servis Bağlantısı: $connStr",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "Yakalanan Son Banka Bildirimi: $lastEventStr",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    HorizontalDivider()

                    PermissionRow(
                        title = "Xiaomi HyperOS / Samsung Arka Plan Ayarı (İsteğe Bağlı)",
                        subtitle = if (batteryIgnored) {
                            "Aktif • Arka plan pil kısıtlaması kapalı"
                        } else {
                            "Bildirimler gecikirse Uygulama Bilgisi ekranından 'Otomatik Başlatma' açıp Pil -> 'Kısıtlama Yok' seçebilirsin"
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
                                    text = "Mağaza Dışı (APK) Kurulumda 'Kısıtlı Ayar' Çıkarsa:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Android 13+ cihazlarda APK elle kurulduğunda Bildirim Erişimi kilitli görünebilir. Önce 'Uygulama Bilgisi' ekranını açıp sağ üstteki (⋮) menüden 'Kısıtlı ayarlara izin ver' seçeneğini onaylayın.",
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

        // 4. Isolated Demo & Custom Parser Tester
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
                        text = "Demo Simülasyonu ve Ayrıştırıcı Testi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Demo kayıtları gerçek harcama toplamlarını ve CSV/PDF/JSON çıktılarını etkilemez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.simulateNextSample() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Demo Harcama Üret")
                        }
                        if (state.demoTransactionCount > 0) {
                            OutlinedButton(onClick = { viewModel.clearDemoTransactions() }) {
                                Text("Demo Sil (${state.demoTransactionCount})")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BankProvider.selectableBanks.forEach { b ->
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
                        label = { Text("${customTestBank.displayName} örnek bildirim metnini test et") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { testAsRealRecord = !testAsRealRecord }
                    ) {
                        Checkbox(checked = testAsRealRecord, onCheckedChange = { testAsRealRecord = it })
                        Text(
                            text = "Demo yerine gerçek harcama olarak işle",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (customTestText.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testCustomNotificationText(customTestBank, customTestText, testAsRealRecord)
                                customTestText = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Metni Ayrıştır")
                        }
                    }
                }
            }
        }

        // 5. Raw Notification Logs Header
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
                    onConvertManual = { convertingLog = log }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Google Play Mandatory Prominent Disclosure Modal
    if (showDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showDisclosureDialog = false },
            title = { Text("Bildirim Erişimi Aydınlatma Metni") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HB Harcama Takip, kart harcamalarınızı ve iadelerinizi otomatik olarak kaydedebilmek için Android Bildirim Erişimi (NotificationListenerService) iznine ihtiyaç duyar.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Neler okunur: Yalnızca desteklenen 13 banka mobil uygulamasının ve tanımlı banka SMS/RCS başlıklarının bildirimleri kontrol edilir. Kişisel sohbetleriniz okunmaz; harcama/iade olmayan bildirimlerin gövde metinleri saklanmaz.\n" +
                            "• Nasıl işlenir: Tutar, işyeri ve kart son 4 hanesi yalnızca telefonunuzun içinde ayrıştırılır. Tek kullanımlık şifre (OTP) içerikleri veritabanına yazılmaz.\n" +
                            "• Veri paylaşımı: Uygulamada INTERNET izni yoktur; hiçbir veriniz sunucuya gönderilmez veya üçüncü taraflarla paylaşılmaz.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SystemPermissionHelper.setProminentDisclosureAccepted(context, true)
                        disclosureAccepted = true
                        showDisclosureDialog = false
                        context.startActivity(SystemPermissionHelper.buildNotificationListenerSettingsIntent())
                    }
                ) {
                    Text("Kabul Ediyorum")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosureDialog = false }) {
                    Text("Reddet (Manuel Kullan)")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Gizlilik ve Veri Güvenliği Beyanı") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Sıfır Ağ Erişimi: Uygulama AndroidManifest üzerinde android.permission.INTERNET izni içermez. Verileriniz cihaz dışına çıkamaz.\n\n" +
                            "2. OTP ve Elenen Mesaj Koruması: Şifre/doğrulama kodu ve harcama olmayan mesajların gövde metinleri veritabanına kaydedilmez.\n\n" +
                            "3. Veri Saklama: Ham bildirim metinleri ve günlükler (çözülmemiş bekleyen kayıtlar hariç) 30 gün sonra otomatik temizlenir; istediğiniz zaman tüm kayıtları silebilir veya JSON yedeği alabilirsiniz.\n\n" +
                            "4. Bağımsızlık: Bu uygulama bağımsız bir kişisel finans aracıdır; adı geçen hiçbir bankayla resmi bağlantısı veya ortaklığı yoktur.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    approvingPending?.let { p ->
        val bank = BankProvider.fromCode(p.bankCode)
        AddManualTransactionDialog(
            cards = state.cards,
            groups = state.cardGroups,
            initialBank = if (bank == BankProvider.OTHER) BankProvider.YAPI_KREDI else bank,
            initialLast4 = if (p.cardLast4 == "GENEL") "" else p.cardLast4,
            initialCardType = DetectedCardType.fromCode(p.detectedCardType),
            initialMerchant = p.merchant,
            initialAmount = p.amount.takeIf { it > 0.0 },
            initialCurrency = p.currency,
            initialIsRefund = p.transactionType == ParsedTransactionType.REFUND.name,
            initialInstallments = p.installmentCount,
            initialTimestamp = p.timestamp,
            initialRawText = p.body,
            dialogTitle = "Bekleyen İşlemi Onayla & Kartı Kaydet",
            onDismiss = { approvingPending = null },
            onSave = { b, last4, cType, grpId, merch, amt, curr, cat, refund, inst, _, _ ->
                viewModel.approvePendingReview(
                    pendingId = p.id,
                    bank = b,
                    cardLast4 = last4,
                    cardType = cType,
                    groupId = grpId,
                    merchant = merch,
                    amount = amt,
                    currency = curr,
                    category = cat,
                    isRefund = refund,
                    installmentCount = inst
                )
                approvingPending = null
            }
        )
    }

    convertingLog?.let { log ->
        AddManualTransactionDialog(
            cards = state.cards,
            groups = state.cardGroups,
            initialTimestamp = log.timestamp,
            initialRawText = log.body,
            dialogTitle = "Bildirimi Harcamaya Dönüştür",
            onDismiss = { convertingLog = null },
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
private fun PendingReviewCard(
    item: PendingReviewEntity,
    onApprove: () -> Unit,
    onBlockCard: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.reasonDetail,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            if (item.amount > 0.0) {
                Text(
                    text = "${item.merchant} • ${Formatters.formatMoney(item.amount, item.currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${item.sourceChannel} • ${Formatters.formatDateTime(item.timestamp)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onApprove) {
                    Text("Kartı Ekle & Kaydet")
                }
                if (item.cardLast4 != "GENEL") {
                    OutlinedButton(onClick = onBlockCard) {
                        Text("Kartı Engelle (${item.cardLast4})")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Yoksay")
                }
            }
        }
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
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun RawLogCard(
    log: RawNotificationLogEntity,
    onConvertManual: () -> Unit
) {
    val badgeColor = when (log.status) {
        "SAVED" -> Color(0xFF16A34A)
        "DUPLICATE" -> Color(0xFF0284C7)
        "PENDING", "UNPARSED" -> Color(0xFFDC2626)
        else -> Color(0xFF6B7280)
    }

    val statusText = when (log.status) {
        "SAVED" -> "KAYDEDİLDİ"
        "DUPLICATE" -> "MÜKERRER BİRLEŞTİRİLDİ"
        "PENDING" -> "ONAY BEKLİYOR"
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
            if (log.body.isNotBlank() && (log.status == "UNPARSED" || log.status == "IGNORED" || log.status == "PENDING")) {
                TextButton(onClick = onConvertManual) {
                    Text("Elle Harcamaya Dönüştür")
                }
            }
        }
    }
}
