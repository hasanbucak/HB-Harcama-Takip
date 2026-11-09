package com.hasanbucak.harcamatakip.data

import com.hasanbucak.harcamatakip.data.local.AppDatabase
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.MerchantRuleEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.CategoryClassifier
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParseOutcome
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionRepository(private val db: AppDatabase) {

    private val ingestMutex = Mutex()

    val transactionsFlow: Flow<List<TransactionEntity>> = db.transactionDao().observeAllTransactions()
    val cardsFlow: Flow<List<CardEntity>> = db.cardDao().observeAllCards()
    val recentLogsFlow: Flow<List<RawNotificationLogEntity>> = db.rawLogDao().observeRecentLogs()

    companion object {
        private const val DEDUP_WINDOW_MS = 3 * 60 * 1000L

        private val CARD_PALETTE: List<Long> = listOf(
            0xFF1E3A8A, // Navy Blue
            0xFF0F766E, // Teal
            0xFF7C3AED, // Violet
            0xFFB45309, // Amber
            0xFFBE123C, // Rose
            0xFF1D4ED8  // Royal Blue
        )
    }

    suspend fun ingestIncomingMessage(
        sourceChannel: String,
        sourceTag: String,
        title: String?,
        body: String?,
        timestamp: Long = System.currentTimeMillis()
    ): ParseOutcome = ingestMutex.withLock {
        val safeTitle = YkbTransactionParser.sanitizeForLog(title?.trim().orEmpty())
        val safeBody = YkbTransactionParser.sanitizeForLog(body?.trim().orEmpty())

        val outcome = YkbTransactionParser.parse(title, body)
        when (outcome) {
            is ParseOutcome.Ignored -> {
                db.rawLogDao().insert(
                    RawNotificationLogEntity(
                        sourceChannel = sourceChannel,
                        title = safeTitle,
                        body = safeBody,
                        status = "IGNORED",
                        detail = outcome.reason,
                        timestamp = timestamp
                    )
                )
                db.rawLogDao().pruneOldLogs()
            }

            is ParseOutcome.Unparsed -> {
                db.rawLogDao().insert(
                    RawNotificationLogEntity(
                        sourceChannel = sourceChannel,
                        title = safeTitle,
                        body = safeBody,
                        status = "UNPARSED",
                        detail = outcome.reason,
                        timestamp = timestamp
                    )
                )
                db.rawLogDao().pruneOldLogs()
            }

            is ParseOutcome.Success -> {
                val parsed = outcome.transaction
                val txTypeStr = parsed.transactionType.name
                val dupCount = db.transactionDao().countRecentDuplicate(
                    cardLast4 = parsed.cardLast4,
                    amount = parsed.amount,
                    currency = parsed.currency,
                    transactionType = txTypeStr,
                    sinceTimestamp = timestamp - DEDUP_WINDOW_MS
                )

                if (dupCount > 0) {
                    db.rawLogDao().insert(
                        RawNotificationLogEntity(
                            sourceChannel = sourceChannel,
                            title = safeTitle,
                            body = safeBody,
                            status = "DUPLICATE",
                            detail = "3 dk içinde aynı kart (${parsed.cardLast4}) ve tutar (${parsed.amount} ${parsed.currency}) zaten kayıtlı",
                            timestamp = timestamp
                        )
                    )
                    db.rawLogDao().pruneOldLogs()
                    return@withLock outcome
                }

                ensureCardExists(parsed.cardLast4, parsed.detectedCardType)

                val normalizedMerchant = CategoryClassifier.normalizeMerchantKey(parsed.merchant)
                val savedRuleCategory = if (normalizedMerchant.isNotEmpty()) {
                    db.merchantRuleDao().findCategory(normalizedMerchant)
                } else {
                    null
                }
                val finalCategory = savedRuleCategory ?: CategoryClassifier.classify(parsed.merchant)

                db.transactionDao().insert(
                    TransactionEntity(
                        cardLast4 = parsed.cardLast4,
                        merchant = parsed.merchant,
                        normalizedMerchant = normalizedMerchant,
                        amount = parsed.amount,
                        currency = parsed.currency,
                        category = finalCategory,
                        transactionType = txTypeStr,
                        installmentCount = parsed.installmentCount,
                        source = sourceTag,
                        timestamp = timestamp,
                        rawText = if (safeTitle.isNotEmpty()) "$safeTitle\n$safeBody" else safeBody
                    )
                )

                db.rawLogDao().insert(
                    RawNotificationLogEntity(
                        sourceChannel = sourceChannel,
                        title = safeTitle,
                        body = safeBody,
                        status = "SAVED",
                        detail = "${parsed.merchant} • ${parsed.amount} ${parsed.currency} (${parsed.cardLast4})",
                        timestamp = timestamp
                    )
                )
                db.rawLogDao().pruneOldLogs()
            }
        }
        outcome
    }

    private suspend fun ensureCardExists(last4: String, detectedType: DetectedCardType) {
        val existing = db.cardDao().getCard(last4)
        if (existing != null) return

        val count = db.cardDao().countCards()
        val color = CARD_PALETTE[count % CARD_PALETTE.size]
        val defaultAlias = when {
            last4 == "GENEL" -> "Yapı Kredi Kartım"
            last4 == "NAKIT" -> "Nakit"
            detectedType == DetectedCardType.VIRTUAL -> "Dijital Kart •••• $last4"
            detectedType == DetectedCardType.DEBIT -> "TLcard •••• $last4"
            else -> "Worldcard •••• $last4"
        }

        db.cardDao().insertIfAbsent(
            CardEntity(
                last4 = last4,
                alias = defaultAlias,
                cardType = detectedType.name,
                statementDay = 1,
                colorHex = color
            )
        )
    }

    suspend fun addManualTransaction(
        cardLast4: String,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        transactionType: ParsedTransactionType,
        installmentCount: Int,
        note: String,
        rawText: String = "Manuel kayıt",
        sourceLogId: Long? = null
    ) {
        val cleanLast4 = cardLast4.trim().ifEmpty { "NAKIT" }
        val detectedType = if (cleanLast4 == "NAKIT") DetectedCardType.DEBIT else DetectedCardType.CREDIT
        ensureCardExists(cleanLast4, detectedType)

        val cleanMerchant = merchant.trim().ifEmpty { "Genel Harcama" }
        val normalized = CategoryClassifier.normalizeMerchantKey(cleanMerchant)

        db.transactionDao().insert(
            TransactionEntity(
                cardLast4 = cleanLast4,
                merchant = cleanMerchant,
                normalizedMerchant = normalized,
                amount = amount,
                currency = currency,
                category = category,
                transactionType = transactionType.name,
                installmentCount = installmentCount.coerceIn(1, 36),
                source = "MANUAL",
                timestamp = System.currentTimeMillis(),
                rawText = rawText,
                note = note.trim()
            )
        )

        if (sourceLogId != null) {
            db.rawLogDao().updateStatus(
                id = sourceLogId,
                status = "SAVED",
                detail = "Elle harcamaya dönüştürüldü ($amount $currency)"
            )
        }
    }

    suspend fun updateTransaction(
        entity: TransactionEntity,
        newCategory: String,
        newNote: String,
        rememberCategoryForMerchant: Boolean
    ) {
        val updated = entity.copy(category = newCategory, note = newNote.trim())
        db.transactionDao().update(updated)

        if (rememberCategoryForMerchant && entity.normalizedMerchant.isNotEmpty()) {
            db.merchantRuleDao().upsert(
                MerchantRuleEntity(
                    normalizedMerchant = entity.normalizedMerchant,
                    category = newCategory
                )
            )
            db.transactionDao().updateCategoryForMerchant(entity.normalizedMerchant, newCategory)
        }
    }

    suspend fun deleteTransaction(entity: TransactionEntity) {
        db.transactionDao().delete(entity)
    }

    suspend fun saveCard(card: CardEntity) {
        db.cardDao().upsert(card)
    }

    suspend fun clearRawLogs() {
        db.rawLogDao().clearAll()
    }

    suspend fun buildCsvExport(): String {
        val all = db.transactionDao().getAllTransactionsSnapshot()
        val cardsByLast4 = mutableMapOf<String, CardEntity>()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Tarih;KartSon4;KartAdi;Isyeri;Kategori;IslemTipi;Taksit;Tutar;ParaBirimi;Kaynak;Not")
        for (tx in all) {
            val card = cardsByLast4.getOrPut(tx.cardLast4) {
                db.cardDao().getCard(tx.cardLast4)
                    ?: CardEntity(last4 = tx.cardLast4, alias = tx.cardLast4, cardType = "CREDIT")
            }
            val dateStr = fmt.format(Date(tx.timestamp))
            val signedAmount = if (tx.transactionType == ParsedTransactionType.REFUND.name) {
                -tx.amount
            } else {
                tx.amount
            }
            sb.appendLine(
                listOf(
                    dateStr,
                    escapeCsv(tx.cardLast4),
                    escapeCsv(card.alias),
                    escapeCsv(tx.merchant),
                    escapeCsv(tx.category),
                    tx.transactionType,
                    tx.installmentCount.toString(),
                    String.format(Locale.US, "%.2f", signedAmount),
                    tx.currency,
                    tx.source,
                    escapeCsv(tx.note)
                ).joinToString(";")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\n", " ").replace("\r", " ")
        return if (cleaned.contains(';') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
