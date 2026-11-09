package com.hasanbucak.harcamatakip.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YkbTransactionParserTest {

    @Test
    fun parsesStandardWorldcardPosNotificationAndIgnoresTrailingLimit() {
        val outcome = YkbTransactionParser.parse(
            title = "Harcama Bilgisi",
            body = "4543****4821 nolu kartınızla 09.11.2026 14:32 tarihinde MİGROS TİCARET A.Ş. işyerinden 645,50 TL tutarında harcama yapılmıştır. Güncel kullanılabilir limitiniz 84.354,50 TL'dir.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals(BankProvider.YAPI_KREDI, tx.bank)
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
    fun parsesVakifBank6251CardAndSetsCreditCardType() {
        val outcome = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "6251 ile biten Worldcard'ınızla 09.11.2026 15:10'da SHELL PETROL A.Ş. işyerinden 1.850,00 TL harcama yapılmıştır. Kullanılabilir limit: 45.000,00 TL",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals(BankProvider.VAKIFBANK, tx.bank)
        assertEquals("6251", tx.cardLast4)
        assertEquals(DetectedCardType.CREDIT, tx.detectedCardType)
        assertEquals("SHELL PETROL A.Ş", tx.merchant)
        assertEquals(1850.00, tx.amount, 0.001)
        assertEquals("TL", tx.currency)
    }

    @Test
    fun ignoresOtherVakifBankCardsNotEndingIn6251() {
        val outcome = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "9988 ile biten kartınızla A101 işyerinden 210,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(outcome is ParseOutcome.Ignored)
    }

    @Test
    fun parsesTomBankCreditCardSmsAndPush() {
        val outcome = YkbTransactionParser.parse(
            title = "TOMBank",
            body = "*1907 nolu Hadi Kredi Kartınızla TRENDYOL.COM işyerinden 3 taksitli 2.490,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.TOM_BANK
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals(BankProvider.TOM_BANK, tx.bank)
        assertEquals("1907", tx.cardLast4)
        assertEquals(DetectedCardType.CREDIT, tx.detectedCardType)
        assertEquals("TRENDYOL.COM", tx.merchant)
        assertEquals(2490.00, tx.amount, 0.001)
        assertEquals(3, tx.installmentCount)
    }

    @Test
    fun parsesTomBankApostropheFormatAndDebitHadiCard() {
        val outcome = YkbTransactionParser.parse(
            title = "TOMBank",
            body = "4412 ile biten Hadi Kart'ınızla GETİR'de 320,50 TL harcama yaptınız.",
            bankHint = BankProvider.TOM_BANK
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals(BankProvider.TOM_BANK, tx.bank)
        assertEquals("4412", tx.cardLast4)
        assertEquals(DetectedCardType.DEBIT, tx.detectedCardType)
        assertEquals("GETİR", tx.merchant)
        assertEquals(320.50, tx.amount, 0.001)
    }

    @Test
    fun resolvesSmsAndRcsSendersStrictlyForKnownBanks() {
        assertEquals(BankProvider.YAPI_KREDI, YkbTransactionParser.resolveBankFromSmsSender("YAPIKREDI"))
        assertEquals(BankProvider.VAKIFBANK, YkbTransactionParser.resolveBankFromSmsSender("VAKIFBANK"))
        assertEquals(BankProvider.TOM_BANK, YkbTransactionParser.resolveBankFromSmsSender("TOMBank"))
        assertEquals(BankProvider.TOM_BANK, YkbTransactionParser.resolveBankFromSmsSender("Hadi"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("+905321112233"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("Ahmet Yılmaz"))
    }

    @Test
    fun parsesDigitalVirtualCardWithUsdCurrency() {
        val outcome = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile STEAM GAMES işyerinden 19.99 USD harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(outcome is ParseOutcome.Success)
        val tx = (outcome as ParseOutcome.Success).transaction
        assertEquals("9104", tx.cardLast4)
        assertEquals(DetectedCardType.VIRTUAL, tx.detectedCardType)
        assertEquals(19.99, tx.amount, 0.001)
        assertEquals("USD", tx.currency)
    }

    @Test
    fun parsesRefundTransaction() {
        val outcome = YkbTransactionParser.parse(
            title = "Harcama İadesi",
            body = "4543****4821 nolu kartınıza TRENDYOL.COM işyerinden 390,00 TL iade işlemi yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
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
        val outcome = YkbTransactionParser.parse("TOMBank", rawOtp, BankProvider.TOM_BANK)
        assertTrue(outcome is ParseOutcome.Ignored)

        val sanitized = YkbTransactionParser.sanitizeForLog(rawOtp)
        assertFalse(sanitized.contains("583921"))
        assertTrue(sanitized.contains("******"))
    }
}
