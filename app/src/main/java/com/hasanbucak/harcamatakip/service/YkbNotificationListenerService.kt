package com.hasanbucak.harcamatakip.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hasanbucak.harcamatakip.HarcamaApp
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class YkbNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val directBank = YkbTransactionParser.resolveBankForPackage(pkg)
        val isMessagingApp = pkg in YkbTransactionParser.MESSAGING_PACKAGES

        if (directBank == null && !isMessagingApp) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val body = when {
            bigText.isNotBlank() -> bigText
            normalText.isNotBlank() -> normalText
            else -> subText
        }
        if (body.isBlank() && title.isBlank()) return

        // For Google/Samsung Messages (SMS/RCS), ONLY accept known bank sender headers
        val resolvedBank: BankProvider = if (directBank != null) {
            directBank
        } else {
            YkbTransactionParser.resolveBankFromSmsSender(title) ?: return
        }

        val (sourceTag, channelLabel) = when {
            isMessagingApp -> "RCS_MSG" to "Mesajlar ($title)"
            pkg == "com.ykb.avm" -> "PUSH_WORLD" to "World Mobil ($pkg)"
            pkg == "com.ykb.android" -> "PUSH_YKB" to "Yapı Kredi Mobil ($pkg)"
            resolvedBank == BankProvider.VAKIFBANK -> "PUSH_VAKIF" to "VakıfBank Mobil ($pkg)"
            resolvedBank == BankProvider.TOM_BANK -> "PUSH_TOM" to "TOM Bank Hadi ($pkg)"
            else -> "PUSH_BANK" to pkg
        }

        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val app = applicationContext as? HarcamaApp ?: return

        serviceScope.launch {
            app.repository.ingestIncomingMessage(
                sourceChannel = channelLabel,
                sourceTag = sourceTag,
                bankHint = resolvedBank,
                title = title,
                body = body,
                timestamp = postedAt
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, YkbNotificationListenerService::class.java))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
