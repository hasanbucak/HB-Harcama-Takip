package com.hasanbucak.harcamatakip.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YkbTransactionParserTest {

    @Test
    fun parsesStandardWorldcardPosNotificationAndExtractsClockTime() {
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
        assertFalse(tx.isGenericMerchant)
        assertEquals(645.50, tx.amount, 0.001)
        assertEquals("TL", tx.currency)
        assertEquals(ParsedTransactionType.EXPENSE, tx.transactionType)
        assertEquals(1, tx.installmentCount)
        assertEquals(14 * 60 + 32, tx.txClockMinute)
        assertEquals(CategoryClassifier.MARKET, CategoryClassifier.classify(tx.merchant))
    }

    @Test
    fun resolvesAll13BankPackagesAndStrictSmsSenders() {
        assertEquals(BankProvider.GARANTI, YkbTransactionParser.resolveBankForPackage("com.garanti.cepsubesi"))
        assertEquals(BankProvider.IS_BANKASI, YkbTransactionParser.resolveBankForPackage("com.pozitron.iscep"))
        assertEquals(BankProvider.AKBANK, YkbTransactionParser.resolveBankForPackage("com.akbank.android.apps.akbank_direkt"))
        assertEquals(BankProvider.AKBANK, YkbTransactionParser.resolveBankForPackage("com.akbank.android.apps.axess"))
        assertEquals(BankProvider.ZIRAAT, YkbTransactionParser.resolveBankForPackage("com.ziraat.ziraatmobil"))
        assertEquals(BankProvider.ENPARA, YkbTransactionParser.resolveBankForPackage("com.enparabank.retail"))
        assertEquals(BankProvider.KUVEYT_TURK, YkbTransactionParser.resolveBankForPackage("com.kuveytturk.mobil"))
        assertEquals(BankProvider.TEB, YkbTransactionParser.resolveBankForPackage("com.teb"))
        assertEquals(BankProvider.DENIZBANK, YkbTransactionParser.resolveBankForPackage("com.denizbank.mobildeniz"))
        assertEquals(BankProvider.PAPARA, YkbTransactionParser.resolveBankForPackage("com.mobillium.papara"))

        // Exact short codes & sender headers
        assertEquals(BankProvider.YAPI_KREDI, YkbTransactionParser.resolveBankFromSmsSender("YAPIKREDI"))
        assertEquals(BankProvider.YAPI_KREDI, YkbTransactionParser.resolveBankFromSmsSender("4454"))
        assertEquals(BankProvider.VAKIFBANK, YkbTransactionParser.resolveBankFromSmsSender("VAKIFBANK"))
        assertEquals(BankProvider.VAKIFBANK, YkbTransactionParser.resolveBankFromSmsSender("5724"))
        assertEquals(BankProvider.TOM_BANK, YkbTransactionParser.resolveBankFromSmsSender("TOMBank"))
        assertEquals(BankProvider.TOM_BANK, YkbTransactionParser.resolveBankFromSmsSender("Hadi"))
        assertEquals(BankProvider.GARANTI, YkbTransactionParser.resolveBankFromSmsSender("3340"))
        assertEquals(BankProvider.IS_BANKASI, YkbTransactionParser.resolveBankFromSmsSender("4402"))
        assertEquals(BankProvider.ZIRAAT, YkbTransactionParser.resolveBankFromSmsSender("ZIRAATBANK"))

        // Personal numbers, substrings, and contact names MUST be rejected
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("+905324454123"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("+905321457240"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("05324454123"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("Hadi Yılmaz"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("TEBRIKLER"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("BONUS"))
        assertNull(YkbTransactionParser.resolveBankFromSmsSender("WORLDCARD"))
    }

    @Test
    fun rejectsPromotionalCampaignsWithoutTransactionSentence() {
        val promo1 = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "1.000 TL ve üzeri alışverişinize 100 TL Worldpuan!",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(promo1 is ParseOutcome.Ignored)

        val promoRefund = YkbTransactionParser.parse(
            title = "Garanti BBVA",
            body = "1.000 TL'lik alışverişinize 100 TL iade fırsatı! Katılmak için hemen tıklayın.",
            bankHint = BankProvider.GARANTI
        )
        assertTrue(promoRefund is ParseOutcome.Ignored)
    }

    @Test
    fun keepsRealTransactionsContainingBonusWorldpuanChipParaOrKampanyaMerchant() {
        val bonusOutcome = YkbTransactionParser.parse(
            title = "Garanti BBVA",
            body = "Bonus kartınızla 1234 ile biten karttan MİGROS işyerinden 450,00 TL harcama yaptınız.",
            bankHint = BankProvider.GARANTI
        )
        assertTrue(bonusOutcome is ParseOutcome.Success)
        assertEquals(450.0, (bonusOutcome as ParseOutcome.Success).transaction.amount, 0.001)

        val pointsTail = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "4543****4821 nolu kartınızla OPET işyerinden 450,00 TL harcama yapılmıştır. Bu işlemden 12,50 TL Worldpuan kazandınız.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(pointsTail is ParseOutcome.Success)
        assertEquals(450.0, (pointsTail as ParseOutcome.Success).transaction.amount, 0.001)

        val chipPara = YkbTransactionParser.parse(
            title = "Jüzdan",
            body = "8812 ile biten Axess kartınızla 300,00 TL harcama yaptınız, 3,00 TL chip-para kazandınız.",
            bankHint = BankProvider.AKBANK
        )
        assertTrue(chipPara is ParseOutcome.Success)
        assertEquals(300.0, (chipPara as ParseOutcome.Success).transaction.amount, 0.001)

        val kampanyaMerchant = YkbTransactionParser.parse(
            title = "Harcama Bilgisi",
            body = "4543****4821 nolu kartınızla KAMPANYA MARKET işyerinden 275,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(kampanyaMerchant is ParseOutcome.Success)
        assertEquals("KAMPANYA MARKET", (kampanyaMerchant as ParseOutcome.Success).transaction.merchant)
    }

    @Test
    fun distinguishesOtpInstructionsFromCompleted3dSecureOrAuthCodeReceipts() {
        // Real OTPs -> Ignored with isSensitiveOtp = true
        val otp1 = YkbTransactionParser.parse(
            title = "YAPIKREDI",
            body = "TRENDYOL harcamanız için tek kullanımlık şifre: 583921. Kimseyle paylaşmayınız.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(otp1 is ParseOutcome.Ignored && otp1.isSensitiveOtp)

        val otp2 = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "İşlem doğrulama Kod: 4829",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(otp2 is ParseOutcome.Ignored && otp2.isSensitiveOtp)

        // Completed receipts mentioning 3D Secure, PIN, or POS Approval Code -> Success!
        val receiptWithAuthCode = YkbTransactionParser.parse(
            title = "Garanti BBVA",
            body = "4543****1122 kartınızla AMAZON işyerinden 450,00 TL harcama yapıldı. Onay Kodu: 123456.",
            bankHint = BankProvider.GARANTI
        )
        assertTrue(receiptWithAuthCode is ParseOutcome.Success)
        assertEquals(450.0, (receiptWithAuthCode as ParseOutcome.Success).transaction.amount, 0.001)

        val receipt3d = YkbTransactionParser.parse(
            title = "İşCep",
            body = "3D Secure ile 7744 nolu kartınızdan HEPSİBURADA işyerinden 950,00 TL ödeme yapıldı.",
            bankHint = BankProvider.IS_BANKASI
        )
        assertTrue(receipt3d is ParseOutcome.Success)
        assertEquals(950.0, (receipt3d as ParseOutcome.Success).transaction.amount, 0.001)

        val receiptPin = YkbTransactionParser.parse(
            title = "Ziraat Mobil",
            body = "5511 ile biten kartınızla PIN ile BİM işyerinden 300,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.ZIRAAT
        )
        assertTrue(receiptPin is ParseOutcome.Success)
        assertEquals(300.0, (receiptPin as ParseOutcome.Success).transaction.amount, 0.001)
    }

    @Test
    fun rejectsDeclinedTransactionsCardDebtPaymentsAndIbanTransfersWithoutFalsePositives() {
        val declined1 = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "4543****4821 nolu kartınızla 450,00 TL tutarındaki harcama işleminiz yetersiz limit nedeniyle gerçekleştirilememiştir.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(declined1 is ParseOutcome.Ignored)

        val declined2 = YkbTransactionParser.parse(
            title = "Akbank",
            body = "1234 nolu kartınızla 450,00 TL harcama yapılamadı.",
            bankHint = BankProvider.AKBANK
        )
        assertTrue(declined2 is ParseOutcome.Ignored)

        val cardDebt = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "4543****4821 nolu kredi kartı borç ödemeniz alınmıştır: 12.500,00 TL.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(cardDebt is ParseOutcome.Ignored)

        val ibanFast = YkbTransactionParser.parse(
            title = "Enpara",
            body = "FAST ile TR120006200000012345678901 IBAN hesabına 1.500,00 TL ödeme yapılmıştır.",
            bankHint = BankProvider.ENPARA
        )
        assertTrue(ibanFast is ParseOutcome.Ignored)

        // Bill payment and substring trap merchants ("DEFTER", "BREAKFAST") MUST succeed!
        val billPayment = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "4543****4821 nolu kartınızdan TURKCELL fatura ödemeniz alınmıştır, 350,00 TL.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(billPayment is ParseOutcome.Success)
        assertEquals(350.0, (billPayment as ParseOutcome.Success).transaction.amount, 0.001)

        val defter = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "4543****4821 nolu kartınızla DEFTER KIRTASİYE işyerinden 120,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(defter is ParseOutcome.Success)

        val breakfast = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "4543****4821 nolu kartınızla BREAKFAST CAFE işyerinden 280,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(breakfast is ParseOutcome.Success)
    }

    @Test
    fun detectsInflectedTurkishRefundsAndCancellations() {
        for (body in listOf(
            "4543****4821 nolu kartınıza 250,00 TL iadeniz yapılmıştır.",
            "4543****4821 nolu kartınıza MİGROS işyerinden 120,50 TL iadesi yapıldı.",
            "4543****4821 nolu kartınızla yapılan 99,90 TL harcama iptal edildi."
        )) {
            val outcome = YkbTransactionParser.parse("Bilgilendirme", body, BankProvider.YAPI_KREDI)
            assertTrue("Expected Success for: $body", outcome is ParseOutcome.Success)
            assertEquals(ParsedTransactionType.REFUND, (outcome as ParseOutcome.Success).transaction.transactionType)
        }
    }

    @Test
    fun extractsCorrectAmountAcrossTlCardPrefixCurrencySymbolLimitFirstAndDates() {
        // *4821 TLcard must NOT read 4821 as 4821 TL
        val tlCard = YkbTransactionParser.parse(
            title = "Yapı Kredi",
            body = "*4821 TLcard ile OPET İSTASYONU işyerinden 1.450,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(tlCard is ParseOutcome.Success)
        val tlCardTx = (tlCard as ParseOutcome.Success).transaction
        assertEquals("4821", tlCardTx.cardLast4)
        assertEquals(DetectedCardType.DEBIT, tlCardTx.detectedCardType)
        assertEquals(1450.0, tlCardTx.amount, 0.001)

        // Prefix currency "TL 645,50 ... Limit 84.354,50 TL"
        val prefixCurrency = YkbTransactionParser.parse(
            title = "VakıfBank",
            body = "6251 ile sonlanan kartınızla MİGROS işyerinden TL 645,50 harcama yapılmıştır. Limit 84.354,50 TL",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(prefixCurrency is ParseOutcome.Success)
        assertEquals(645.50, (prefixCurrency as ParseOutcome.Success).transaction.amount, 0.001)

        // Limit sentence written BEFORE spend + ₺ symbol
        val limitFirst = YkbTransactionParser.parse(
            title = "VakıfBank",
            body = "Kalan limitiniz 84.354,50 TL. 6251 ile biten kartınızla 645,50 ₺ harcama yapılmıştır.",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(limitFirst is ParseOutcome.Success)
        assertEquals(645.50, (limitFirst as ParseOutcome.Success).transaction.amount, 0.001)

        // Amount followed immediately by date must not corrupt date into amount
        val amountBeforeDate = YkbTransactionParser.parse(
            title = "Akbank",
            body = "1234 nolu kartınızla 645,50 TL 09.11.2026 tarihinde harcama yapılmıştır.",
            bankHint = BankProvider.AKBANK
        )
        assertTrue(amountBeforeDate is ParseOutcome.Success)
        assertEquals(645.50, (amountBeforeDate as ParseOutcome.Success).transaction.amount, 0.001)

        // Dual currency "19,99 USD (712,00 TL)"
        val dual = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "Dijital Kartınız (9104) ile STEAM işyerinden 19,99 USD (712,00 TL) harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(dual is ParseOutcome.Success)
        val dualTx = (dual as ParseOutcome.Success).transaction
        assertEquals("USD", dualTx.currency)
        assertEquals(19.99, dualTx.amount, 0.001)
        assertNotNull(dualTx.secondaryAmountTl)
        assertEquals(712.0, dualTx.secondaryAmountTl!!, 0.001)
    }

    @Test
    fun extractsCardLast4WithoutConfusingMerchantAsteriskOrAmountAndPreservesKnownBankHint() {
        // "9988 ile sonlanan ... 6251,00 TL" -> last4 MUST be 9988, NOT 6251
        val vakif9988 = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "9988 ile sonlanan kartınızla TEKNOSA işyerinden 6251,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(vakif9988 is ParseOutcome.Success)
        assertEquals("9988", (vakif9988 as ParseOutcome.Success).transaction.cardLast4)
        assertEquals(6251.0, (vakif9988 as ParseOutcome.Success).transaction.amount, 0.001)

        // "IYZICO*1234" merchant code before card or without card must NOT become cardLast4 "1234"
        val iyzicoWithCard = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "IYZICO*1234 işyerinden 5400****6251 nolu kartınızla 320,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(iyzicoWithCard is ParseOutcome.Success)
        assertEquals("6251", (iyzicoWithCard as ParseOutcome.Success).transaction.cardLast4)

        val iyzicoCardless = YkbTransactionParser.parse(
            title = "VAKIFBANK",
            body = "IYZICO*1234 işyerinden kartınızla 320,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.VAKIFBANK
        )
        assertTrue(iyzicoCardless is ParseOutcome.Success)
        assertEquals("GENEL", (iyzicoCardless as ParseOutcome.Success).transaction.cardLast4)

        // Known bankHint = YAPI_KREDI must NOT flip to VAKIFBANK when merchant is "VAKIFBANK SIGORTA"
        val ykbAtVakifMerchant = YkbTransactionParser.parse(
            title = "Yapı Kredi Mobil",
            body = "4543****4821 nolu kartınızla VAKIFBANK SİGORTA işyerinden 900,00 TL harcama yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(ykbAtVakifMerchant is ParseOutcome.Success)
        assertEquals(BankProvider.YAPI_KREDI, (ykbAtVakifMerchant as ParseOutcome.Success).transaction.bank)
    }

    @Test
    fun parsesPlusInstallmentsColonInstallmentsAndWordBoundaryCategories() {
        val plusInst = YkbTransactionParser.parse(
            title = "World Mobil",
            body = "4543****4821 nolu kartınızla TEKNOSA işyerinden 3+2 taksit ile 5.000,00 TL alışveriş yapılmıştır.",
            bankHint = BankProvider.YAPI_KREDI
        )
        assertTrue(plusInst is ParseOutcome.Success)
        assertEquals(5, (plusInst as ParseOutcome.Success).transaction.installmentCount)

        val colonInst = YkbTransactionParser.parse(
            title = "Garanti BBVA",
            body = "1234 ile biten kartınızla VATAN BİLGİSAYAR işyerinden 12.000,00 TL harcama yapıldı. Taksit sayısı: 6",
            bankHint = BankProvider.GARANTI
        )
        assertTrue(colonInst is ParseOutcome.Success)
        assertEquals(6, (colonInst as ParseOutcome.Success).transaction.installmentCount)

        // Word boundary check: "BASKI MERKEZİ" must NOT match "ASKI"
        assertEquals(CategoryClassifier.OTHER, CategoryClassifier.classify("BASKI MERKEZİ"))
        assertEquals(CategoryClassifier.BILLS_SUBSCRIPTION, CategoryClassifier.classify("ASKİ GENEL MÜDÜRLÜĞÜ"))
    }
}
