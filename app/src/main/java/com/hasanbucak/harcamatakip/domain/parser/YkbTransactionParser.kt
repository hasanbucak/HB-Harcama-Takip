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
        "YAPIKREDI" to BankProvider.YAPI_KREDI,
        "YAPI KREDI" to BankProvider.YAPI_KREDI,
        "YAPI KREDI BANKASI" to BankProvider.YAPI_KREDI,
        "4440444" to BankProvider.YAPI_KREDI,
        "08502220444" to BankProvider.YAPI_KREDI,
        "4454" to BankProvider.YAPI_KREDI,
        "VAKIFBANK" to BankProvider.VAKIFBANK,
        "VAKIF BANK" to BankProvider.VAKIFBANK,
        "08502220724" to BankProvider.VAKIFBANK,
        "4440724" to BankProvider.VAKIFBANK,
        "5724" to BankProvider.VAKIFBANK,
        "TOMBANK" to BankProvider.TOM_BANK,
        "TOM BANK" to BankProvider.TOM_BANK,
        "TOMKATILIM" to BankProvider.TOM_BANK,
        "TOM KATILIM" to BankProvider.TOM_BANK,
        "HADI" to BankProvider.TOM_BANK,
        "HADI BANK" to BankProvider.TOM_BANK,
        "TOM BANK HADI" to BankProvider.TOM_BANK,
        "08503993636" to BankProvider.TOM_BANK,
        "GARANTI" to BankProvider.GARANTI,
        "GARANTIBBVA" to BankProvider.GARANTI,
        "GARANTI BBVA" to BankProvider.GARANTI,
        "4440333" to BankProvider.GARANTI,
        "3340" to BankProvider.GARANTI,
        "ISBANK" to BankProvider.IS_BANKASI,
        "ISBANKASI" to BankProvider.IS_BANKASI,
        "IS BANKASI" to BankProvider.IS_BANKASI,
        "TURKIYE IS BANKASI" to BankProvider.IS_BANKASI,
        "MAXIMUM" to BankProvider.IS_BANKASI,
        "08507240724" to BankProvider.IS_BANKASI,
        "4402" to BankProvider.IS_BANKASI,
        "AKBANK" to BankProvider.AKBANK,
        "AXESS" to BankProvider.AKBANK,
        "JUZDAN" to BankProvider.AKBANK,
        "4442525" to BankProvider.AKBANK,
        "4566" to BankProvider.AKBANK,
        "ZIRAAT" to BankProvider.ZIRAAT,
        "ZIRAATBANK" to BankProvider.ZIRAAT,
        "ZIRAAT BANKASI" to BankProvider.ZIRAAT,
        "BANKKART" to BankProvider.ZIRAAT,
        "4440000" to BankProvider.ZIRAAT,
        "4747" to BankProvider.ZIRAAT,
        "ENPARA" to BankProvider.ENPARA,
        "ENPARA COM" to BankProvider.ENPARA,
        "ENPARABANK" to BankProvider.ENPARA,
        "08502223663" to BankProvider.ENPARA,
        "QNB" to BankProvider.QNB,
        "FINANSBANK" to BankProvider.QNB,
        "QNB FINANSBANK" to BankProvider.QNB,
        "CARDFINANS" to BankProvider.QNB,
        "08502220900" to BankProvider.QNB,
        "2273" to BankProvider.QNB,
        "KUVEYTTURK" to BankProvider.KUVEYT_TURK,
        "KUVEYT TURK" to BankProvider.KUVEYT_TURK,
        "SAGLAMKART" to BankProvider.KUVEYT_TURK,
        "4440123" to BankProvider.KUVEYT_TURK,
        "TEB" to BankProvider.TEB,
        "CEPTETEB" to BankProvider.TEB,
        "08502000666" to BankProvider.TEB,
        "4600" to BankProvider.TEB,
        "DENIZBANK" to BankProvider.DENIZBANK,
        "DENIZ BANK" to BankProvider.DENIZBANK,
        "MOBILDENIZ" to BankProvider.DENIZBANK,
        "08502220800" to BankProvider.DENIZBANK,
        "3280" to BankProvider.DENIZBANK,
        "PAPARA" to BankProvider.PAPARA,
        "08503400340" to BankProvider.PAPARA
    )

    // Sentence splitter that NEVER cuts thousands separators like 1.450,00 or 84.354,50
    private val SENTENCE_SPLIT_REGEX = Regex("""(?:(?<!\d)\.(?!\d)|[!;\n])+""")

    // Strict completed past-tense verbs (rejects "YAPANA", "YAPIN", "EDILMESI")
    private val COMPLETED_REFUND_REGEX = Regex(
        """\b(?:IADE(?:NIZ|SI|LERI)?\s+(?:YAPIL(?:DI|MIS)\w*|EDIL(?:DI|MIS)\w*|GERCEKLES(?:TI|MIS)\w*|YATIRIL(?:DI|MIS)\w*)|IADE\s+TUTARI\b.{0,30}\bYATIRIL(?:DI|MIS)\w*|IADE\s+ISLEMI\s+(?:YAPIL(?:DI|MIS)\w*|GERCEKLES\w*)|IPTAL\s+EDIL(?:DI|MIS)\w*|IPTAL\s+ISLEMI\s+(?:YAPIL(?:DI|MIS)\w*|GERCEKLES\w*))\b"""
    )

    private val COMPLETED_TX_VERB_REGEX = Regex(
        """\b(?:HARCAMA\s+(?:YAP(?:TI|TIGINIZ|ILDI|ILMIS)\w*|GERCEKLES(?:TI|MIS|TIRILDI|TIRILMIS)\w*)|ALISVERIS\s+(?:YAP(?:TI|TIGINIZ|ILDI|ILMIS)\w*|GERCEKLES\w*)|ISLEM\s+(?:GERCEKLES(?:TI|MIS|TIRILDI|TIRILMIS)\w*|YAPIL(?:DI|MIS)\w*)|ODEME\s+(?:YAP(?:TI|ILDI|ILMIS)\w*|GERCEKLES(?:TI|MIS|TIRILDI|TIRILMIS)\w*)|FATURA\s+ODEME[A-Z]*\s+ALIN(?:DI|MIS)\w*|CEKIM\s+YAPIL(?:DI|MIS)\w*|PROVIZYON\s+ALIN(?:DI|MIS)\w*)\b"""
    )

    private val DECLINED_TX_REGEX = Regex(
        """\b(?:GERCEKLESTIRILEME\w*|GERCEKLESME(?:DI|MIS)\w*|YAPILAMA\w*|TAMAMLANAMA\w*|REDDEDIL\w*|BASARISIZ\w*|ONAYLANMA(?:DI|MIS)\w*|YETERSIZ\s+LIMIT|YETERSIZ\s+BAKIYE|LIMIT\s+ASIM\w*|HATALI\s+SIFRE|HATALI\s+PIN)\b"""
    )

    private val CARD_DEBT_OR_STATEMENT_REGEX = Regex(
        """\b(?:(?:KREDI\s+KART\w*\s+|KART\w*\s+)?BORC\w*\s+ODEME\w*|EKSTRE\s+ODEME\w*|HESABINIZDAN\s+KARTINIZA|DONEM\s+BORC\w*|ASGARI\s+ODEME|EKSTRENIZ\s+KESIL\w*|HESAP\s+OZETINIZ|SON\s+ODEME\s+TARIHI|KART\s+BORCUNUZ)\b"""
    )

    private val IBAN_TRANSFER_REGEX = Regex(
        """(?:\bFAST\s+ILE\b|\bFAST\s+ISLEM\w*\b|\bEFT\b(?![-/ ]?POS)|\bHAVALE\b|\bIBAN\b|\bPARA\s+TRANSFER\w*\b|\bHESABINIZA\s+.{0,40}\b(?:GELMISTIR|YATIRILMISTIR|AKTARILMISTIR)\b)"""
    )

    private val PROMO_OFFER_REGEX = Regex(
        """\b(?:UZERI\s+(?:ALISVERIS|HARCAMA)\w*|HARCAMA\s+YAPANA|ALISVERISINIZE|ALISVERISLERINIZE|HARCAMANIZA|HARCAMALARINIZA|IADE\s+FIRSAT\w*|KAZANMA\s+FIRSAT\w*|TAKSIT\s+FIRSAT\w*|FIRSATI\s+KACIRMA\w*|KATILMAK\s+ICIN|HEMEN\s+TIKLA\w*|FAIZLI\s+ESNEK\s+HESAP|LIMITINIZI\s+HEMEN|RET\s+YAZ\w*|MERSIS)\b"""
    )

    private val DIRECT_OTP_PROMPT_REGEX = Regex(
        """\b(?:TEK\s+KULLANIMLIK|DOGRULAMA\s+KOD[A-Z]*|GUVENLIK\s+KOD[A-Z]*|AKILLI\s+SIFRE|MOBIL\s+GIRIS\s+SIFRE[A-Z]*|GIRIS\s+KOD[A-Z]*|OTP)\b"""
    )

    // Matches "3D SIFRESI: 583921", "SIFRENIZ: 583921", "KODU: 4829", and "583921 KODUNU / SIFRESINI"
    // Excludes POS receipt "ONAY KODU: 123456" unless accompanied by OTP instruction or lacking completed spend
    private val EXPLICIT_SECRET_CODE_REGEX = Regex(
        """\b(?:3D\s+(?:SECURE\s+)?SIFRE[A-Z]*|SIFRE[A-Z]*|PAROLA[A-Z]*|DOGRULAMA\s+KOD[A-Z]*|GUVENLIK\s+KOD[A-Z]*|(?:^|(?<!ONAY\s))KOD[A-Z]*|OTP)\s*[:=]?\s*\d{4,8}\b|\b\d{4,8}\s+(?:NO['’]?LU\s+)?(?:DOGRULAMA\s+|ONAY\s+|GUVENLIK\s+)?(?:KOD|SIFRE|PAROLA)[A-Z]*\b"""
    )

    private val POS_APPROVAL_CODE_REGEX = Regex("""\bONAY\s+KOD[A-Z]*\s*[:=]?\s*\d{4,8}\b""")

    private val OTP_INSTRUCTION_VERB_REGEX = Regex(
        """\b(?:PAYLASMA[A-Z]*|KIMSEYLE|GIRINIZ|GIREREK|ONAYLAYIN[A-Z]*|DOGRULAYIN[A-Z]*|GECERLIDIR)\b"""
    )

    private val CARD_PATTERNS: List<Regex> = listOf(
        // 4543****1234, 454312******6251, 4543-12**-****-6251, ****6251, •••• 6251
        Regex("""(?:\b\d{4,8}[\s*•.xX\d-]{0,6}[*•xX][\s*•.xX-]{1,10}|(?:^|[\s(])[*•.xX]{2,10}\s*)(\d{4})\b"""),
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

    // Uses non-number-dot sentence stop so "84.354,50 TL" is removed whole without leaving "354,50 TL"
    private const val CLAUSE_TAIL = """(?:(?!(?<!\d)\.(?!\d)|[!;\n]).)*"""
    private val NOISE_CLAUSE_PATTERNS: List<Regex> = listOf(
        Regex("""\b(?:GUNCEL\s+)?KULLANILABILIR\s+(?:LIMIT|BAKIYE)\b$CLAUSE_TAIL"""),
        Regex("""\bKALAN\s+(?:LIMIT|BAKIYE)\b$CLAUSE_TAIL"""),
        Regex("""\b(?:LIMITINIZ|LIMIT|BAKIYENIZ|BAKIYE)\s*:?\s*(?:TL|TRY|USD|EUR|GBP|₺|\$|€)?\s*\d[\d.,]*\s*(?:TL|TRY|USD|EUR|GBP|₺|\$|€)?$CLAUSE_TAIL"""),
        Regex("""\bBU\s+ISLEMDEN\b$CLAUSE_TAIL\bPUAN\b$CLAUSE_TAIL"""),
        Regex("""\bTOPLAM\s+[A-Z-]*PUAN\b$CLAUSE_TAIL"""),
        Regex("""\bKAZANILAN\s+[A-Z-]*PUAN\b$CLAUSE_TAIL"""),
        Regex("""\b\d[\d.,]*\s*(?:TL\s*)?(?:WORLDPUAN|CHIP-PARA|CHIPPARA|BONUS|MAXIPUAN|BANKKART\s+LIRA|PARAFPARA)\s+KAZAN\w*$CLAUSE_TAIL"""),
        Regex("""\bONAY\s+KOD[A-Z]*\s*[:=]?\s*\d+\b""")
    )

    private val SUFFIX_AMOUNT_REGEX = Regex(
        """(?<![A-Z0-9*])(\d[\d.,]*)\s*(TL|TRY|USD|EUR|GBP|₺|\$|€)(?![A-Z0-9])"""
    )

    private val PREFIX_AMOUNT_REGEX = Regex(
        """(?<![A-Z0-9])(TL|TRY|USD|EUR|GBP|₺|\$|€)\s*(\d[\d.,]*)(?![A-Z0-9])"""
    )

    private val DUAL_CURRENCY_REGEX = Regex(
        """(?<![A-Z0-9*])(\d[\d.,]*)\s*(USD|EUR|GBP|\$|€)(?![A-Z0-9])\s*\(\s*(\d[\d.,]*)\s*(TL|TRY|₺)(?![A-Z0-9])\s*\)"""
    )

    private val PLUS_INSTALLMENT_REGEX = Regex("""\b(\d{1,2})\s*\+\s*(\d{1,2})\s*TAKSIT\w*\b""")
    private val COLON_INSTALLMENT_REGEX = Regex("""\bTAKSIT\s*(?:SAYISI|ADEDI)?\s*:\s*(\d{1,2})\b""")
    private val SIMPLE_INSTALLMENT_REGEX = Regex("""\b(\d{1,2})\s*TAKSIT\w*\b""")

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

    private fun hasCompletedTxOrRefund(asciiText: String): Boolean {
        return COMPLETED_TX_VERB_REGEX.containsMatchIn(asciiText) ||
            COMPLETED_REFUND_REGEX.containsMatchIn(asciiText)
    }

    fun isOtpOrSecurityText(asciiUpper: String): Boolean {
        // Any explicit secret code pattern (e.g. "3D şifresi: 583921", "Kod: 4829", "583921 kodunu") is ALWAYS an OTP
        if (EXPLICIT_SECRET_CODE_REGEX.containsMatchIn(asciiUpper)) {
            return true
        }
        val hasCompletedTx = hasCompletedTxOrRefund(asciiUpper)
        if (DIRECT_OTP_PROMPT_REGEX.containsMatchIn(asciiUpper)) {
            return true
        }
        if (POS_APPROVAL_CODE_REGEX.containsMatchIn(asciiUpper)) {
            if (!hasCompletedTx || OTP_INSTRUCTION_VERB_REGEX.containsMatchIn(asciiUpper)) {
                return true
            }
        }
        // Generic "Şifrenizi kimseyle paylaşmayınız" WITHOUT digits is only OTP when there's NO completed spend sentence!
        if (!hasCompletedTx && Regex("""\b(?:SIFRENIZ\w*|PAROLANIZ\w*|KODUNUZ\w*)\b""").containsMatchIn(asciiUpper)) {
            return true
        }
        return false
    }

    fun sanitizeForLog(text: String): String {
        if (text.isBlank()) return ""
        val ascii = CategoryClassifier.toAsciiUpper(text)
        if (isOtpOrSecurityText(ascii)) {
            return "[OTP / Güvenlik mesajı — içerik kaydedilmedi]"
        }
        return text
            .replace(
                Regex("""(?i)\b(kod\p{L}*|şifre\p{L}*|sifre\p{L}*|parola\p{L}*|pin|otp)\s*([:=]?)\s*\d{4,8}\b"""),
                "$1$2 ******"
            )
            .replace(
                Regex("""(?i)\b\d{4,8}\s+(kod\p{L}*|şifre\p{L}*|sifre\p{L}*|parola\p{L}*)\b"""),
                "****** $1"
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

        // 4. True sentence-level evaluation (never splitting inside numbers like 1.450,00)
        val sentences = asciiPrimary
            .split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val validTxSentences = sentences.filter { s ->
            val isRefundSentence = COMPLETED_REFUND_REGEX.containsMatchIn(s)
            val isSpendSentence = COMPLETED_TX_VERB_REGEX.containsMatchIn(s)
            if (!isRefundSentence && !isSpendSentence) return@filter false
            if (DECLINED_TX_REGEX.containsMatchIn(s)) return@filter false
            if (CARD_DEBT_OR_STATEMENT_REGEX.containsMatchIn(s)) return@filter false
            if (PROMO_OFFER_REGEX.containsMatchIn(s)) return@filter false
            if (!isRefundSentence && IBAN_TRANSFER_REGEX.containsMatchIn(s)) return@filter false
            true
        }

        if (validTxSentences.isEmpty()) {
            return when {
                DECLINED_TX_REGEX.containsMatchIn(asciiCombined) ->
                    ParseOutcome.Ignored("Başarısız veya reddedilen işlem bildirimi")
                CARD_DEBT_OR_STATEMENT_REGEX.containsMatchIn(asciiCombined) ->
                    ParseOutcome.Ignored("Kart borcu veya ekstre bilgilendirmesi")
                IBAN_TRANSFER_REGEX.containsMatchIn(asciiCombined) ->
                    ParseOutcome.Ignored("IBAN / para transferi (FAST/EFT/Havale) bildirimi")
                PROMO_OFFER_REGEX.containsMatchIn(asciiCombined) ->
                    ParseOutcome.Ignored("Kampanya veya tanıtım bildirimi")
                else ->
                    ParseOutcome.Ignored("Tamamlanmış kart harcama veya iade ifadesi bulunamadı")
            }
        }

        val joinedTxSentences = validTxSentences.joinToString(". ")

        // 5. Extract card last4 safely
        val cardLast4 = extractCardLast4(joinedTxSentences)
            ?: extractCardLast4(asciiCombined)
            ?: "GENEL"

        // 6. Extract amount from valid transaction sentence(s) FIRST so trailing debt/limit sentences never interfere!
        val sanitizedTxText = prepareTextForAmountExtraction(joinedTxSentences)
        val dualMatch = DUAL_CURRENCY_REGEX.find(sanitizedTxText)

        val amount: Double
        val currency: String
        val secondaryTl: Double?

        if (dualMatch != null) {
            amount = parseFlexibleAmount(dualMatch.groupValues[1])
                ?: return ParseOutcome.Unparsed("Döviz tutarı okunamadı")
            currency = normalizeCurrency(dualMatch.groupValues[2])
            secondaryTl = parseFlexibleAmount(dualMatch.groupValues[3])
        } else {
            val extracted = extractBestAmountAndCurrency(sanitizedTxText)
                ?: extractBestAmountAndCurrency(prepareTextForAmountExtraction(asciiPrimary))
                ?: return ParseOutcome.Unparsed("Tutar veya para birimi okunamadı")
            amount = extracted.first
            currency = extracted.second
            secondaryTl = null
        }

        if (amount <= 0.0) {
            return ParseOutcome.Unparsed("Geçersiz tutar: $amount")
        }

        val cardType = detectCardType(asciiCombined)
        val txType = detectTransactionType(joinedTxSentences)
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

    private fun detectTransactionType(txSentencesAscii: String): ParsedTransactionType {
        return if (COMPLETED_REFUND_REGEX.containsMatchIn(txSentencesAscii)) {
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
        val range: IntRange
    )

    fun extractAmountAndCurrency(text: String): Pair<Double, String>? {
        val ascii = prepareTextForAmountExtraction(CategoryClassifier.toAsciiUpper(text))
        return extractBestAmountAndCurrency(ascii)
    }

    private fun extractBestAmountAndCurrency(cleanedAscii: String): Pair<Double, String>? {
        val suffixCandidates = mutableListOf<AmountCandidate>()

        for (m in SUFFIX_AMOUNT_REGEX.findAll(cleanedAscii)) {
            val parsed = parseFlexibleAmount(m.groupValues[1])
            if (parsed != null && parsed > 0.0) {
                suffixCandidates.add(
                    AmountCandidate(parsed, normalizeCurrency(m.groupValues[2]), m.range)
                )
            }
        }

        val allCandidates = ArrayList<AmountCandidate>(suffixCandidates)
        for (m in PREFIX_AMOUNT_REGEX.findAll(cleanedAscii)) {
            // Check true character-range overlap so "5.000,00 TL 3 taksit" never matches "TL 3"!
            val overlapsSuffix = suffixCandidates.any { s ->
                m.range.first <= s.range.last && m.range.last >= s.range.first
            }
            if (!overlapsSuffix) {
                val parsed = parseFlexibleAmount(m.groupValues[2])
                if (parsed != null && parsed > 0.0) {
                    allCandidates.add(
                        AmountCandidate(parsed, normalizeCurrency(m.groupValues[1]), m.range)
                    )
                }
            }
        }

        if (allCandidates.isEmpty()) return null
        if (allCandidates.size == 1) return allCandidates[0].amount to allCandidates[0].currency

        val verbPos = (COMPLETED_TX_VERB_REGEX.find(cleanedAscii)
            ?: COMPLETED_REFUND_REGEX.find(cleanedAscii))?.range?.first

        val chosen = if (verbPos != null) {
            allCandidates.minByOrNull { kotlin.math.abs(it.range.first - verbPos) } ?: allCandidates.first()
        } else {
            allCandidates.first()
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

    private fun extractMerchant(
        title: String,
        body: String,
        bank: BankProvider
    ): Pair<String, Boolean> {
        val asciiBody = CategoryClassifier.toAsciiUpper(body)

        val p1 = Regex(
            """(?:TARIHINDE|\d{2}:\d{2}['’]?(?:DA|DE|TA|TE)?(?:\s+TARIHINDE)?)\s+(.+?)\s+(?:IS\s*YERINDEN|IS\s*YERINDE|FIRMASINDAN|FIRMASINDA|UYE\s*ISYERINDEN)"""
        )
        p1.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        val p2 = Regex(
            """(?:KARTINIZLA|KART'INIZLA|KART’INIZLA|KARTINIZA|KARTINIZDAN|KARTTAN|KARTINIZ\s+ILE|\(\d{4}\)\s*ILE|BITEN\s+[\w'’]+\s+ILE|BITEN\s+KARTINIZLA|TLCARD\s+ILE)\s+(?:PIN\s+ILE\s+)?(?:\d{2}[./]\d{2}[./]\d{4}(?:\s+\d{2}:\d{2}['’]?(?:DA|DE|TA|TE)?)?\s*(?:TARIHINDE\s+)?)?(.+?)\s+(?:IS\s*YERINDEN|IS\s*YERINDE|FIRMASINDAN|FIRMASINDA)"""
        )
        p2.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

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

        val p4 = Regex(
            """(?:KARTINIZLA|KART['’]INIZLA|KARTI['’]NIZLA)\s+(.+?)['’](?:DA|DE|TA|TE|DAN|DEN|TAN|TEN)\s+\d"""
        )
        p4.find(asciiBody)?.groups?.get(1)?.range?.let { r ->
            val cleaned = cleanMerchantName(body.substring(r))
            if (cleaned.isNotEmpty()) return cleaned to false
        }

        // Pattern 5: "<BRAND> FATURA ODEMENIZ" (requiring preceding boundary/card word so card digits aren't included)
        val p5 = Regex(
            """(?:(?:KARTINIZDAN|KARTINIZLA|HESABINIZDAN)\s+|^)([A-Z][A-Z0-9 .&-]{1,28}?)\s+FATURA\s+ODEME"""
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
