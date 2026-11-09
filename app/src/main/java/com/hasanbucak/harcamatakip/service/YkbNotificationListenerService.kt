package com.hasanbucak.harcamatakip.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.hasanbucak.harcamatakip.HarcamaApp
import com.hasanbucak.harcamatakip.data.MessageFingerprint
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import com.hasanbucak.harcamatakip.util.ListenerHealthTracker
import kotlinx.coroutines.launch

class YkbNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerHealthTracker.recordConnected(applicationContext)

        // Recover any bank/SMS notifications posted while listener was disconnected.
        // processed_messages fingerprint table guarantees idempotency so nothing duplicates.
        val activeList = try {
            activeNotifications
        } catch (_: Throwable) {
            null
        } ?: return

        for (sbn in activeList) {
            processStatusBarNotification(sbn)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processStatusBarNotification(sbn)
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        val directBank = YkbTransactionParser.resolveBankForPackage(pkg)
        val isMessagingApp = pkg in YkbTransactionParser.MESSAGING_PACKAGES
        if (directBank == null && !isMessagingApp) return

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (sbn.isOngoing) return

        val app = applicationContext as? HarcamaApp ?: return
        val postedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val extras = notification.extras

        if (isMessagingApp) {
            val extractedMessages = extractMessagingStyleItems(notification, extras, postedAt)
            if (extractedMessages.isEmpty()) return

            app.applicationScope.launch {
                for (item in extractedMessages) {
                    try {
                        val bank = YkbTransactionParser.resolveBankFromSmsSender(item.sender) ?: continue
                        val channelLabel = "Mesajlar (${item.sender})"
                        ListenerHealthTracker.recordBankEvent(applicationContext, channelLabel)
                        val fp = MessageFingerprint.forMessagingStyle(
                            pkg = pkg,
                            sender = item.sender,
                            msgTimestamp = item.timestamp,
                            body = item.body
                        )
                        app.repository.ingestIncomingMessage(
                            sourceChannel = channelLabel,
                            sourceTag = "RCS_MSG",
                            sourcePackage = pkg,
                            fingerprint = fp,
                            bankHint = bank,
                            title = item.sender,
                            body = item.body,
                            timestamp = item.timestamp
                        )
                    } catch (_: Throwable) {
                        // Isolate single message errors so service never crashes
                    }
                }
            }
            return
        }

        // Direct banking app push notification
        val resolvedBank: BankProvider = directBank ?: return
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val body = when {
            bigText.isNotBlank() -> bigText
            normalText.isNotBlank() -> normalText
            else -> subText
        }
        if (body.isBlank() && title.isBlank()) return

        val channelLabel = "${resolvedBank.displayName} ($pkg)"
        val whenTime = notification.`when`.takeIf { it > 0L } ?: postedAt
        val fingerprint = MessageFingerprint.forNotification(
            pkg = pkg,
            sbnKey = sbn.key ?: pkg,
            whenTime = whenTime,
            title = title,
            body = body
        )

        ListenerHealthTracker.recordBankEvent(applicationContext, channelLabel)

        app.applicationScope.launch {
            try {
                app.repository.ingestIncomingMessage(
                    sourceChannel = channelLabel,
                    sourceTag = "PUSH_${resolvedBank.code}",
                    sourcePackage = pkg,
                    fingerprint = fingerprint,
                    bankHint = resolvedBank,
                    title = title,
                    body = body,
                    timestamp = postedAt
                )
            } catch (_: Throwable) {
                // Never allow uncaught DB/IO errors to terminate NotificationListenerService
            }
        }
    }

    private data class ExtractedSmsItem(
        val sender: String,
        val body: String,
        val timestamp: Long
    )

    private fun extractMessagingStyleItems(
        notification: Notification,
        extras: android.os.Bundle?,
        fallbackTime: Long
    ): List<ExtractedSmsItem> {
        val results = mutableListOf<ExtractedSmsItem>()
        val fallbackTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

        try {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (style != null && style.messages.isNotEmpty()) {
                val convTitle = style.conversationTitle?.toString().orEmpty()
                for (m in style.messages) {
                    val text = m.text?.toString().orEmpty()
                    if (text.isBlank()) continue
                    val sender = m.person?.name?.toString()?.takeIf { it.isNotBlank() }
                        ?: convTitle.takeIf { it.isNotBlank() }
                        ?: fallbackTitle
                    val ts = m.timestamp.takeIf { it > 0L } ?: fallbackTime
                    results.add(ExtractedSmsItem(sender = sender, body = text, timestamp = ts))
                }
                if (results.isNotEmpty()) {
                    return results
                }
            }
        } catch (_: Throwable) {
            // Fall back to standard extras below
        }

        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val normalText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = bigText.ifBlank { normalText }
        if (fallbackTitle.isNotBlank() && body.isNotBlank()) {
            results.add(ExtractedSmsItem(sender = fallbackTitle, body = body, timestamp = fallbackTime))
        }
        return results
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerHealthTracker.recordDisconnected(applicationContext)
        try {
            requestRebind(ComponentName(this, YkbNotificationListenerService::class.java))
        } catch (_: Throwable) {
        }
    }
}
