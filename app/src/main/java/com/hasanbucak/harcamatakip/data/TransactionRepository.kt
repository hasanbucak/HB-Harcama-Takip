package com.hasanbucak.harcamatakip.data

import com.hasanbucak.harcamatakip.data.local.AppDatabase
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.MerchantRuleEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
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

        private val YKB_CARD_PALETTE: List<Long> = listOf(
            0xFF1E3A8A, // Navy Blue
            0xFF7C3AED, // Violet
            0xFF1D4ED8, // Royal Blue
            0xFFBE123C  // Rose
        )
    }

    suspend fun ensurePreconfiguredCards() {
        val vakifKey = CardEntity.buildKey(
            BankProvider.VAKIFBANK.code,
            YkbTransactionParser.VAKIFBANK_ALLOWED_LAST4
        )
        db.cardDao().insertIfAbsent(
            CardEntity(
                cardKey = vakifKey,
                bankCode = BankProvider.VAKIFBANK.code,
                bankName = BankProvider.VAKIFBANK.displayName,
                last4 = YkbTransactionParser.VAKIFBANK_ALLOWED_LAST4,
                alias = "VakıfBank •••• 6251",
                cardType = DetectedCardType.CREDIT.name,
                statementDay = 1,
                colorHex = BankProvider.VAKIFBANK.defaultColorHex
            )
        )
    }

    suspend fun ingestIncomingMessage(
        sourceChannel: String,
        sourceTag: String,
        bankHint: BankProvider,
        title: String?,
        body: String?,
        timestamp: Long = System.currentTimeMillis()
    ): ParseOutcome = ingestMutex.withLock {
        val safeTitle = YkbTransactionParser.sanitizeForLog(title?.trim().orEmpty())
        val safeBody = YkbTransactionParser.sanitizeForLog(body?.trim().orEmpty())

        val outcome = YkbTransactionParser.parse(
            title = title,
            body = body,
            bankHint = bankHint
        )
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
                    bankCode = parsed.bank.code,
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
                            detail = "3 dk içinde ${parsed.bank.displayName} (${parsed.cardLast4}) için ${parsed.amount} ${parsed.currency} zaten kayıtlı",
                            timestamp = timestamp
                        )
                    )
                    db.rawLogDao().pruneOldLogs()
                    return@withLock outcome
                }

                val card = ensureCardExists(
                    bank = parsed.bank,
                    last4 = parsed.cardLast4,
                    detectedType = parsed.detectedCardType
                )

                val normalizedMerchant = CategoryClassifier.normalizeMerchantKey(parsed.merchant)
                val savedRuleCategory = if (normalizedMerchant.isNotEmpty()) {
                    db.merchantRuleDao().findCategory(normalizedMerchant)
                } else {
                    null
                }
                val finalCategory = savedRuleCategory ?: CategoryClassifier.classify(parsed.merchant)
                val typeLabel = DetectedCardType.fromCode(card.cardType).labelTr

                db.transactionDao().insert(
                    TransactionEntity(
                        cardKey = card.cardKey,
                        bankCode = card.bankCode,
                        bankName = card.bankName,
                        cardLast4 = card.last4,
                        cardType = card.cardType,
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
                        detail = "${card.bankName} (${card.last4} • $typeLabel) -> ${parsed.merchant} • ${parsed.amount} ${parsed.currency}",
                        timestamp = timestamp
                    )
                )
                db.rawLogDao().pruneOldLogs()
            }
        }
        outcome
    }

    private suspend fun ensureCardExists(
        bank: BankProvider,
        last4: String,
        detectedType: DetectedCardType
    ): CardEntity {
        val cardKey = CardEntity.buildKey(bank.code, last4)
        val existing = db.cardDao().getCard(cardKey)
        if (existing != null) return existing

        val count = db.cardDao().countCards()
        val color = when (bank) {
            BankProvider.YAPI_KREDI -> YKB_CARD_PALETTE[count % YKB_CARD_PALETTE.size]
            else -> bank.defaultColorHex
        }

        val defaultAlias = when {
            last4 == "NAKIT" -> "Nakit"
            last4 == "GENEL" -> "${bank.displayName} Kartım"
            bank == BankProvider.VAKIFBANK -> "VakıfBank •••• $last4"
            bank == BankProvider.TOM_BANK -> "TOM Bank •••• $last4"
            detectedType == DetectedCardType.VIRTUAL -> "Yapı Kredi Dijital •••• $last4"
            detectedType == DetectedCardType.DEBIT -> "Yapı Kredi TLcard •••• $last4"
            else -> "Yapı Kredi •••• $last4"
        }

        val created = CardEntity(
            cardKey = cardKey,
            bankCode = bank.code,
            bankName = bank.displayName,
            last4 = last4,
            alias = defaultAlias,
            cardType = detectedType.name,
            statementDay = 1,
            colorHex = color
        )
        db.cardDao().insertIfAbsent(created)
        return db.cardDao().getCard(cardKey) ?: created
    }

    suspend fun addManualTransaction(
        bank: BankProvider,
        cardLast4: String,
        cardType: DetectedCardType,
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
        val cleanLast4 = cardLast4.trim().ifEmpty {
            if (cardType == DetectedCardType.CASH) "NAKIT" else "GENEL"
        }
        val effectiveBank = if (cardType == DetectedCardType.CASH) BankProvider.OTHER else bank
        val card = ensureCardExists(effectiveBank, cleanLast4, cardType)

        val cleanMerchant = merchant.trim().ifEmpty { "Genel Harcama" }
        val normalized = CategoryClassifier.normalizeMerchantKey(cleanMerchant)

        db.transactionDao().insert(
            TransactionEntity(
                cardKey = card.cardKey,
                bankCode = card.bankCode,
                bankName = if (cardType == DetectedCardType.CASH) "Nakit" else card.bankName,
                cardLast4 = card.last4,
                cardType = cardType.name,
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
        db.transactionDao().updateCardMetadataForTransactions(
            cardKey = card.cardKey,
            cardType = card.cardType,
            bankName = card.bankName
        )
    }

    suspend fun clearRawLogs() {
        db.rawLogDao().clearAll()
    }

    suspend fun buildCsvExport(): String {
        val all = db.transactionDao().getAllTransactionsSnapshot()
        val cardsByKey = mutableMapOf<String, CardEntity>()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Tarih;Banka;KartSon4;KartAdi;KartTuru;Isyeri;Kategori;IslemTipi;Taksit;Tutar;ParaBirimi;Kaynak;Not")
        for (tx in all) {
            val card = cardsByKey.getOrPut(tx.cardKey) {
                db.cardDao().getCard(tx.cardKey) ?: CardEntity(
                    cardKey = tx.cardKey,
                    bankCode = tx.bankCode,
                    bankName = tx.bankName,
                    last4 = tx.cardLast4,
                    alias = tx.cardLast4,
                    cardType = tx.cardType
                )
            }
            val dateStr = fmt.format(Date(tx.timestamp))
            val signedAmount = if (tx.transactionType == ParsedTransactionType.REFUND.name) {
                -tx.amount
            } else {
                tx.amount
            }
            val cardTypeTr = DetectedCardType.fromCode(tx.cardType).labelTr
            sb.appendLine(
                listOf(
                    dateStr,
                    escapeCsv(tx.bankName),
                    escapeCsv(tx.cardLast4),
                    escapeCsv(card.alias),
                    escapeCsv(cardTypeTr),
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
