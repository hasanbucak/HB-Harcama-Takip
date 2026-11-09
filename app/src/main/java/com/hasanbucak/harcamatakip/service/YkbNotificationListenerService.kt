package com.hasanbucak.harcamatakip.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hasanbucak.harcamatakip.HarcamaApp
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
        if (pkg !in YkbTransactionParser.SUPPORTED_PACKAGES) return

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

        val sourceTag = if (pkg == "com.ykb.avm") "PUSH_WORLD" else "PUSH_YKB"
        val channelLabel = if (pkg == "com.ykb.avm") "World Mobil ($pkg)" else "Yapı Kredi Mobil ($pkg)"
        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        val app = applicationContext as? HarcamaApp ?: return
        serviceScope.launch {
            app.repository.ingestIncomingMessage(
                sourceChannel = channelLabel,
                sourceTag = sourceTag,
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
