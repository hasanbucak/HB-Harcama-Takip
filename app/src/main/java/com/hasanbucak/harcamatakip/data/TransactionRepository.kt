package com.hasanbucak.harcamatakip.data

import androidx.room.withTransaction
import com.hasanbucak.harcamatakip.data.local.AppDatabase
import com.hasanbucak.harcamatakip.data.local.BankRuleEntity
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.MerchantRuleEntity
import com.hasanbucak.harcamatakip.data.local.PendingReviewEntity
import com.hasanbucak.harcamatakip.data.local.ProcessedMessageEntity
import com.hasanbucak.harcamatakip.data.local.RawNotificationLogEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.data.local.UnknownCardPolicy
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.CategoryClassifier
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParseOutcome
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransaction
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import com.hasanbucak.harcamatakip.domain.parser.YkbTransactionParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed class IngestResult {
    data class Saved(val transaction: ParsedTransaction, val cardAlias: String) : IngestResult()
    data class MergedDuplicate(val detail: String) : IngestResult()
    data class AlreadyProcessed(val detail: String) : IngestResult()
    data class PendingReview(val reason: String) : IngestResult()
    data class Ignored(val reason: String) : IngestResult()
}

class TransactionRepository(private val db: AppDatabase) {

    private val ingestMutex = Mutex()

    val transactionsFlow: Flow<List<TransactionEntity>> = db.transactionDao().observeAllTransactions()
    val cardsFlow: Flow<List<CardEntity>> = db.cardDao().observeActiveCards()
    val cardGroupsFlow: Flow<List<CardGroupEntity>> = db.cardGroupDao().observeAllGroups()
    val bankRulesFlow: Flow<List<BankRuleEntity>> = db.bankRuleDao().observeAllRules()
    val pendingReviewsFlow: Flow<List<PendingReviewEntity>> = db.pendingReviewDao().observeUnresolved()
    val recentLogsFlow: Flow<List<RawNotificationLogEntity>> = db.rawLogDao().observeRecentLogs()

    companion object {
        private const val PROCESSED_TTL_MS = 60L * 24 * 60 * 60 * 1000L
        private const val RAW_TEXT_TTL_MS = 30L * 24 * 60 * 60 * 1000L

        private val CARD_COLOR_PALETTE: List<Long> = listOf(
            0xFF1E3A8A,
            0xFFB45309,
            0xFF0F766E,
            0xFF7C3AED,
            0xFF15803D,
            0xFFBE123C,
            0xFF0284C7
        )
    }

    suspend fun initializeDefaultsAndMaintenance() {
        db.withTransaction {
            db.cardGroupDao().insertIfAbsent(CardGroupEntity.DEFAULT_GROUPS)
            val now = System.currentTimeMillis()
            db.processedMessageDao().pruneOlderThan(now - PROCESSED_TTL_MS)
            db.pendingReviewDao().pruneResolvedOlderThan(now - RAW_TEXT_TTL_MS)
            db.transactionDao().scrubOldRawTexts(now - RAW_TEXT_TTL_MS)
            db.rawLogDao().pruneOldLogs()
        }
    }

    suspend fun ingestIncomingMessage(
        sourceChannel: String,
        sourceTag: String,
        sourcePackage: String = sourceTag,
        fingerprint: String? = null,
        bankHint: BankProvider,
        title: String?,
        body: String?,
        timestamp: Long = System.currentTimeMillis(),
        isDemo: Boolean = false
    ): IngestResult = ingestMutex.withLock {
        val computedFingerprint = fingerprint ?: MessageFingerprint.forManualOrDemo(
            tag = sourcePackage,
            timestamp = timestamp,
            body = "${title.orEmpty()}\n${body.orEmpty()}"
        )

        val outcome = YkbTransactionParser.parse(
            title = title,
            body = body,
            bankHint = bankHint
        )

        db.withTransaction {
            // 1. Fingerprint check prevents re-processing on listener reconnect or notification updates
            if (!isDemo) {
                val alreadySeen = db.processedMessageDao().find(computedFingerprint)
                if (alreadySeen != null) {
                    return@withTransaction IngestResult.AlreadyProcessed(
                        "Bu bildirim daha önce işlendi (${alreadySeen.outcome})"
                    )
                }
            }

            when (outcome) {
                is ParseOutcome.Ignored -> {
                    // Zero-retention for OTP/security prompts: never write secret text to SQLite
                    val safeTitle = if (outcome.isSensitiveOtp) "Güvenlik Bildirimi" else YkbTransactionParser.sanitizeForLog(title?.trim().orEmpty())
                    val safeBody = if (outcome.isSensitiveOtp) "[OTP / Güvenlik kodu — içerik kaydedilmedi]" else YkbTransactionParser.sanitizeForLog(body?.trim().orEmpty())

                    if (!isDemo) {
                        db.processedMessageDao().upsert(
                            ProcessedMessageEntity(
                                fingerprint = computedFingerprint,
                                sourcePackage = sourcePackage,
                                outcome = "IGNORED",
                                txUuid = null,
                                firstSeenAt = timestamp
                            )
                        )
                    }
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
                    IngestResult.Ignored(outcome.reason)
                }

                is ParseOutcome.Unparsed -> {
                    val safeTitle = YkbTransactionParser.sanitizeForLog(title?.trim().orEmpty())
                    val safeBody = YkbTransactionParser.sanitizeForLog(body?.trim().orEmpty())

                    if (!isDemo) {
                        db.processedMessageDao().upsert(
                            ProcessedMessageEntity(
                                fingerprint = computedFingerprint,
                                sourcePackage = sourcePackage,
                                outcome = "PENDING",
                                txUuid = null,
                                firstSeenAt = timestamp
                            )
                        )
                        db.pendingReviewDao().insert(
                            PendingReviewEntity(
                                fingerprint = computedFingerprint,
                                sourceChannel = sourceChannel,
                                sourcePackage = sourcePackage,
                                bankCode = bankHint.code,
                                cardLast4 = "GENEL",
                                detectedCardType = DetectedCardType.CREDIT.name,
                                merchant = "${bankHint.displayName} İşlemi",
                                amount = 0.0,
                                currency = "TL",
                                transactionType = ParsedTransactionType.EXPENSE.name,
                                installmentCount = 1,
                                txClockMinute = null,
                                reasonType = "UNPARSED",
                                reasonDetail = outcome.reason,
                                title = safeTitle,
                                body = safeBody,
                                timestamp = timestamp
                            )
                        )
                    }
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
                    IngestResult.PendingReview(outcome.reason)
                }

                is ParseOutcome.Success -> {
                    val safeTitle = YkbTransactionParser.sanitizeForLog(title?.trim().orEmpty())
                    val safeBody = YkbTransactionParser.sanitizeForLog(body?.trim().orEmpty())
                    val parsed = outcome.transaction

                    // Demo simulations never pollute real cards or real totals
                    if (isDemo) {
                        val demoKey = CardEntity.buildKey(parsed.bank.code, parsed.cardLast4)
                        val existingCard = db.cardDao().getCard(demoKey)
                        val txUuid = UUID.randomUUID().toString()
                        val category = CategoryClassifier.classify(parsed.merchant)
                        db.transactionDao().insert(
                            TransactionEntity(
                                uuid = txUuid,
                                cardKey = demoKey,
                                bankCode = parsed.bank.code,
                                bankName = parsed.bank.displayName,
                                cardLast4 = parsed.cardLast4,
                                cardType = existingCard?.cardType ?: parsed.detectedCardType.name,
                                groupId = existingCard?.defaultGroupId,
                                groupManual = false,
                                merchant = parsed.merchant,
                                normalizedMerchant = CategoryClassifier.normalizeMerchantKey(parsed.merchant),
                                amount = parsed.amount,
                                currency = parsed.currency,
                                category = category,
                                categoryManual = false,
                                transactionType = parsed.transactionType.name,
                                installmentCount = parsed.installmentCount,
                                source = "DEMO",
                                seenPackages = sourcePackage,
                                txClockMinute = parsed.txClockMinute,
                                isDemo = true,
                                timestamp = timestamp,
                                rawText = safeBody,
                                note = "Demo test kaydı"
                            )
                        )
                        db.rawLogDao().insert(
                            RawNotificationLogEntity(
                                sourceChannel = sourceChannel,
                                title = safeTitle,
                                body = safeBody,
                                status = "SAVED",
                                detail = "[DEMO] ${parsed.bank.displayName} (${parsed.cardLast4}) -> ${parsed.merchant} • ${parsed.amount} ${parsed.currency}",
                                timestamp = timestamp
                            )
                        )
                        return@withTransaction IngestResult.Saved(parsed, existingCard?.alias ?: "${parsed.bank.displayName} •••• ${parsed.cardLast4}")
                    }

                    // 2. Check cross-package deduplication & card/merchant enrichment BEFORE card creation
                    val windowHalf = DeduplicationMatcher.CLOCK_MATCH_EXTENDED_WINDOW_MS
                    val candidates = db.transactionDao().findCandidatesInWindow(
                        bankCode = parsed.bank.code,
                        transactionType = parsed.transactionType.name,
                        minTimestamp = timestamp - windowHalf,
                        maxTimestamp = timestamp + windowHalf
                    )
                    val mergeTarget = DeduplicationMatcher.findMergeTarget(
                        incoming = parsed,
                        sourcePackage = sourcePackage,
                        timestamp = timestamp,
                        candidates = candidates
                    )

                    if (mergeTarget != null) {
                        val enriched = enrichMergedTransaction(mergeTarget, parsed, sourcePackage)
                        db.transactionDao().update(enriched)
                        db.processedMessageDao().upsert(
                            ProcessedMessageEntity(
                                fingerprint = computedFingerprint,
                                sourcePackage = sourcePackage,
                                outcome = "MERGED",
                                txUuid = enriched.uuid,
                                firstSeenAt = timestamp
                            )
                        )
                        db.rawLogDao().insert(
                            RawNotificationLogEntity(
                                sourceChannel = sourceChannel,
                                title = safeTitle,
                                body = safeBody,
                                status = "DUPLICATE",
                                detail = "Farklı kanaldan gelen aynı işlem birleştirildi (${parsed.bank.displayName} • ${enriched.cardLast4} • ${parsed.amount} ${parsed.currency})",
                                timestamp = timestamp
                            )
                        )
                        db.rawLogDao().pruneOldLogs()
                        return@withTransaction IngestResult.MergedDuplicate(
                            "${parsed.bank.displayName} (${enriched.cardLast4}) işlemi mevcut kayıtla birleştirildi"
                        )
                    }

                    // 3. Apply Card & UnknownCardPolicy rules
                    val cardKey = CardEntity.buildKey(parsed.bank.code, parsed.cardLast4)
                    val existingCard = if (parsed.cardLast4 != "GENEL") {
                        db.cardDao().getCard(cardKey)
                    } else {
                        null
                    }

                    if (existingCard != null && existingCard.isBlocked) {
                        val reason = "Engellenmiş kart (${parsed.bank.displayName} •••• ${parsed.cardLast4}) atlandı"
                        db.processedMessageDao().upsert(
                            ProcessedMessageEntity(
                                fingerprint = computedFingerprint,
                                sourcePackage = sourcePackage,
                                outcome = "IGNORED",
                                txUuid = null,
                                firstSeenAt = timestamp
                            )
                        )
                        db.rawLogDao().insert(
                            RawNotificationLogEntity(
                                sourceChannel = sourceChannel,
                                title = safeTitle,
                                body = safeBody,
                                status = "IGNORED",
                                detail = reason,
                                timestamp = timestamp
                            )
                        )
                        db.rawLogDao().pruneOldLogs()
                        return@withTransaction IngestResult.Ignored(reason)
                    }

                    val rule = db.bankRuleDao().getRule(parsed.bank.code)
                    val policy = UnknownCardPolicy.fromCode(rule?.unknownCardPolicy)

                    val resolvedCard: CardEntity = when {
                        existingCard != null -> existingCard

                        parsed.cardLast4 == "GENEL" -> {
                            if (policy == UnknownCardPolicy.AUTO_ADD) {
                                ensureCardExists(parsed.bank, "GENEL", parsed.detectedCardType)
                            } else {
                                val reason = "${parsed.bank.displayName} bildiriminde kart son 4 hanesi yok — kart seçimi bekliyor"
                                enqueuePendingCardReview(
                                    fingerprint = computedFingerprint,
                                    sourceChannel = sourceChannel,
                                    sourcePackage = sourcePackage,
                                    parsed = parsed,
                                    reasonDetail = reason,
                                    safeTitle = safeTitle,
                                    safeBody = safeBody,
                                    timestamp = timestamp
                                )
                                return@withTransaction IngestResult.PendingReview(reason)
                            }
                        }

                        policy == UnknownCardPolicy.AUTO_ADD -> {
                            ensureCardExists(parsed.bank, parsed.cardLast4, parsed.detectedCardType)
                        }

                        policy == UnknownCardPolicy.IGNORE -> {
                            val reason = "${parsed.bank.displayName} için yalnızca kayıtlı kartlar takip ediliyor (gelen: ${parsed.cardLast4})"
                            db.processedMessageDao().upsert(
                                ProcessedMessageEntity(
                                    fingerprint = computedFingerprint,
                                    sourcePackage = sourcePackage,
                                    outcome = "IGNORED",
                                    txUuid = null,
                                    firstSeenAt = timestamp
                                )
                            )
                            db.rawLogDao().insert(
                                RawNotificationLogEntity(
                                    sourceChannel = sourceChannel,
                                    title = safeTitle,
                                    body = safeBody,
                                    status = "IGNORED",
                                    detail = reason,
                                    timestamp = timestamp
                                )
                            )
                            db.rawLogDao().pruneOldLogs()
                            return@withTransaction IngestResult.Ignored(reason)
                        }

                        else -> {
                            val reason = "${parsed.bank.displayName} •••• ${parsed.cardLast4} henüz kart listende yok — onay bekliyor"
                            enqueuePendingCardReview(
                                fingerprint = computedFingerprint,
                                sourceChannel = sourceChannel,
                                sourcePackage = sourcePackage,
                                parsed = parsed,
                                reasonDetail = reason,
                                safeTitle = safeTitle,
                                safeBody = safeBody,
                                timestamp = timestamp
                            )
                            return@withTransaction IngestResult.PendingReview(reason)
                        }
                    }

                    // 4. Persist verified transaction with card's defaultGroupId snapshot
                    val txUuid = UUID.randomUUID().toString()
                    val normalizedMerchant = CategoryClassifier.normalizeMerchantKey(parsed.merchant)
                    val savedRuleCategory = if (!parsed.isGenericMerchant && normalizedMerchant.isNotEmpty()) {
                        db.merchantRuleDao().findCategory(normalizedMerchant)
                    } else {
                        null
                    }
                    val finalCategory = savedRuleCategory ?: CategoryClassifier.classify(parsed.merchant)
                    val typeLabel = DetectedCardType.fromCode(resolvedCard.cardType).labelTr

                    db.transactionDao().insert(
                        TransactionEntity(
                            uuid = txUuid,
                            cardKey = resolvedCard.cardKey,
                            bankCode = resolvedCard.bankCode,
                            bankName = resolvedCard.bankName,
                            cardLast4 = resolvedCard.last4,
                            cardType = resolvedCard.cardType,
                            groupId = resolvedCard.defaultGroupId,
                            groupManual = false,
                            merchant = parsed.merchant,
                            normalizedMerchant = normalizedMerchant,
                            amount = parsed.amount,
                            currency = parsed.currency,
                            category = finalCategory,
                            categoryManual = false,
                            transactionType = parsed.transactionType.name,
                            installmentCount = parsed.installmentCount,
                            source = sourceTag,
                            seenPackages = sourcePackage,
                            txClockMinute = parsed.txClockMinute,
                            isDemo = false,
                            timestamp = timestamp,
                            rawText = if (safeTitle.isNotEmpty()) "$safeTitle\n$safeBody" else safeBody
                        )
                    )

                    db.processedMessageDao().upsert(
                        ProcessedMessageEntity(
                            fingerprint = computedFingerprint,
                            sourcePackage = sourcePackage,
                            outcome = "SAVED",
                            txUuid = txUuid,
                            firstSeenAt = timestamp
                        )
                    )

                    db.rawLogDao().insert(
                        RawNotificationLogEntity(
                            sourceChannel = sourceChannel,
                            title = safeTitle,
                            body = safeBody,
                            status = "SAVED",
                            detail = "${resolvedCard.bankName} (${resolvedCard.last4} • $typeLabel) -> ${parsed.merchant} • ${parsed.amount} ${parsed.currency}",
                            timestamp = timestamp
                        )
                    )
                    db.rawLogDao().pruneOldLogs()
                    IngestResult.Saved(parsed, resolvedCard.alias)
                }
            }
        }
    }

    private suspend fun enqueuePendingCardReview(
        fingerprint: String,
        sourceChannel: String,
        sourcePackage: String,
        parsed: ParsedTransaction,
        reasonDetail: String,
        safeTitle: String,
        safeBody: String,
        timestamp: Long
    ) {
        db.processedMessageDao().upsert(
            ProcessedMessageEntity(
                fingerprint = fingerprint,
                sourcePackage = sourcePackage,
                outcome = "PENDING",
                txUuid = null,
                firstSeenAt = timestamp
            )
        )
        db.pendingReviewDao().insert(
            PendingReviewEntity(
                fingerprint = fingerprint,
                sourceChannel = sourceChannel,
                sourcePackage = sourcePackage,
                bankCode = parsed.bank.code,
                cardLast4 = parsed.cardLast4,
                detectedCardType = parsed.detectedCardType.name,
                merchant = parsed.merchant,
                amount = parsed.amount,
                currency = parsed.currency,
                transactionType = parsed.transactionType.name,
                installmentCount = parsed.installmentCount,
                txClockMinute = parsed.txClockMinute,
                reasonType = "PENDING_CARD",
                reasonDetail = reasonDetail,
                title = safeTitle,
                body = safeBody,
                timestamp = timestamp
            )
        )
        db.rawLogDao().insert(
            RawNotificationLogEntity(
                sourceChannel = sourceChannel,
                title = safeTitle,
                body = safeBody,
                status = "PENDING",
                detail = reasonDetail,
                timestamp = timestamp
            )
        )
        db.rawLogDao().pruneOldLogs()
    }

    private suspend fun enrichMergedTransaction(
        existing: TransactionEntity,
        incoming: ParsedTransaction,
        sourcePackage: String
    ): TransactionEntity {
        val updatedSeen = (existing.seenPackages.split(',').filter { it.isNotBlank() } + sourcePackage)
            .distinct()
            .joinToString(",")

        var updatedCardKey = existing.cardKey
        var updatedLast4 = existing.cardLast4
        var updatedCardType = existing.cardType
        var updatedGroupId = existing.groupId

        // If earlier record was GENEL and incoming message carries specific last4, enrich card + group snapshot!
        if (existing.cardLast4 == "GENEL" && incoming.cardLast4 != "GENEL") {
            val specificKey = CardEntity.buildKey(incoming.bank.code, incoming.cardLast4)
            val specificCard = db.cardDao().getCard(specificKey)
                ?: if (UnknownCardPolicy.fromCode(db.bankRuleDao().getRule(incoming.bank.code)?.unknownCardPolicy) == UnknownCardPolicy.AUTO_ADD) {
                    ensureCardExists(incoming.bank, incoming.cardLast4, incoming.detectedCardType)
                } else {
                    null
                }
            if (specificCard != null && !specificCard.isBlocked) {
                updatedCardKey = specificCard.cardKey
                updatedLast4 = specificCard.last4
                updatedCardType = specificCard.cardType
                if (!existing.groupManual) {
                    updatedGroupId = specificCard.defaultGroupId
                }
            }
        }

        val existingIsGeneric = existing.merchant.endsWith(" İşlemi")
        val updatedMerchant = if (existingIsGeneric && !incoming.isGenericMerchant) {
            incoming.merchant
        } else {
            existing.merchant
        }
        val updatedNormalized = CategoryClassifier.normalizeMerchantKey(updatedMerchant)

        // Never overwrite a category that the user manually edited
        val updatedCategory = if (!existing.categoryManual && existingIsGeneric && !incoming.isGenericMerchant) {
            db.merchantRuleDao().findCategory(updatedNormalized) ?: CategoryClassifier.classify(updatedMerchant)
        } else {
            existing.category
        }

        return existing.copy(
            cardKey = updatedCardKey,
            cardLast4 = updatedLast4,
            cardType = updatedCardType,
            groupId = updatedGroupId,
            merchant = updatedMerchant,
            normalizedMerchant = updatedNormalized,
            category = updatedCategory,
            seenPackages = updatedSeen,
            txClockMinute = existing.txClockMinute ?: incoming.txClockMinute
        )
    }

    private suspend fun ensureCardExists(
        bank: BankProvider,
        last4: String,
        detectedType: DetectedCardType,
        defaultGroupId: String? = null
    ): CardEntity {
        val cardKey = CardEntity.buildKey(bank.code, last4)
        val existing = db.cardDao().getCard(cardKey)
        if (existing != null && !existing.isBlocked) return existing

        val count = db.cardDao().countActiveCards()
        val color = if (bank == BankProvider.YAPI_KREDI) {
            CARD_COLOR_PALETTE[count % CARD_COLOR_PALETTE.size]
        } else {
            bank.defaultColorHex
        }

        val defaultAlias = when {
            last4 == "NAKIT" -> "Nakit"
            last4 == "GENEL" -> "${bank.displayName} Kartım"
            detectedType == DetectedCardType.VIRTUAL -> "${bank.displayName} Sanal •••• $last4"
            detectedType == DetectedCardType.DEBIT -> "${bank.displayName} Banka •••• $last4"
            else -> "${bank.displayName} •••• $last4"
        }

        val created = CardEntity(
            cardKey = cardKey,
            bankCode = bank.code,
            bankName = bank.displayName,
            last4 = last4,
            alias = defaultAlias,
            cardType = detectedType.name,
            defaultGroupId = defaultGroupId,
            isBlocked = false,
            statementDay = 1,
            colorHex = color
        )
        db.cardDao().upsert(created)
        return db.cardDao().getCard(cardKey) ?: created
    }

    suspend fun approvePendingReview(
        pendingId: Long,
        targetBank: BankProvider,
        targetLast4: String,
        targetCardType: DetectedCardType,
        targetGroupId: String?,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        isRefund: Boolean,
        installmentCount: Int
    ) {
        db.withTransaction {
            val item = db.pendingReviewDao().getById(pendingId) ?: return@withTransaction
            val cleanLast4 = targetLast4.trim().ifEmpty { "GENEL" }
            val card = ensureCardExists(targetBank, cleanLast4, targetCardType, targetGroupId)
            val cleanMerchant = merchant.trim().ifEmpty { "${targetBank.displayName} İşlemi" }
            val norm = CategoryClassifier.normalizeMerchantKey(cleanMerchant)
            val txUuid = UUID.randomUUID().toString()

            db.transactionDao().insert(
                TransactionEntity(
                    uuid = txUuid,
                    cardKey = card.cardKey,
                    bankCode = card.bankCode,
                    bankName = card.bankName,
                    cardLast4 = card.last4,
                    cardType = card.cardType,
                    groupId = targetGroupId ?: card.defaultGroupId,
                    groupManual = targetGroupId != null && targetGroupId != card.defaultGroupId,
                    merchant = cleanMerchant,
                    normalizedMerchant = norm,
                    amount = amount,
                    currency = currency,
                    category = category,
                    categoryManual = true,
                    transactionType = if (isRefund) ParsedTransactionType.REFUND.name else ParsedTransactionType.EXPENSE.name,
                    installmentCount = installmentCount.coerceIn(1, 36),
                    source = item.sourcePackage,
                    seenPackages = item.sourcePackage,
                    txClockMinute = item.txClockMinute,
                    isDemo = false,
                    timestamp = item.timestamp,
                    rawText = if (item.title.isNotEmpty()) "${item.title}\n${item.body}" else item.body
                )
            )
            db.pendingReviewDao().markResolved(pendingId)
            db.processedMessageDao().upsert(
                ProcessedMessageEntity(
                    fingerprint = item.fingerprint,
                    sourcePackage = item.sourcePackage,
                    outcome = "SAVED",
                    txUuid = txUuid,
                    firstSeenAt = item.timestamp
                )
            )
        }
    }

    suspend fun dismissOrBlockPendingReview(pendingId: Long, blockCard: Boolean) {
        db.withTransaction {
            val item = db.pendingReviewDao().getById(pendingId) ?: return@withTransaction
            if (blockCard && item.cardLast4 != "GENEL") {
                val bank = BankProvider.fromCode(item.bankCode)
                val key = CardEntity.buildKey(bank.code, item.cardLast4)
                val existing = db.cardDao().getCard(key)
                db.cardDao().upsert(
                    (existing ?: CardEntity(
                        cardKey = key,
                        bankCode = bank.code,
                        bankName = bank.displayName,
                        last4 = item.cardLast4,
                        alias = "${bank.displayName} •••• ${item.cardLast4}",
                        cardType = item.detectedCardType
                    )).copy(isBlocked = true)
                )
            }
            db.pendingReviewDao().markResolved(pendingId)
            db.processedMessageDao().upsert(
                ProcessedMessageEntity(
                    fingerprint = item.fingerprint,
                    sourcePackage = item.sourcePackage,
                    outcome = "IGNORED",
                    txUuid = null,
                    firstSeenAt = item.timestamp
                )
            )
        }
    }

    suspend fun addManualTransaction(
        bank: BankProvider,
        cardLast4: String,
        cardType: DetectedCardType,
        groupId: String?,
        merchant: String,
        amount: Double,
        currency: String,
        category: String,
        transactionType: ParsedTransactionType,
        installmentCount: Int,
        timestamp: Long = System.currentTimeMillis(),
        note: String,
        rawText: String = "Manuel kayıt",
        sourceLogId: Long? = null
    ) {
        db.withTransaction {
            val cleanLast4 = cardLast4.trim().ifEmpty {
                if (cardType == DetectedCardType.CASH) "NAKIT" else "GENEL"
            }
            val effectiveBank = if (cardType == DetectedCardType.CASH) BankProvider.OTHER else bank
            val card = ensureCardExists(effectiveBank, cleanLast4, cardType, groupId)

            val cleanMerchant = merchant.trim().ifEmpty { "Genel Harcama" }
            val normalized = CategoryClassifier.normalizeMerchantKey(cleanMerchant)
            val effectiveGroupId = groupId ?: card.defaultGroupId

            db.transactionDao().insert(
                TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    cardKey = card.cardKey,
                    bankCode = card.bankCode,
                    bankName = if (cardType == DetectedCardType.CASH) "Nakit" else card.bankName,
                    cardLast4 = card.last4,
                    cardType = cardType.name,
                    groupId = effectiveGroupId,
                    groupManual = groupId != null && groupId != card.defaultGroupId,
                    merchant = cleanMerchant,
                    normalizedMerchant = normalized,
                    amount = amount,
                    currency = currency,
                    category = category,
                    categoryManual = true,
                    transactionType = transactionType.name,
                    installmentCount = installmentCount.coerceIn(1, 36),
                    source = "MANUAL",
                    seenPackages = "MANUAL",
                    txClockMinute = null,
                    isDemo = false,
                    timestamp = timestamp,
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
    }

    suspend fun updateTransaction(
        entity: TransactionEntity,
        newCategory: String,
        newGroupId: String?,
        newNote: String,
        rememberCategoryForMerchant: Boolean
    ) {
        db.withTransaction {
            val card = db.cardDao().getCard(entity.cardKey)
            val isManualGroupChange = newGroupId != entity.groupId || entity.groupManual
            val isManualCatChange = newCategory != entity.category || entity.categoryManual

            val updated = entity.copy(
                category = newCategory,
                categoryManual = isManualCatChange,
                groupId = newGroupId,
                groupManual = if (newGroupId == card?.defaultGroupId && !entity.groupManual) false else isManualGroupChange,
                note = newNote.trim()
            )
            db.transactionDao().update(updated)

            val isGenericMerchant = entity.merchant.endsWith(" İşlemi")
            if (rememberCategoryForMerchant && !isGenericMerchant && entity.normalizedMerchant.isNotEmpty()) {
                db.merchantRuleDao().upsert(
                    MerchantRuleEntity(
                        normalizedMerchant = entity.normalizedMerchant,
                        category = newCategory
                    )
                )
                db.transactionDao().updateCategoryForMerchant(entity.normalizedMerchant, newCategory)
            }
        }
    }

    suspend fun deleteTransaction(entity: TransactionEntity) {
        db.transactionDao().delete(entity)
    }

    suspend fun clearDemoTransactions() {
        db.transactionDao().deleteDemoTransactions()
    }

    suspend fun saveCard(
        card: CardEntity,
        applyGroupToPastTransactions: Boolean = false
    ) {
        db.withTransaction {
            db.cardDao().upsert(card.copy(isBlocked = false))
            db.transactionDao().updateCardMetadataForTransactions(
                cardKey = card.cardKey,
                cardType = card.cardType,
                bankName = card.bankName
            )
            if (applyGroupToPastTransactions) {
                db.transactionDao().updateNonManualGroupForCard(
                    cardKey = card.cardKey,
                    newGroupId = card.defaultGroupId
                )
            }
        }
    }

    suspend fun removeCard(cardKey: String, deleteTransactionsToo: Boolean) {
        db.withTransaction {
            val existing = db.cardDao().getCard(cardKey)
            if (deleteTransactionsToo) {
                db.transactionDao().deleteByCardKey(cardKey)
            }
            if (existing != null) {
                // Keep blocked tombstone so AUTO_ADD does not silently recreate deleted card
                db.cardDao().upsert(existing.copy(isBlocked = true))
            }
        }
    }

    suspend fun saveCardGroup(group: CardGroupEntity) {
        db.cardGroupDao().upsert(group)
    }

    suspend fun deleteCardGroup(groupId: String, moveTransactionsToGroupId: String? = null) {
        db.withTransaction {
            db.cardDao().reassignGroupForCards(groupId, moveTransactionsToGroupId)
            db.transactionDao().moveGroupTransactionsOnGroupDelete(groupId, moveTransactionsToGroupId)
            db.cardGroupDao().deleteById(groupId)
        }
    }

    suspend fun setBankUnknownCardPolicy(bankCode: String, policy: UnknownCardPolicy) {
        db.bankRuleDao().upsert(BankRuleEntity(bankCode = bankCode, unknownCardPolicy = policy.name))
    }

    suspend fun clearRawLogs() {
        db.rawLogDao().clearAll()
    }

    suspend fun buildCsvExport(transactionsToExport: List<TransactionEntity>? = null): String {
        val list = transactionsToExport ?: db.transactionDao().getNonDemoTransactionsSnapshot()
        val cards = db.cardDao().getAllCardsSnapshot().associateBy { it.cardKey }
        val groups = db.cardGroupDao().getAllGroupsSnapshot().associateBy { it.groupId }
        return BackupSerializer.exportCsv(
            transactions = list,
            cardsByKey = cards,
            groupsById = groups
        )
    }

    suspend fun buildJsonBackup(includeRawText: Boolean = false): String {
        return db.withTransaction {
            BackupSerializer.exportJson(
                groups = db.cardGroupDao().getAllGroupsSnapshot(),
                bankRules = db.bankRuleDao().getAllRulesSnapshot(),
                cards = db.cardDao().getAllCardsSnapshot(),
                merchantRules = db.merchantRuleDao().getAllRulesSnapshot(),
                transactions = db.transactionDao().getNonDemoTransactionsSnapshot(),
                includeRawText = includeRawText
            )
        }
    }

    suspend fun restoreFromJsonBackup(jsonText: String): Pair<Int, Int> {
        val payload = BackupSerializer.importJson(jsonText)
        var importedTxCount = 0
        var skippedTxCount = 0

        db.withTransaction {
            payload.groups.forEach { db.cardGroupDao().upsert(it) }
            payload.bankRules.forEach { db.bankRuleDao().upsert(it) }
            payload.cards.forEach { db.cardDao().upsert(it) }
            payload.merchantRules.forEach { db.merchantRuleDao().upsert(it) }

            for (tx in payload.transactions) {
                val existing = db.transactionDao().findByUuid(tx.uuid)
                if (existing == null) {
                    db.transactionDao().insertIgnore(tx.copy(id = 0))
                    importedTxCount++
                } else {
                    skippedTxCount++
                }
            }
        }
        return importedTxCount to skippedTxCount
    }
}
