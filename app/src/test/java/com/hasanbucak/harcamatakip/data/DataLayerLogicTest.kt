package com.hasanbucak.harcamatakip.data

import com.hasanbucak.harcamatakip.data.local.BankRuleEntity
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.MerchantRuleEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.data.local.UnknownCardPolicy
import com.hasanbucak.harcamatakip.domain.parser.BankProvider
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransaction
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataLayerLogicTest {

    private fun sampleTxEntity(
        id: Long = 1L,
        uuid: String = "tx-1",
        bankCode: String = "YAPI_KREDI",
        cardLast4: String = "4821",
        merchant: String = "MİGROS TİCARET A.Ş",
        normalizedMerchant: String = "MIGROS TICARET A S",
        amount: Double = 645.50,
        currency: String = "TL",
        seenPackages: String = "com.ykb.android",
        txClockMinute: Int? = 14 * 60 + 32,
        timestamp: Long = 1_700_000_000_000L
    ) = TransactionEntity(
        id = id,
        uuid = uuid,
        cardKey = "$bankCode:$cardLast4",
        bankCode = bankCode,
        bankName = "Yapı Kredi",
        cardLast4 = cardLast4,
        cardType = DetectedCardType.CREDIT.name,
        groupId = "EV",
        groupManual = false,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        amount = amount,
        currency = currency,
        category = "Market",
        categoryManual = false,
        transactionType = ParsedTransactionType.EXPENSE.name,
        installmentCount = 1,
        source = "PUSH_YKB",
        seenPackages = seenPackages,
        txClockMinute = txClockMinute,
        isDemo = false,
        timestamp = timestamp,
        rawText = "raw",
        note = ""
    )

    @Test
    fun samePackageSecondNotificationDoesNotMergeSoRealSecondSpendIsKept() {
        val existing = sampleTxEntity(seenPackages = "com.ykb.android", txClockMinute = null)
        val incoming = ParsedTransaction(
            bank = BankProvider.YAPI_KREDI,
            cardLast4 = "4821",
            detectedCardType = DetectedCardType.CREDIT,
            merchant = "MİGROS TİCARET A.Ş",
            isGenericMerchant = false,
            amount = 645.50,
            currency = "TL",
            transactionType = ParsedTransactionType.EXPENSE,
            installmentCount = 1,
            txClockMinute = null
        )

        val match = DeduplicationMatcher.findMergeTarget(
            incoming = incoming,
            sourcePackage = "com.ykb.android",
            timestamp = existing.timestamp + 20_000L,
            candidates = listOf(existing)
        )
        assertNull(match)
    }

    @Test
    fun differentPackageMergesWithinWindowAndDelayedSmsMergesWhenClockMinuteMatches() {
        val existing = sampleTxEntity(seenPackages = "com.ykb.android", txClockMinute = 14 * 60 + 32)
        val incomingSms = ParsedTransaction(
            bank = BankProvider.YAPI_KREDI,
            cardLast4 = "4821",
            detectedCardType = DetectedCardType.CREDIT,
            merchant = "MİGROS TİCARET A.Ş",
            isGenericMerchant = false,
            amount = 645.50,
            currency = "TL",
            transactionType = ParsedTransactionType.EXPENSE,
            installmentCount = 1,
            txClockMinute = 14 * 60 + 32
        )

        // Even 35 minutes delayed SMS merges because txClockMinute (14:32) matches!
        val delayedMatch = DeduplicationMatcher.findMergeTarget(
            incoming = incomingSms,
            sourcePackage = "com.google.android.apps.messaging",
            timestamp = existing.timestamp + 35 * 60_000L,
            candidates = listOf(existing)
        )
        assertNotNull(delayedMatch)
        assertEquals("tx-1", delayedMatch?.uuid)

        // Different merchant on different channel within 1 minute MUST NOT merge!
        val differentMerchant = incomingSms.copy(
            merchant = "STARBUCKS",
            txClockMinute = null
        )
        val wrongMerge = DeduplicationMatcher.findMergeTarget(
            incoming = differentMerchant,
            sourcePackage = "com.google.android.apps.messaging",
            timestamp = existing.timestamp + 45_000L,
            candidates = listOf(existing)
        )
        assertNull(wrongMerge)
    }

    @Test
    fun dualCurrencyPushAndTlSmsMergeCleanly() {
        val existingTlSms = sampleTxEntity(
            amount = 712.00,
            currency = "TL",
            merchant = "STEAM",
            normalizedMerchant = "STEAM",
            seenPackages = "com.google.android.apps.messaging",
            txClockMinute = null
        )
        val incomingDualPush = ParsedTransaction(
            bank = BankProvider.YAPI_KREDI,
            cardLast4 = "4821",
            detectedCardType = DetectedCardType.VIRTUAL,
            merchant = "STEAM",
            isGenericMerchant = false,
            amount = 19.99,
            currency = "USD",
            secondaryAmountTl = 712.00,
            transactionType = ParsedTransactionType.EXPENSE,
            installmentCount = 1,
            txClockMinute = null
        )

        val match = DeduplicationMatcher.findMergeTarget(
            incoming = incomingDualPush,
            sourcePackage = "com.ykb.avm",
            timestamp = existingTlSms.timestamp + 60_000L,
            candidates = listOf(existingTlSms)
        )
        assertNotNull(match)
    }

    @Test
    fun backupJsonRoundTripAndCsvFormulaInjectionGuardWork() {
        val groups = listOf(CardGroupEntity("IS", "İş / Şirket", 0xFF0F766E, 2))
        val rules = listOf(BankRuleEntity("VAKIFBANK", UnknownCardPolicy.IGNORE.name))
        val cards = listOf(
            CardEntity(
                cardKey = "VAKIFBANK:6251",
                bankCode = "VAKIFBANK",
                bankName = "VakıfBank",
                last4 = "6251",
                alias = "Şirket Vakıf",
                cardType = DetectedCardType.CREDIT.name,
                defaultGroupId = "IS",
                isBlocked = false
            )
        )
        val merchantRules = listOf(MerchantRuleEntity("OPET", "Akaryakıt & Ulaşım"))
        val tx = sampleTxEntity(
            uuid = "uuid-abc-123",
            bankCode = "VAKIFBANK",
            cardLast4 = "6251",
            merchant = "=CMD|' /C calc'!A0",
            amount = 1234.50
        ).copy(cardKey = "VAKIFBANK:6251", bankName = "VakıfBank", groupId = "IS", groupManual = true)

        val json = BackupSerializer.exportJson(
            groups = groups,
            bankRules = rules,
            cards = cards,
            merchantRules = merchantRules,
            transactions = listOf(tx),
            includeRawText = false
        )
        assertFalse(json.contains("\"rawText\":\"raw\""))

        val restored = BackupSerializer.importJson(json)
        assertEquals(1, restored.groups.size)
        assertEquals("İş / Şirket", restored.groups[0].name)
        assertEquals(UnknownCardPolicy.IGNORE.name, restored.bankRules[0].unknownCardPolicy)
        assertEquals("IS", restored.cards[0].defaultGroupId)
        assertEquals("uuid-abc-123", restored.transactions[0].uuid)
        assertTrue(restored.transactions[0].groupManual)
        assertEquals("", restored.transactions[0].rawText)

        val csv = BackupSerializer.exportCsv(
            transactions = listOf(tx),
            cardsByKey = cards.associateBy { it.cardKey },
            groupsById = groups.associateBy { it.groupId }
        )
        assertTrue(csv.contains("1234,50"))
        assertTrue(csv.contains("İş / Şirket"))
        // Excel formula injection neutralized with leading apostrophe
        assertTrue(csv.contains("'=CMD|"))
    }

    @Test
    fun messageFingerprintsDifferAcrossStackedSmsMessages() {
        val f1 = MessageFingerprint.forMessagingStyle("com.google.android.apps.messaging", "YAPIKREDI", 1000L, "100 TL harcama")
        val f2 = MessageFingerprint.forMessagingStyle("com.google.android.apps.messaging", "YAPIKREDI", 2000L, "100 TL harcama")
        assertNotEquals(f1, f2)
    }
}
