package com.hasanbucak.harcamatakip.data

import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.CategoryClassifier
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransaction
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.min

object MessageFingerprint {

    fun forMessagingStyle(
        pkg: String,
        sender: String,
        msgTimestamp: Long,
        body: String
    ): String {
        val normSender = CategoryClassifier.normalizeMerchantKey(sender)
        val hash = sha256Hex(body.trim()).take(24)
        return "msg|$pkg|$normSender|$msgTimestamp|$hash"
    }

    /**
     * Excludes notification.when jitter so reposted/updated notifications keep a stable key.
     * TransactionRepository applies a 10-minute freshness window on live notif| fingerprints.
     */
    fun forNotification(
        pkg: String,
        sbnKey: String,
        title: String,
        body: String
    ): String {
        val hash = sha256Hex("${title.trim()}\n${body.trim()}").take(24)
        return "notif|$pkg|$sbnKey|$hash"
    }

    fun forManualOrDemo(tag: String, timestamp: Long, body: String): String {
        val hash = sha256Hex(body.trim()).take(20)
        return "local|$tag|$timestamp|$hash"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}

object DeduplicationMatcher {

    const val STANDARD_WINDOW_MS = 15 * 60 * 1000L
    const val CLOCK_MATCH_EXTENDED_WINDOW_MS = 60 * 60 * 1000L

    fun findMergeTarget(
        incoming: ParsedTransaction,
        sourcePackage: String,
        timestamp: Long,
        candidates: List<TransactionEntity>
    ): TransactionEntity? {
        val incomingNormMerchant = CategoryClassifier.normalizeMerchantKey(incoming.merchant)

        val matching = candidates.filter { cand ->
            if (cand.isDemo) return@filter false
            if (cand.bankCode != incoming.bank.code) return@filter false
            if (cand.transactionType != incoming.transactionType.name) return@filter false
            if (cand.source == "MANUAL") return@filter false

            // Same package/channel = distinct real transaction, NEVER merge
            val seenList = cand.seenPackages
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (sourcePackage in seenList) return@filter false

            // Bidirectional amount check (including USD/EUR push <-> TL SMS in EITHER arrival order!)
            val amountMatches = when {
                cand.currency == incoming.currency && abs(cand.amount - incoming.amount) < 0.01 -> true
                incoming.secondaryAmountTl != null &&
                    cand.currency == "TL" &&
                    abs(cand.amount - incoming.secondaryAmountTl) < 0.50 -> true
                cand.secondaryAmountTl != null &&
                    incoming.currency == "TL" &&
                    abs(incoming.amount - cand.secondaryAmountTl) < 0.50 -> true
                else -> false
            }
            if (!amountMatches) return@filter false

            val cardCompatible = cand.cardLast4 == incoming.cardLast4 ||
                cand.cardLast4 == "GENEL" ||
                incoming.cardLast4 == "GENEL"
            if (!cardCompatible) return@filter false

            val timeCompatible = if (cand.txClockMinute != null && incoming.txClockMinute != null) {
                val rawDiff = abs(cand.txClockMinute - incoming.txClockMinute)
                val circularDiff = min(rawDiff, 1440 - rawDiff)
                circularDiff <= 2 && abs(cand.timestamp - timestamp) <= CLOCK_MATCH_EXTENDED_WINDOW_MS
            } else {
                abs(cand.timestamp - timestamp) <= STANDARD_WINDOW_MS
            }
            if (!timeCompatible) return@filter false

            merchantsCompatible(
                candMerchant = cand.merchant,
                candNormalized = cand.normalizedMerchant,
                incomingIsGeneric = incoming.isGenericMerchant,
                incomingNormalized = incomingNormMerchant
            )
        }

        if (matching.isEmpty()) return null
        if (matching.size > 1 && matching.all { it.cardLast4 == "GENEL" }) {
            return null
        }

        return matching.minByOrNull { abs(it.timestamp - timestamp) }
    }

    private fun merchantsCompatible(
        candMerchant: String,
        candNormalized: String,
        incomingIsGeneric: Boolean,
        incomingNormalized: String
    ): Boolean {
        val candIsGeneric = candMerchant.endsWith(" İşlemi") || candNormalized.isEmpty()
        if (candIsGeneric || incomingIsGeneric || incomingNormalized.isEmpty()) return true
        if (candNormalized == incomingNormalized) return true
        if (candNormalized.contains(incomingNormalized) || incomingNormalized.contains(candNormalized)) {
            return true
        }
        val firstTokenA = candNormalized.substringBefore(' ')
        val firstTokenB = incomingNormalized.substringBefore(' ')
        return firstTokenA.length >= 3 && firstTokenA == firstTokenB
    }
}
