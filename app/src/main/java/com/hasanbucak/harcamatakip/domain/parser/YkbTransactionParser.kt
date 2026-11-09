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
    OTHER("OTHER", "Diğer", 0xFF475569);

    companion object {
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
    val amount: Double,
    val currency: String,
    val transactionType: ParsedTransactionType,
    val installmentCount: Int
)

sealed class ParseOutcome {
    data class Success(val transaction: ParsedTransaction) : ParseOutcome()
    data class Ignored(val reason: String) : ParseOutcome()
    data class Unparsed(val reason: String) : ParseOutcome()
}

object YkbTransactionParser {

    const val VAKIFBANK_ALLOWED_LAST4 = "6251"

    private val DIRECT_BANK_PACKAGES: Map<String, BankProvider> = mapOf(
        "com.ykb.android" to BankProvider.YAPI_KREDI,
        "com.ykb.avm" to BankProvider.YAPI_KREDI,
        "com.vakifbank.mobile" to BankProvider.VAKIFBANK,
        "com.vakifbank.ceptekazan" to BankProvider.VAKIFBANK,
        "com.tom.hadi" to BankProvider.TOM_BANK
    )

    val MESSAGING_PACKAGES: Set<String> = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.miui.mms"
    )

    private val YKB_SMS_SENDERS = listOf(
        "YAPIKREDI", "YAPI KREDI", "WORLDCARD", "4440444", "4454"
    )
    private val VAKIF_SMS_SENDERS = listOf(
        "VAKIFBANK", "VAKIF BANK", "08502220724", "4440724", "5724"
    )
    private val TOMBANK_SMS_SENDERS = listOf(
        "TOMBANK", "TOM BANK", "TOMKATILIM", "TOM KATILIM", "HADI", "08503993636"
    )

    private val NON_TRANSACTION_KEYWORDS: List<Pair<String, String>> = listOf(
        "SIFRENIZ" to "Tek kullanımlık şifre / OTP mesajı",
        "SIFREYI KIMSEYLE" to "Güvenlik şifresi mesajı",
        "DOGRULAMA KODU" to "Doğrulama kodu mesajı",
        "ONAY KODU" to "Onay kodu mesajı",
        "GUVENLIK KODU" to "Güvenlik kodu mesajı",
        "3D SECURE SIFRE" to "3D Secure onay şifresi",
        "AKILLI SIFRE" to "Akıllı Şifre mesajı",
        "MOBIL GIRIS" to "Giriş bilgilendirmesi",
        "KAMPANYA" to "Kampanya / tanıtım bildirimi",
        "KATILMAK ICIN" to "Kampanya katılım bildirimi",
        "FIRSATI KACIRMA" to "Kampanya bildirimi",
        "EKSTRENIZ KESILMISTIR" to "Ekstre kesim bilgilendirmesi",
        "HESAP OZETINIZ" to "Ekstre bilgilendirmesi",
        "SON ODEME TARIHI" to "Ekstre ödeme hatırlatması",
        "ASGARI ODEME" to "Ekstre ödeme hatırlatması",
        "KART BORCUNUZ" to "Kart borç bilgilendirmesi"
    )

    private val TRANSACTION_VERBS: List<String> = listOf(
        "HARCAMA",
        "ALISVERIS",
        "ISLEM GERCEKLESTIRIL",
        "ISLEM YAPIL",
        "TUTARINDA ISLEM",
        "ODEME YAPIL",
        "ODEME GERCEKLESTI",
        "CEKIM YAPIL",
        "PROVIZYON",
        "IADE",
        "IPTAL EDILMISTIR",
        "IPTAL ISLEMI"
    )

    private val CARD_PATTERNS: List<Regex> = listOf(
        // 4543****1234, 5400********6251, ****6251, •••• 6251, *6251, ..6251
        Regex("""(?:\d{4}[\s*•.xX]{2,10}|[*•.xX]{2,10}\s*|\*)(\d{4})\b"""),
        // 6251 nolu / numarali / ile biten / son haneli
        Regex("""\b(\d{4})\s*(?:NO'?LU|NOLU|NUMARALI|ILE BITEN|SON HANELI)\b"""),
        // son 4 hanesi 6251 / sonu 6251
        Regex("""(?:SON\s*4\s*HANESI|SONU)\s*(\d{4})\b"""),
        // Dijital Kartiniz (1234) / Kart (6251)
        Regex("""KART[A-Z']*\s*\(\s*(\d{4})\s*\)""")
    )

    private val AMOUNT_FULL_TOKEN_REGEX = Regex(
        """(\d[\d.,]*)\s*(TL|TRY|₺|USD|EUR|GBP|\$|€)""",
        RegexOption.IGNORE_CASE
    )

    private val INSTALLMENT_REGEX = Regex("""(\d{1,2})\s*TAKSIT""", RegexOption.IGNORE_CASE)

    private val TRAILING_NOISE_PATTERNS: List<Regex> = listOf(
        Regex("""GUNCEL\s+KULLANILABILIR\s+LIMIT.*$"""),
        Regex("""KULLANILABILIR\s+LIMIT.*$"""),
        Regex("""KALAN\s+LIMIT.*$"""),
        Regex("""KULLANILABILIR\s+BAKIYE.*$"""),
        Regex("""KALAN\s+BAKIYE.*$"""),
        Regex("""TOPLAM\s+PUAN.*$"""),
        Regex("""WORLDPUAN\s+KAZAN.*$"""),
        Regex("""KAZANILAN\s+PUAN.*$"""),
        Regex("""NAKITE\s+CEVIR.*$""")
    )

    fun resolveBankForPackage(packageName: String): BankProvider? {
        return DIRECT_BANK_PACKAGES[packageName]
    }

    fun resolveBankFromSmsSender(senderOrTitle: String?): BankProvider? {
        if (senderOrTitle.isNullOrBlank()) return null
        val upper = CategoryClassifier.toAsciiUpper(senderOrTitle.trim())
        return when {
            VAKIF_SMS_SENDERS.any { upper.contains(it) } -> BankProvider.VAKIFBANK
            TOMBANK_SMS_SENDERS.any {
                if (it == "HADI") {
                    upper == "HADI" || upper.startsWith("HADI ") || upper.endsWith(" HADI")
                } else {
                    upper.contains(it)
                }
            } -> BankProvider.TOM_BANK
            YKB_SMS_SENDERS.any { upper.contains(it) } -> BankProvider.YAPI_KREDI
            else -> null
        }
    }

    fun isSupportedSmsSender(sender: String?): Boolean {
        return resolveBankFromSmsSender(sender) != null
    }

    /**
     * Masks 5-8 digit standalone verification codes if an OTP message arrives,
     * ensuring one-time passwords are never persisted in raw logs.
     */
    fun sanitizeForLog(text: String): String {
        val upper = CategoryClassifier.toAsciiUpper(text)
        val looksLikeOtp = upper.contains("SIFRE") ||
            upper.contains("KODU") ||
            upper.contains("3D SECURE")
        if (!looksLikeOtp) return text
        return text.replace(Regex("""\b\d{5,8}\b"""), "******")
    }

    fun parse(
        title: String?,
        body: String?,
        bankHint: BankProvider = BankProvider.YAPI_KREDI
    ): ParseOutcome {
        val cleanTitle = title?.trim().orEmpty()
        val cleanBody = body?.trim().orEmpty()
        if (cleanBody.isEmpty() && cleanTitle.isEmpty()) {
            return ParseOutcome.Ignored("Boş bildirim içeriği")
        }

        val combined = if (cleanTitle.isNotEmpty() && !cleanBody.startsWith(cleanTitle, ignoreCase = true)) {
            "$cleanTitle - $cleanBody"
        } else {
            cleanBody.ifEmpty { cleanTitle }
        }

        val asciiCombined = CategoryClassifier.toAsciiUpper(combined)

        val resolvedBank = resolveBankFromTextIfNeeded(asciiCombined, bankHint)

        for ((keyword, reason) in NON_TRANSACTION_KEYWORDS) {
            if (asciiCombined.contains(keyword)) {
                return ParseOutcome.Ignored(reason)
            }
        }

        val hasTransactionSignal = TRANSACTION_VERBS.any { asciiCombined.contains(it) }
        if (!hasTransactionSignal) {
            return ParseOutcome.Ignored("Harcama veya iade ifadesi bulunamadı")
        }

        val extractedLast4 = extractCardLast4(asciiCombined)
        val cardLast4 = when {
            extractedLast4 != null -> extractedLast4
            resolvedBank == BankProvider.VAKIFBANK &&
                Regex("""\b$VAKIFBANK_ALLOWED_LAST4\b""").containsMatchIn(asciiCombined) -> VAKIFBANK_ALLOWED_LAST4
            else -> "GENEL"
        }

        // Strict VakifBank filter: ONLY card ending in 6251 is tracked
        if (resolvedBank == BankProvider.VAKIFBANK && cardLast4 != VAKIFBANK_ALLOWED_LAST4) {
            return ParseOutcome.Ignored(
                "VakıfBank için yalnızca $VAKIFBANK_ALLOWED_LAST4 ile biten kart takip ediliyor (gelen: $cardLast4)"
            )
        }

        val cleanedBodyForAmount = stripTrailingLimitAndPoints(cleanBody.ifEmpty { cleanTitle })
        val amountPair = extractAmountAndCurrency(cleanedBodyForAmount)
            ?: extractAmountAndCurrency(combined)
            ?: return ParseOutcome.Unparsed("Tutar veya para birimi okunamadı")

        val (amount, currency) = amountPair
        if (amount <= 0.0) {
            return ParseOutcome.Unparsed("Geçersiz tutar: $amount")
        }

        val cardType = detectCardType(asciiCombined)
        val txType = detectTransactionType(asciiCombined)
        val installments = extractInstallments(asciiCombined)
        val merchant = extractMerchant(cleanTitle, cleanBody.ifEmpty { cleanTitle }, resolvedBank)

        return ParseOutcome.Success(
            ParsedTransaction(
                bank = resolvedBank,
                cardLast4 = cardLast4,
                detectedCardType = cardType,
                merchant = merchant,
                amount = amount,
                currency = currency,
                transactionType = txType,
                installmentCount = installments
            )
        )
    }

    private fun resolveBankFromTextIfNeeded(asciiText: String, hint: BankProvider): BankProvider {
        if (hint != BankProvider.OTHER) {
            // Still allow explicit bank signature inside manual test text to override default hint
            return when {
                asciiText.contains("VAKIFBANK") || asciiText.contains("BANKOMAT") -> BankProvider.VAKIFBANK
                asciiText.contains("TOMBANK") || asciiText.contains("TOM BANK") ||
                    asciiText.contains("TOM KATILIM") || asciiText.contains("HADI KREDI") ||
                    asciiText.contains("HADI KART") -> BankProvider.TOM_BANK
                else -> hint
            }
        }
        return when {
            asciiText.contains("VAKIFBANK") || asciiText.contains("BANKOMAT") -> BankProvider.VAKIFBANK
            asciiText.contains("TOMBANK") || asciiText.contains("TOM BANK") ||
                asciiText.contains("HADI") -> BankProvider.TOM_BANK
            else -> BankProvider.YAPI_KREDI
        }
    }

    private fun stripTrailingLimitAndPoints(text: String): String {
        val sentenceParts = text.split(Regex("""(?<=[.!])\s+"""))
        val kept = sentenceParts.filter { sentence ->
            val ascii = CategoryClassifier.toAsciiUpper(sentence)
            TRAILING_NOISE_PATTERNS.none { it.containsMatchIn(ascii) }
        }
        return if (kept.isNotEmpty()) kept.joinToString(" ") else text
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
            asciiText.contains("KREDI KART") ||
                asciiText.contains("WORLDCARD") -> DetectedCardType.CREDIT
            asciiText.contains("TLCARD") ||
                asciiText.contains("BANKOMAT") ||
                asciiText.contains("BANKA KART") ||
                asciiText.contains("HADI KART") -> DetectedCardType.DEBIT
            else -> DetectedCardType.CREDIT
        }
    }

    private fun detectTransactionType(asciiText: String): ParsedTransactionType {
        return if (
            asciiText.contains("IADE ISLEMI") ||
            asciiText.contains("IADE EDILMISTIR") ||
            asciiText.contains("IADE YAPILMISTIR") ||
            asciiText.contains("IPTAL EDILMISTIR") ||
            asciiText.contains("IPTAL ISLEMI") ||
            Regex("""\bIADE\b""").containsMatchIn(asciiText)
        ) {
            ParsedTransactionType.REFUND
        } else {
            ParsedTransactionType.EXPENSE
        }
    }

    private fun extractInstallments(asciiText: String): Int {
        val match = INSTALLMENT_REGEX.find(asciiText) ?: return 1
        val count = match.groupValues[1].toIntOrNull() ?: 1
        return count.coerceIn(1, 36)
    }

    fun extractAmountAndCurrency(text: String): Pair<Double, String>? {
        val match = AMOUNT_FULL_TOKEN_REGEX.find(text) ?: return null
        val rawNumber = match.groupValues[1]
        val rawCurrency = match.groupValues[2]
        val amount = parseFlexibleAmount(rawNumber) ?: return null
        val currency = normalizeCurrency(rawCurrency)
        return amount to currency
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

    private fun extractMerchant(title: String, body: String, bank: BankProvider): String {
        // Pattern 1: "... tarihinde / 'te / 'de <MERCHANT> işyerinden / işyerinde / firmasında ..."
        val afterDateRegex = Regex(
            """(?i)(?:tarihinde|\d{2}:\d{2}['’]?(?:da|de|ta|te)?(?:\s+tarihinde)?)\s+(.+?)\s+(?:iş\s*yerinden|is\s*yerinden|iş\s*yerinde|is\s*yerinde|firmasından|firmasindan|üye\s*işyerinden|uye\s*isyerinden)"""
        )
        afterDateRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val cleaned = cleanMerchantName(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 2: "... kartınızla / kartınıza / kartınız ile / (1234) ile <MERCHANT> işyerinden / işyerinde ..."
        val afterCardRegex = Regex(
            """(?i)(?:kartınızla|kartinizla|kart'ınızla|kart'inizla|kartınıza|kartiniza|kartınız\s+ile|kartiniz\s+ile|\(\d{4}\)\s*ile|olan\s+kartınızla|olan\s+kartinizla|biten\s+[\w'’]+\s+ile|biten\s+kartınızla|biten\s+kartinizla)\s+(?:\d{2}[./]\d{2}[./]\d{4}(?:\s+\d{2}:\d{2}['’]?(?:da|de|ta|te)?)?\s*(?:tarihinde\s+)?)?(.+?)\s+(?:iş\s*yerinden|is\s*yerinden|iş\s*yerinde|is\s*yerinde|firmasından|firmasindan|firmasında|firmasinda)"""
        )
        afterCardRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val cleaned = cleanMerchantName(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 3: "<MERCHANT> işyerinden / işyerinde <AMOUNT>"
        val beforeIsyeriRegex = Regex(
            """(?i)(?:,\s*|\b)([A-ZÇĞİÖŞÜ0-9][A-ZÇĞİÖŞÜa-zçğıöşü0-9 .&*/_-]{1,45}?)\s+(?:iş\s*yerinden|is\s*yerinden|iş\s*yerinde|is\s*yerinde)\b"""
        )
        beforeIsyeriRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val stripped = candidate
                .replace(Regex("""(?i)^.*?(?:kartınızla|kartinizla|kart'ınızla|tarihinde)\s+"""), "")
            val cleaned = cleanMerchantName(stripped)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 4: "... kartınızla / Kart'ınızla <MERCHANT>'da / 'de / 'den / 'dan <AMOUNT> TL" (common in TOMBank / Hadi)
        val apostropheRegex = Regex(
            """(?i)(?:kartınızla|kartinizla|kart['’]ınızla|kart['’]inizla|kartı['’]nızla|karti['’]nizla)\s+(.+?)['’](?:da|de|ta|te|dan|den|tan|ten)\s+\d"""
        )
        apostropheRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val cleaned = cleanMerchantName(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 5: Useful non-generic notification title
        val genericTitles = setOf(
            "YAPI KREDI", "YAPI KREDI MOBIL", "WORLD MOBIL", "WORLDCARD", "YAPIKREDI",
            "VAKIFBANK", "VAKIFBANK MOBIL", "TOM BANK", "TOMBANK", "TOM BANK HADI", "HADI",
            "HARCAMA BILGISI", "HARCAMA BILDIRIMI", "ISLEM BILGISI", "BILGILENDIRME"
        )
        val asciiTitle = CategoryClassifier.toAsciiUpper(title)
        if (title.isNotBlank() && asciiTitle !in genericTitles && title.length in 2..40) {
            return cleanMerchantName(title)
        }

        return "${bank.displayName} İşlemi"
    }

    private fun cleanMerchantName(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', ',', '.')
            .take(60)
    }
}
