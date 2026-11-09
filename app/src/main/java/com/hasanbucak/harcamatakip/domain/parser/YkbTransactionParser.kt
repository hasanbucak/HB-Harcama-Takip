package com.hasanbucak.harcamatakip.domain.parser

import java.util.Locale

enum class BankProvider(
    val code: String,
    val displayName: String,
    val defaultColorHex: Long
) {
    YAPI_KREDI("YAPI_KREDI", "Yapı Kredi", 0xFF1E3A8A),
    VAKIFBANK("VAKIFBANK", "VakıfBank", 0xFFB45309),
    TOM_BANK("TOM_BANK", "TOM Bank", 0xFF0F766E),
    GARANTI("GARANTI", "Garanti BBVA", 0xFF15803D),
    IS_BANKASI("IS_BANKASI", "İş Bankası", 0xFF1D4ED8),
    AKBANK("AKBANK", "Akbank", 0xFFDC2626),
    ZIRAAT("ZIRAAT", "Ziraat Bankası", 0xFFB91C1C),
    ENPARA("ENPARA", "Enpara", 0xFF7C3AED),
    QNB("QNB", "QNB", 0xFF312E81),
    KUVEYT_TURK("KUVEYT_TURK", "Kuveyt Türk", 0xFF047857),
    TEB("TEB", "TEB", 0xFF0D9488),
    DENIZBANK("DENIZBANK", "DenizBank", 0xFF0284C7),
    PAPARA("PAPARA", "Papara", 0xFF111827),
    OTHER("OTHER", "Diğer", 0xFF475569);

    companion object {
        val selectableBanks: List<BankProvider> = entries.filter { it != OTHER }

        fun fromCode(code: String): BankProvider {
            return entries.find { it.code == code } ?: OTHER
        }
    }
}

enum class ParsedTransactionType {
    EXPENSE,
    REFUND
}

enum class DetectedCardType(val labelTr: String) {
    CREDIT("Kredi Kartı"),
    VIRTUAL("Dijital / Sanal Kart"),
    DEBIT("Banka Kartı"),
    CASH("Nakit");

    companion object {
        fun fromCode(code: String): DetectedCardType {
            return entries.find { it.name == code } ?: CREDIT
        }
    }
}

data class ParsedTransaction(
    val bank: BankProvider,
    val cardLast4: String,
    val detectedCardType: DetectedCardType,
    val merchant: String,
    val isGenericMerchant: Boolean,
    val amount: Double,
    val currency: String,
    val secondaryAmountTl: Double? = null,
    val transactionType: ParsedTransactionType,
    val installmentCount: Int,
    val txClockMinute: Int? = null
)

sealed class ParseOutcome {
    data class Success(val transaction: ParsedTransaction) : ParseOutcome()
    data class Ignored(val reason: String, val isSensitiveOtp: Boolean = false) : ParseOutcome()
    data class Unparsed(val reason: String) : ParseOutcome()
}

object YkbTransactionParser {

    @Deprecated("Legacy migration constant; card rules are now user-configured via BankRuleEntity")
    const val VAKIFBANK_ALLOWED_LAST4 = "6251"

    private val DIRECT_BANK_PACKAGES: Map<String, BankProvider> = mapOf(
        "com.ykb.android" to BankProvider.YAPI_KREDI,
        "com.ykb.avm" to BankProvider.YAPI_KREDI,
        "com.vakifbank.mobile" to BankProvider.VAKIFBANK,
        "com.vakifbank.ceptekazan" to BankProvider.VAKIFBANK,
        "com.tom.hadi" to BankProvider.TOM_BANK,
        "com.garanti.cepsubesi" to BankProvider.GARANTI,
        "com.garanti.bonus" to BankProvider.GARANTI,
        "com.pozitron.iscep" to BankProvider.IS_BANKASI,
        "com.softtech.maxipara" to BankProvider.IS_BANKASI,
        "com.akbank.android.apps.akbank_direkt" to BankProvider.AKBANK,
        "com.akbank.android.apps.axess" to BankProvider.AKBANK,
        "com.ziraat.ziraatmobil" to BankProvider.ZIRAAT,
        "com.ziraat.bankkart" to BankProvider.ZIRAAT,
        "com.enparabank.retail" to BankProvider.ENPARA,
        "com.finansbank.enpara" to BankProvider.ENPARA,
        "com.finansbank.mobile.cepsube" to BankProvider.QNB,
        "com.kuveytturk.mobil" to BankProvider.KUVEYT_TURK,
        "com.teb" to BankProvider.TEB,
        "com.denizbank.mobildeniz" to BankProvider.DENIZBANK,
        "com.mobillium.papara" to BankProvider.PAPARA
    )

    val MESSAGING_PACKAGES: Set<String> = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.miui.mms"
    )

    private val PERSONAL_PHONE_REGEX = Regex("""^(?:\+?90|0)?5\d{9}$""")

    private val EXACT_SENDER_MAP: Map<String, BankProvider> = mapOf(
        // Yapı Kredi
        "YAPIKREDI" to BankProvider.YAPI_KREDI,
        "YAPI KREDI" to BankProvider.YAPI_KREDI,
        "YAPI KREDI BANKASI" to BankProvider.YAPI_KREDI,
        "4440444" to BankProvider.YAPI_KREDI,
        "08502220444" to BankProvider.YAPI_KREDI,
        "4454" to BankProvider.YAPI_KREDI,
        // VakıfBank
        "VAKIFBANK" to BankProvider.VAKIFBANK,
        "VAKIF BANK" to BankProvider.VAKIFBANK,
        "08502220724" to BankProvider.VAKIFBANK,
        "4440724" to BankProvider.VAKIFBANK,
        "5724" to BankProvider.VAKIFBANK,
        // TOM Bank
        "TOMBANK" to BankProvider.TOM_BANK,
        "TOM BANK" to BankProvider.TOM_BANK,
        "TOMKATILIM" to BankProvider.TOM_BANK,
        "TOM KATILIM" to BankProvider.TOM_BANK,
        "HADI" to BankProvider.TOM_BANK,
        "HADI BANK" to BankProvider.TOM_BANK,
        "TOM BANK HADI" to BankProvider.TOM_BANK,
        "08503993636" to BankProvider.TOM_BANK,
        // Garanti BBVA
        "GARANTI" to BankProvider.GARANTI,
        "GARANTIBBVA" to BankProvider.GARANTI,
        "GARANTI BBVA" to BankProvider.GARANTI,
        "4440333" to BankProvider.GARANTI,
        "3340" to BankProvider.GARANTI,
        // İş Bankası
        "ISBANKASI" to BankProvider.IS_BANKASI,
        "IS BANKASI" to BankProvider.IS_BANKASI,
        "TURKIYE IS BANKASI" to BankProvider.IS_BANKASI,
        "MAXIMUM" to BankProvider.IS_BANKASI,
        "08507240724" to BankProvider.IS_BANKASI,
        "4402" to BankProvider.IS_BANKASI,
        // Akbank
        "AKBANK" to BankProvider.AKBANK,
        "AXESS" to BankProvider.AKBANK,
        "JUZDAN" to BankProvider.AKBANK,
        "4442525" to BankProvider.AKBANK,
        "4566" to BankProvider.AKBANK,
        // Ziraat
        "ZIRAAT" to BankProvider.ZIRAAT,
        "ZIRAATBANK" to BankProvider.ZIRAAT,
        "ZIRAAT BANKASI" to BankProvider.ZIRAAT,
        "BANKKART" to BankProvider.ZIRAAT,
        "4440000" to BankProvider.ZIRAAT,
        "4747" to BankProvider.ZIRAAT,
        // Enpara
        "ENPARA" to BankProvider.ENPARA,
        "ENPARA COM" to BankProvider.ENPARA,
        "ENPARABANK" to BankProvider.ENPARA,
        "08502223663" to BankProvider.ENPARA,
        // QNB
        "QNB" to BankProvider.QNB,
        "FINANSBANK" to BankProvider.QNB,
        "QNB FINANSBANK" to BankProvider.QNB,
        "CARDFINANS" to BankProvider.QNB,
        "08502220900" to BankProvider.QNB,
        "2273" to BankProvider.QNB,
        // Kuveyt Türk
        "KUVEYTTURK" to BankProvider.KUVEYT_TURK,
        "KUVEYT TURK" to BankProvider.KUVEYT_TURK,
        "SAGLAMKART" to BankProvider.KUVEYT_TURK,
        "4440123" to BankProvider.KUVEYT_TURK,
        // TEB
        "TEB" to BankProvider.TEB,
        "CEPTETEB" to BankProvider.TEB,
        "08502000666" to BankProvider.TEB,
        "4600" to BankProvider.TEB,
        // DenizBank
        "DENIZBANK" to BankProvider.DENIZBANK,
        "DENIZ BANK" to BankProvider.DENIZBANK,
        "MOBILDENIZ" to BankProvider.DENIZBANK,
        "08502220800" to BankProvider.DENIZBANK,
        "3280" to BankProvider.DENIZBANK,
        // Papara
        "PAPARA" to BankProvider.PAPARA,
        "08503400340" to BankProvider.PAPARA
    )

    private val COMPLETED_TX_VERB_REGEX = Regex(
        """\b(?:HARCAMA\s+YAP\w+|HARCAMA\s+GERCEKLES\w+|ALISVERIS\s+YAP\w+|ISLEM\s+GERCEKLES\w+|ISLEM\s+YAPIL\w+|ODEME\s+YAPIL\w+|ODEME\s+GERCEKLES\w+|FATURA\s+ODEMENIZ\s+ALIN\w+|CEKIM\s+YAPIL\w+|IADE\w*\s+YAPIL\w+|IADE\s+EDIL\w+|IADE\s+ISLEMI\s+YAP\w+|IPTAL\s+EDIL\w+|IPTAL\s+ISLEMI)\b"""
    )

    private val GENERAL_TX_SIGNAL_REGEX = Regex(
        """\b(?:HARCAMA\w*|ALISVERIS\w*|ISLEM\s+GERCEKLES\w*|ISLEM\s+YAPIL\w*|TUTARINDA\s+ISLEM\w*|ODEME\s+YAPIL\w*|ODEME\s+GERCEKLES\w*|FATURA\s+ODEME\w*|CEKIM\s+YAPIL\w*|PROVIZYON\w*|IADE|IADENIZ|IADESI|IPTAL\s+EDIL\w*|IPTAL\s+ISLEMI)\b"""
    )

    private val DECLINED_TX_REGEX = Regex(
        """\b(?:GERCEKLESTIRILEME\w*|GERCEKLESME(?:DI|MIS)\w*|YAPILAMA\w*|TAMAMLANAMA\w*|REDDEDIL\w*|BASARISIZ\w*|ONAYLANMA(?:DI|MIS)\w*|YETERSIZ\s+LIMIT|YETERSIZ\s+BAKIYE|LIMIT\s+ASIM\w*|HATALI\s+SIFRE|HATALI\s+PIN)\b"""
    )

    private val CARD_DEBT_OR_STATEMENT_REGEX = Regex(
        """\b(?:(?:KREDI\s+KART\w*\s+|KART\w*\s+)?BORC\w*\s+ODEME\w*|EKSTRE\s+ODEME\w*|HESABINIZDAN\s+KARTINIZA|DONEM\s+BORC\w*|ASGARI\s+ODEME|EKSTRENIZ\s+KESIL\w*|HESAP\s+OZETINIZ|SON\s+ODEME\s+TARIHI|KART\s+BORCUNUZ)\b"""
    )

    private val IBAN_TRANSFER_REGEX = Regex(
        """(?:\bFAST\s+ILE\b|\bFAST\s+ISLEM\w*\b|\bEFT\b|\bHAVALE\b|\bIBAN\b|\bPARA\s+TRANSFER\w*\b|\bHESABINIZA\s+.{0,40}\b(?:GELMISTIR|YATIRILMISTIR|AKTARILMISTIR)\b)"""
    )

    private val PROMO_OFFER_REGEX = Regex(
        """\b(?:UZERI\s+ALISVERIS\w*|ALISVERISINIZE|ALISVERISLERINIZE|HARCAMANIZA|HARCAMALARINIZA|IADE\s+FIRSAT\w*|KAZANMA\s+FIRSAT\w*|TAKSIT\s+FIRSAT\w*|FIRSATI\s+KACIRMA\w*|KATILMAK\s+ICIN|HEMEN\s+TIKLA\w*|FAIZLI\s+ESNEK\s+HESAP|LIMITINIZI\s+HEMEN|RET\s+YAZ\w*|MERSIS)\b"""
    )

    private val DIRECT_OTP_KEYWORDS_REGEX = Regex(
        """\b(?:TEK\s+KULLANIMLIK|DOGRULAMA\s+KOD\w*|GUVENLIK\s+KOD\w*|AKILLI\s+SIFRE|MOBIL\s+GIRIS|GIRIS\s+SIFRE\w*|SIFRENIZ\w*|PAROLANIZ\w*|KODUNUZ\w*|OTP)\b"""
    )

    private val OTP_CODE_LABEL_REGEX = Regex("""\b(?:SIFRE|PAROLA|KOD|ONAY\s+KODU|PIN)\s*[:=]?\s*\d{4,8}\b""")
    private val OTP_INSTRUCTION_VERB_REGEX = Regex(
        """\b(?:PAYLASMA\w*|KIMSEYLE|GIRINIZ|GIREREK|ONAYLAYIN\w*|DOGRULA\w*|GECERLIDIR)\b"""
    )

    private val CARD_PATTERNS: List<Regex> = listOf(
        // 4543****1234, 5400********6251, ****6251, •••• 6251, ..6251
        Regex("""(?:\b\d{4}[\s*•.xX-]{2,12}|(?:^|[\s(])[*•.xX]{2,10}\s*)(\d{4})\b"""),
        // Standalone leading *1907 or *4821 (NOT preceded by merchant letters/digits like IYZICO*1234)
        Regex("""(?:^|[\s(])\*(\d{4})\b"""),
        // 6251 nolu / numarali / ile biten / ile sonlanan / son haneli
        Regex("""\b(\d{4})\s*(?:NO['’]?LU|NOLU|NUMARALI|ILE\s+BITEN|BITEN|ILE\s+SONLANAN|SONLANAN|SON\s+HANELI)\b"""),
        // son 4 hanesi 6251 / sonu 6251
        Regex("""\b(?:SON\s*4\s*HANESI|SONU)\s*:?\s*(\d{4})\b"""),
        // Dijital Kartiniz (1234) / Kart (6251)
        Regex("""\bKART[A-Z'’]*\s*\(\s*(\d{4})\s*\)""")
    )

    private val DATE_MASK_REGEX = Regex("""\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}\b""")
    private val TIME_MASK_REGEX = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?\b""")

    private val NOISE_CLAUSE_PATTERNS: List<Regex> = listOf(
        Regex("""\b(?:GUNCEL\s+)?KULLANILABILIR\s+(?:LIMIT|BAKIYE)\b[^.!;\n]*"""),
        Regex("""\bKALAN\s+(?:LIMIT|BAKIYE)\b[^.!;\n]*"""),
        Regex("""\b(?:LIMITINIZ|LIMIT|BAKIYENIZ|BAKIYE)\s*:?\s*(?:TL|TRY|USD|EUR|GBP|₺|\$|€)?\s*\d[\d.,]*\s*(?:TL|TRY|USD|EUR|GBP|₺|\$|€)?[^.!;\n]*"""),
        Regex("""\bBU\s+ISLEMDEN\b[^.!;\n]*\bPUAN\b[^.!;\n]*"""),
        Regex("""\bTOPLAM\s+[A-Z-]*PUAN\b[^.!;\n]*"""),
        Regex("""\bKAZANILAN\s+[A-Z-]*PUAN\b[^.!;\n]*"""),
        Regex("""\b\d[\d.,]*\s*(?:TL\s*)?(?:WORLDPUAN|CHIP-PARA|CHIPPARA|BONUS|MAXIPUAN|BANKKART\s+LIRA|PARAFPARA)\s+KAZAN\w*[^.!;\n]*"""),
        Regex("""\bONAY\s+KODU\s*:\s*\d+\b""")
    )

    // Suffix currency: "645,50 TL", "19.99 USD", "645,50 ₺", "14.99 $"
    // (?![A-Z0-9]) prevents "*4821 TLcard" from matching "4821 TL"
    private val SUFFIX_AMOUNT_REGEX = Regex(
        """(?<![A-Z0-9*])(\d[\d.,]*)\s*(TL|TRY|USD|EUR|GBP|₺|\$|€)(?![A-Z0-9])"""
    )

    // Prefix currency: "TL 645,50", "USD 19.99", "₺645,50", "$14.99"
    private val PREFIX_AMOUNT_REGEX = Regex(
        """(?<![A-Z0-9])(TL|TRY|USD|EUR|GBP|₺|\$|€)\s*(\d[\d.,]*)(?![A-Z0-9])"""
    )

    private val DUAL_CURRENCY_REGEX = Regex(
        """(?<![A-Z0-9*])(\d[\d.,]*)\s*(USD|EUR|GBP|\$|€)(?![A-Z0-9])\s*\(\s*(\d[\d.,]*)\s*(TL|TRY|₺)(?![A-Z0-9])\s*\)"""
    )

    private val PLUS_INSTALLMENT_REGEX = Regex("""\b(\d{1,2})\s*\+\s*(\d{1,2})\s*TAKSIT\w*\b""")
    private val COLON_INSTALLMENT_REGEX = Regex("""\bTAKSIT\s*(?:SAYISI|ADEDI)?\s*:\s*(\d{1,2})\b""")
    private val SIMPLE_INSTALLMENT_REGEX = Regex("""\b(\d{1,2})\s*TAKSIT\w*\b""")

    private val REFUND_REGEX = Regex(
        """\b(?:IADE|IADENIZ|IADESI|IADELERI|IPTAL\s+EDIL\w*|IPTAL\s+ISLEMI)\b"""
    )

    fun resolveBankForPackage(packageName: String): BankProvider? {
        return DIRECT_BANK_PACKAGES[packageName]
    }

    fun resolveBankFromSmsSender(senderOrTitle: String?): BankProvider? {
        if (senderOrTitle.isNullOrBlank()) return null
        val rawTrimmed = senderOrTitle.trim()
        val digitsAndPlus = rawTrimmed.replace(Regex("""[\s()-]"""), "")
        if (PERSONAL_PHONE_REGEX.matches(digitsAndPlus)) {
            return null
        }

        val normalized = CategoryClassifier.toAsciiUpper(rawTrimmed)
            .replace(Regex("""[^A-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isEmpty()) return null

        val compact = normalized.replace(" ", "")
        EXACT_SENDER_MAP[normalized]?.let { return it }
        EXACT_SENDER_MAP[compact]?.let { return it }

        // Bank sender headers with suffix like "YAPI KREDI MOBIL" or "GARANTI BBVA MOBIL",
        // while strictly rejecting personal names like "HADI YILMAZ" or substring "TEBRIKLER"
        return when {
            normalized.startsWith("YAPI KREDI ") || normalized.startsWith("YAPIKREDI ") -> BankProvider.YAPI_KREDI
            normalized.startsWith("VAKIFBANK ") || normalized.startsWith("VAKIF BANK ") -> BankProvider.VAKIFBANK
            normalized.startsWith("TOMBANK ") || normalized.startsWith("TOM BANK ") -> BankProvider.TOM_BANK
            normalized.startsWith("GARANTI BBVA ") -> BankProvider.GARANTI
            normalized.startsWith("IS BANKASI ") || normalized.startsWith("ISCEP") -> BankProvider.IS_BANKASI
            normalized.startsWith("AKBANK ") -> BankProvider.AKBANK
            normalized.startsWith("ZIRAAT BANKASI ") || normalized.startsWith("ZIRAAT MOBIL") -> BankProvider.ZIRAAT
            normalized.startsWith("KUVEYT TURK ") -> BankProvider.KUVEYT_TURK
            normalized.startsWith("QNB FINANSBANK ") -> BankProvider.QNB
            normalized.startsWith("DENIZBANK ") -> BankProvider.DENIZBANK
            else -> null
        }
    }

    fun isSupportedSmsSender(sender: String?): Boolean {
        return resolveBankFromSmsSender(sender) != null
    }

    fun isOtpOrSecurityText(asciiUpper: String): Boolean {
        val hasCompletedSpend = COMPLETED_TX_VERB_REGEX.containsMatchIn(asciiUpper)
        if (DIRECT_OTP_KEYWORDS_REGEX.containsMatchIn(asciiUpper)) {
            if (!hasCompletedSpend || OTP_INSTRUCTION_VERB_REGEX.containsMatchIn(asciiUpper)) {
                return true
            }
        }
        if (OTP_CODE_LABEL_REGEX.containsMatchIn(asciiUpper)) {
            if (OTP_INSTRUCTION_VERB_REGEX.containsMatchIn(asciiUpper) || !hasCompletedSpend) {
                return true
            }
        }
        return false
    }

    /**
     * Zero-retention sanitizer: if the message is an OTP/security code prompt, drop the body
     * completely so secrets are never written to SQLite logs; otherwise mask any stray code labels.
     */
    fun sanitizeForLog(text: String): String {
        if (text.isBlank()) return ""
        val ascii = CategoryClassifier.toAsciiUpper(text)
        if (isOtpOrSecurityText(ascii)) {
            return "[OTP / Güvenlik mesajı — içerik kaydedilmedi]"
        }
        return text.replace(
            Regex("""(?i)\b(kod|şifre|sifre|parola|pin|otp)\s*([:=]?)\s*\d{4,8}\b"""),
            "$1$2 ******"
        )
    }

    fun parse(
        title: String?,
        body: String?,
        bankHint: BankProvider = BankProvider.OTHER
    ): ParseOutcome {
        val cleanTitle = title?.trim().orEmpty()
        val cleanBody = body?.trim().orEmpty()
        if (cleanBody.isEmpty() && cleanTitle.isEmpty()) {
            return ParseOutcome.Ignored("Boş bildirim içeriği")
        }

        val primaryRaw = cleanBody.ifEmpty { cleanTitle }
        val combinedRaw = if (cleanTitle.isNotEmpty() && !cleanBody.startsWith(cleanTitle, ignoreCase = true)) {
            "$cleanTitle - $cleanBody"
        } else {
            primaryRaw
        }

        val asciiPrimary = CategoryClassifier.toAsciiUpper(primaryRaw)
        val asciiCombined = CategoryClassifier.toAsciiUpper(combinedRaw)

        // 1. OTP / Security instruction check FIRST (zero retention)
        if (isOtpOrSecurityText(asciiCombined)) {
            return ParseOutcome.Ignored("Tek kullanımlık şifre / güvenlik kodu mesajı", isSensitiveOtp = true)
        }

        // 2. Resolve bank without letting body text override a known package/sender bankHint
        val resolvedBank = resolveBankFromTextIfNeeded(asciiCombined, bankHint)

        // 3. Extract clock time before masking dates & times
        val txClockMinute = extractClockMinute(asciiPrimary)

        // 4. Sentence-level classification
        val sentences = asciiPrimary
            .split(Regex("""[.!;\n]+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hasCompletedVerbAnywhere = COMPLETED_TX_VERB_REGEX.containsMatchIn(asciiCombined)

        // Check declined/failed status in any transaction sentence
        if (DECLINED_TX_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("Başarısız veya reddedilen işlem bildirimi")
        }

        // Check card debt / statement payments
        if (CARD_DEBT_OR_STATEMENT_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("Kart borcu veya ekstre bilgilendirmesi")
        }

        // Check IBAN / FAST / EFT / Havale transfers
        if (IBAN_TRANSFER_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("IBAN / para transferi (FAST/EFT/Havale) bildirimi")
        }

        // Promotional check ONLY rejects when there is no completed transaction sentence
        if (!hasCompletedVerbAnywhere && PROMO_OFFER_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("Kampanya veya tanıtım bildirimi")
        }

        if (!GENERAL_TX_SIGNAL_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("Harcama veya iade ifadesi bulunamadı")
        }

        // If message still looks like a conditional offer even with "iade" / "alisveris" noun
        val txSentences = sentences.filter { s ->
            GENERAL_TX_SIGNAL_REGEX.containsMatchIn(s) && !PROMO_OFFER_REGEX.containsMatchIn(s)
        }
        if (txSentences.isEmpty() && PROMO_OFFER_REGEX.containsMatchIn(asciiCombined)) {
            return ParseOutcome.Ignored("Kampanya veya tanıtım bildirimi")
        }

        // 5. Extract card last4 safely (never matching merchant*1234 or amount 6251,00 TL)
        val cardLast4 = extractCardLast4(asciiCombined) ?: "GENEL"

        // 6. Mask dates/times and strip limit/balance/points clauses before amount extraction
        val sanitizedForAmount = prepareTextForAmountExtraction(asciiPrimary)
        val dualMatch = DUAL_CURRENCY_REGEX.find(sanitizedForAmount)

        val amount: Double
        val currency: String
        val secondaryTl: Double?

        if (dualMatch != null) {
            amount = parseFlexibleAmount(dualMatch.groupValues[1])
                ?: return ParseOutcome.Unparsed("Döviz tutarı okunamadı")
            currency = normalizeCurrency(dualMatch.groupValues[2])
            secondaryTl = parseFlexibleAmount(dualMatch.groupValues[3])
        } else {
            val extracted = extractBestAmountAndCurrency(sanitizedForAmount)
                ?: extractBestAmountAndCurrency(prepareTextForAmountExtraction(asciiCombined))
                ?: return ParseOutcome.Unparsed("Tutar veya para birimi okunamadı")
            amount = extracted.first
            currency = extracted.second
            secondaryTl = null
        }

        if (amount <= 0.0) {
            return ParseOutcome.Unparsed("Geçersiz tutar: $amount")
        }

        val cardType = detectCardType(asciiCombined)
        val txType = detectTransactionType(asciiCombined)
        val installments = extractInstallments(asciiCombined)
        val (merchant, isGeneric) = extractMerchant(cleanTitle, primaryRaw, resolvedBank)

        return ParseOutcome.Success(
            ParsedTransaction(
                bank = resolvedBank,
                cardLast4 = cardLast4,
                detectedCardType = cardType,
                merchant = merchant,
                isGenericMerchant = isGeneric,
                amount = amount,
                currency = currency,
                secondaryAmountTl = secondaryTl,
                transactionType = txType,
                installmentCount = installments,
                txClockMinute = txClockMinute
            )
        )
    }

    private fun resolveBankFromTextIfNeeded(asciiText: String, hint: BankProvider): BankProvider {
        // Never allow merchant/ATM text inside a message to override a known package or SMS sender!
        if (hint != BankProvider.OTHER) {
            return hint
        }
        return when {
            asciiText.contains("VAKIFBANK") || asciiText.contains("BANKOMAT") -> BankProvider.VAKIFBANK
            asciiText.contains("TOMBANK") || asciiText.contains("TOM BANK") ||
                asciiText.contains("TOM KATILIM") || asciiText.contains("HADI KREDI") ||
                asciiText.contains("HADI KART") -> BankProvider.TOM_BANK
            asciiText.contains("GARANTI BBVA") || asciiText.contains("GARANTIBBVA") -> BankProvider.GARANTI
            asciiText.contains("IS BANKASI") || asciiText.contains("ISCEP") || asciiText.contains("MAXIMUM KART") -> BankProvider.IS_BANKASI
            asciiText.contains("AKBANK") || asciiText.contains("AXESS") || asciiText.contains("JUZDAN") -> BankProvider.AKBANK
            asciiText.contains("ZIRAAT") || asciiText.contains("BANKKART") -> BankProvider.ZIRAAT
            asciiText.contains("ENPARA") -> BankProvider.ENPARA
            asciiText.contains("QNB") || asciiText.contains("CARDFINANS") -> BankProvider.QNB
            asciiText.contains("KUVEYT TURK") || asciiText.contains("SAGLAMKART") -> BankProvider.KUVEYT_TURK
            Regex("""\b(?:CEPTETEB|TEB)\b""").containsMatchIn(asciiText) -> BankProvider.TEB
            asciiText.contains("DENIZBANK") || asciiText.contains("MOBILDENIZ") -> BankProvider.DENIZBANK
            asciiText.contains("PAPARA") -> BankProvider.PAPARA
            asciiText.contains("YAPI KREDI") || asciiText.contains("YAPIKREDI") ||
                asciiText.contains("WORLDCARD") || asciiText.contains("TLCARD") -> BankProvider.YAPI_KREDI
            else -> BankProvider.OTHER
        }
    }

    private fun extractClockMinute(asciiText: String): Int? {
        val match = TIME_MASK_REGEX.find(asciiText) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    private fun prepareTextForAmountExtraction(asciiText: String): String {
        var cleaned = asciiText
            .replace(DATE_MASK_REGEX) { " ".repeat(it.value.length) }
            .replace(TIME_MASK_REGEX) { " ".repeat(it.value.length) }
        for (noise in NOISE_CLAUSE_PATTERNS) {
            cleaned = cleaned.replace(noise, " ")
        }
        return cleaned
    }

    private fun extractCardLast4(asciiText: String): String? {
        for (pattern in CARD_PATTERNS) {
            val match = pattern.find(asciiText)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun detectCardType(asciiText: String): DetectedCardType {
        return when {
            asciiText.contains("DIJITAL KART") ||
                asciiText.contains("SANAL KART") ||
                asciiText.contains("HAYALET KART") -> DetectedCardType.VIRTUAL
            asciiText.contains("TLCARD") ||
                asciiText.contains("BANKOMAT") ||
                asciiText.contains("BANKA KART") ||
                asciiText.contains("PARACARD") ||
                asciiText.contains("ENCARD") ||
                asciiText.contains("PAPARA CARD") ||
                asciiText.contains("HADI KART") -> DetectedCardType.DEBIT
            asciiText.contains("KREDI KART") ||
                asciiText.contains("WORLDCARD") ||
                asciiText.contains("BONUS") ||
                asciiText.contains("AXESS") ||
                asciiText.contains("MAXIMUM") ||
                asciiText.contains("CARDFINANS") ||
                asciiText.contains("SAGLAMKART") -> DetectedCardType.CREDIT
            else -> DetectedCardType.CREDIT
        }
    }

    private fun detectTransactionType(asciiText: String): ParsedTransactionType {
        return if (REFUND_REGEX.containsMatchIn(asciiText)) {
            ParsedTransactionType.REFUND
        } else {
            ParsedTransactionType.EXPENSE
        }
    }

    private fun extractInstallments(asciiText: String): Int {
        PLUS_INSTALLMENT_REGEX.find(asciiText)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: 0
            val b = m.groupValues[2].toIntOrNull() ?: 0
            if (a + b >= 2) return (a + b).coerceIn(1, 36)
        }
        COLON_INSTALLMENT_REGEX.find(asciiText)?.let { m ->
            val c = m.groupValues[1].toIntOrNull() ?: 1
            return c.coerceIn(1, 36)
        }
        SIMPLE_INSTALLMENT_REGEX.find(asciiText)?.let { m ->
            val c = m.groupValues[1].toIntOrNull() ?: 1
            return c.coerceIn(1, 36)
        }
        return 1
    }

    private data class AmountCandidate(
        val amount: Double,
        val currency: String,
        val position: Int
    )

    fun extractAmountAndCurrency(text: String): Pair<Double, String>? {
        val ascii = prepareTextForAmountExtraction(CategoryClassifier.toAsciiUpper(text))
        return extractBestAmountAndCurrency(ascii)
    }

    private fun extractBestAmountAndCurrency(cleanedAscii: String): Pair<Double, String>? {
        val candidates = mutableListOf<AmountCandidate>()

        for (m in SUFFIX_AMOUNT_REGEX.findAll(cleanedAscii)) {
            val parsed = parseFlexibleAmount(m.groupValues[1])
            if (parsed != null && parsed > 0.0) {
                candidates.add(AmountCandidate(parsed, normalizeCurrency(m.groupValues[2]), m.range.first))
            }
        }
        for (m in PREFIX_AMOUNT_REGEX.findAll(cleanedAscii)) {
            val overlapsExisting = candidates.any { kotlin.math.abs(it.position - m.range.first) <= 3 }
            if (!overlapsExisting) {
                val parsed = parseFlexibleAmount(m.groupValues[2])
                if (parsed != null && parsed > 0.0) {
                    candidates.add(AmountCandidate(parsed, normalizeCurrency(m.groupValues[1]), m.range.first))
                }
            }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0].amount to candidates[0].currency

        val verbPos = GENERAL_TX_SIGNAL_REGEX.find(cleanedAscii)?.range?.first
        val chosen = if (verbPos != null) {
            candidates.minByOrNull { kotlin.math.abs(it.position - verbPos) } ?: candidates.first()
        } else {
            candidates.first()
        }
        return chosen.amount to chosen.currency
    }

    fun parseFlexibleAmount(raw: String): Double? {
        val trimmed = raw.trim().trimEnd('.', ',')
        if (trimmed.isEmpty()) return null

        val lastComma = trimmed.lastIndexOf(',')
        val lastDot = trimmed.lastIndexOf('.')

        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                if (lastComma > lastDot) {
                    trimmed.replace(".", "").replace(',', '.')
                } else {
                    trimmed.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                val decimals = trimmed.length - lastComma - 1
                if (decimals in 1..2) {
                    trimmed.replace(',', '.')
                } else {
                    trimmed.replace(",", "")
                }
            }
            lastDot >= 0 -> {
                val dotCount = trimmed.count { it == '.' }
                val decimals = trimmed.length - lastDot - 1
                if (dotCount == 1 && decimals in 1..2) {
                    trimmed
                } else {
                    trimmed.replace(".", "")
                }
            }
            else -> trimmed
        }

        return normalized.toDoubleOrNull()
    }

    private fun normalizeCurrency(raw: String): String {
        return when (raw.uppercase(Locale.US)) {
            "TL", "TRY", "₺" -> "TL"
            "USD", "$" -> "USD"
            "EUR", "€" -> "EUR"
            "GBP" -> "GBP"
            else -> raw.uppercase(Locale.US)
        }
    }

    /**
     * Uses 1:1 length-preserving ASCII folding to locate merchant ranges without relying on
     * platform-dependent Turkish Unicode case-insensitive regex, then slices original text.
     */
    private fun extractMerchant(
        title: String,
        body: String,
        bank: BankProvider
    ): Pair<String, Boolean> {
        val asciiBody = CategoryClassifier.toAsciiUpper(body)

        // Pattern 1: "... TARIHINDE / HH:MM'DE <MERCHANT> ISYERINDEN / ISYERINDE / FIRMASINDAN"
        val p1 = Regex(
            """(?:TARIHINDE|\d{2}:\d{2}['’]?(?:DA|DE|TA|TE)?(?:\s+TARIHINDE)?)\s+(.+?)\s+(?:IS\s*YERINDEN|IS\s*YERINDE|FIRMASINDAN|FIRMASINDA|UYE\s*ISYERINDEN)"""
        )
        p1.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        // Pattern 2: "... KARTINIZLA / KARTTAN / KARTINIZDAN / (1234) ILE [date/PIN] <MERCHANT> ISYERINDEN"
        val p2 = Regex(
            """(?:KARTINIZLA|KART'INIZLA|KART’INIZLA|KARTINIZA|KARTINIZDAN|KARTTAN|KARTINIZ\s+ILE|\(\d{4}\)\s*ILE|BITEN\s+[\w'’]+\s+ILE|BITEN\s+KARTINIZLA|TLCARD\s+ILE)\s+(?:PIN\s+ILE\s+)?(?:\d{2}[./]\d{2}[./]\d{4}(?:\s+\d{2}:\d{2}['’]?(?:DA|DE|TA|TE)?)?\s*(?:TARIHINDE\s+)?)?(.+?)\s+(?:IS\s*YERINDEN|IS\s*YERINDE|FIRMASINDAN|FIRMASINDA)"""
        )
        p2.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        // Pattern 3: "<MERCHANT> ISYERINDEN / ISYERINDE"
        val p3 = Regex(
            """(?:,\s*|\b)([A-Z0-9][A-Z0-9 .&*/_-]{1,45}?)\s+(?:IS\s*YERINDEN|IS\s*YERINDE)\b"""
        )
        p3.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val rawSlice = body.substring(r)
            val asciiSlice = asciiBody.substring(r)
            val prefixMatch = Regex("""^.*?(?:KARTINIZLA|KARTTAN|KARTINIZDAN|TARIHINDE|PIN\s+ILE|TLCARD\s+ILE)\s+""").find(asciiSlice)
            val stripped = if (prefixMatch != null) {
                rawSlice.substring(prefixMatch.range.last + 1)
            } else {
                rawSlice
            }
            val cleaned = cleanMerchantName(stripped)
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        // Pattern 4: "... KARTINIZLA <MERCHANT>'DA / 'DE / 'DEN <AMOUNT>" (TOMBank / Enpara / Papara)
        val p4 = Regex(
            """(?:KARTINIZLA|KART['’]INIZLA|KARTI['’]NIZLA)\s+(.+?)['’](?:DA|DE|TA|TE|DAN|DEN|TAN|TEN)\s+\d"""
        )
        p4.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        // Pattern 5: "<BRAND> FATURA ODEMENIZ" (e.g. "TURKCELL fatura ödemeniz")
        val p5 = Regex(
            """(?:KARTINIZDAN|KARTINIZLA|HESABINIZDAN)?\s*([A-Z0-9][A-Z0-9 .&-]{1,30}?)\s+FATURA\s+ODEME"""
        )
        p5.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        return "${bank.displayName} İşlemi" to true
    }

    private fun cleanMerchantName(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', ',', '.')
            .take(60)
    }
}
