package com.hasanbucak.harcamatakip.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.hasanbucak.harcamatakip.HarcamaApp
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YkbSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val groupedBySender = linkedMapOf<Pair<String, BankProvider>, StringBuilder>()
        var messageTimestamp = System.currentTimeMillis()

        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
            val bank = YkbTransactionParser.resolveBankFromSmsSender(sender) ?: continue
            if (sms.timestampMillis > 0L) {
                messageTimestamp = sms.timestampMillis
            }
            val builder = groupedBySender.getOrPut(sender to bank) { StringBuilder() }
            builder.append(sms.displayMessageBody.orEmpty())
        }

        if (groupedBySender.isEmpty()) return

        val app = context.applicationContext as? HarcamaApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((senderBankPair, bodyBuilder) in groupedBySender) {
                    val (sender, bank) = senderBankPair
                    val fullBody = bodyBuilder.toString().trim()
                    if (fullBody.isNotEmpty()) {
                        app.repository.ingestIncomingMessage(
                            sourceChannel = "SMS ($sender)",
                            sourceTag = "SMS",
                            bankHint = bank,
                            title = sender,
                            body = fullBody,
                            timestamp = messageTimestamp
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
