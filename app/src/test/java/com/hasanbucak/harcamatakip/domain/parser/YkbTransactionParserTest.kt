package com.hasanbucak.harcamatakip.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YkbTransactionParserTest {

    @Test
    fun parsesStandardWorldcardPosNotificationAndIgnoresTrailingLimit() {
        val outcome = YkbTransactionParser.parse(
            title = "Harcama Bilgisi",
            body = "4543****4821 nolu kartınızla 09.11.2026 14:32 tarihinde MİGROS TİCARET A.Ş. işyerinden 645,50 TL tutarında harcama yapılmıştır. Güncel kullanılabilir limitiniz 84.354,50 TL'dir."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("4821", tx.cardLast4)
        assertEquals(DetectedCardType.CREDIT, tx.detectedCardType)
        assertEquals("MİGROS TİCARET A.Ş", tx.merchant)
        assertEquals(645.50, tx.amount, 0.001)
        assertEquals("TL", tx.currency)
        assertEquals(ParsedTransactionType.EXPENSE, tx.transactionType)
        assertEquals(1, tx.installmentCount)
        assertEquals(CategoryClassifier.MARKET, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun parsesAsciiSmsFormatWithThousandsSeparator() {
        val outcome = YkbTransactionParser.parse(
            title = "YAPIKREDI",
            body = "4921********7712 nolu kartinizla 09/11/2026 tarihinde SHELL PETROL A.S. isyerinden 1.850,00 TL tutarinda harcama yapilmistir."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("7712", tx.cardLast4)
        assertEquals("SHELL PETROL A.S", tx.merchant)
        assertEquals(1850.00, tx.amount, 0.001)
        assertEquals("TL", tx.currency)
        assertEquals(CategoryClassifier.FUEL_TRANSPORT, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun parsesDigitalVirtualCardWithInstallments() {
        val outcome = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile TRENDYOL.COM işyerinden 3 taksitli 2.490,00 TL alışveriş yapılmıştır."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("9104", tx.cardLast4)
        assertEquals(DetectedCardType.VIRTUAL, tx.detectedCardType)
        assertEquals("TRENDYOL.COM", tx.merchant)
        assertEquals(2490.00, tx.amount, 0.001)
        assertEquals(3, tx.installmentCount)
        assertEquals(CategoryClassifier.ONLINE_SHOPPING, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun parsesTlCardDebitNotification() {
        val outcome = YkbTransactionParser.parse(
            title = "Yapı Kredi Mobil",
            body = "TLcard'ınızla (son 4 hanesi 3310) BİM BİRLEŞİK MAĞAZALAR işyerinden 312,25 TL tutarında işlem gerçekleştirilmiştir."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("3310", tx.cardLast4)
        assertEquals(DetectedCardType.DEBIT, tx.detectedCardType)
        assertEquals(312.25, tx.amount, 0.001)
        assertEquals(CategoryClassifier.MARKET, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun parsesForeignCurrencyUsdSpend() {
        val outcome = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile STEAM GAMES işyerinden 19.99 USD harcama yapılmıştır."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("9104", tx.cardLast4)
        assertEquals(19.99, tx.amount, 0.001)
        assertEquals("USD", tx.currency)
        assertEquals(CategoryClassifier.ENTERTAINMENT, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun parsesRefundTransaction() {
        val outcome = YkbTransactionParser.parse(
            title = "Harcama İadesi",
            body = "4543****4821 nolu kartınıza TRENDYOL.COM işyerinden 390,00 TL iade işlemi yapılmıştır."
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("4821", tx.cardLast4)
        assertEquals(390.00, tx.amount, 0.001)
        assertEquals(ParsedTransactionType.REFUND, tx.transactionType)
    }

    @Test
    fun ignoresOtpAndMasksSecretCodeInSanitizer() {
        val rawOtp = "3D Secure şifreniz: 583921. TRENDYOL işyerinden 950,00 TL harcama için şifreyi kimseyle paylaşmayınız."
        val outcome = YkbTransactionParser.parse("YAPIKREDI", rawOtp)
        assertTrue(outcome is ParseOutcome.Ignored)

        val sanitized = YkbTransactionParser.sanitizeForLog(rawOtp)
        assertFalse(sanitized.contains("583921"))
        assertTrue(sanitized.contains("******"))
    }

    @Test
    fun ignoresCampaignNotification() {
        val outcome = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "Market alışverişlerinizde 500 TL'ye varan Worldpuan kampanyasına katılmak için hemen tıklayın!"
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }
}
