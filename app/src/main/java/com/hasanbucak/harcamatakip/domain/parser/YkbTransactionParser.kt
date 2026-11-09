package com.hasanbucak.harcamatakip.domain.parser

import java.util.Locale

enum class ParsedTransactionType {
    EXPENSE,
    REFUND
}

enum class DetectedCardType {
    CREDIT,
    VIRTUAL,
    DEBIT
}

data class ParsedTransaction(
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

    val SUPPORTED_PACKAGES: Set<String> = setOf(
        "com.ykb.android", // Yapi Kredi Mobil
        "com.ykb.avm"      // World Mobil
    )

    private val SUPPORTED_SMS_SENDERS: List<String> = listOf(
        "YAPIKREDI",
        "YAPI KREDI",
        "WORLDCARD",
        "WORLD",
        "4440444",
        "4454"
    )

    private val NON_TRANSACTION_KEYWORDS: List<Pair<String, String>> = listOf(
        "SIFRENIZ" to "Tek kullanımlık şifre / OTP mesajı",
        "SIFREYI KIMSEYLE" to "Güvenlik şifresi mesajı",
        "DOGRULAMA KODU" to "Doğrulama kodu mesajı",
        "ONAY KODU" to "Onay kodu mesajı",
        "3D SECURE SIFRE" to "3D Secure onay şifresi",
        "AKILLI SIFRE" to "Akıllı Şifre mesajı",
        "MOBIL GIRIS" to "Giriş bilgilendirmesi",
        "KAMPANYA" to "Kampanya / tanıtım bildirimi",
        "KATILMAK ICIN" to "Kampanya katılım bildirimi",
        "FIRSATI KACIRMA" to "Kampanya bildirimi",
        "EKSTRENIZ KESILMISTIR" to "Ekstre kesim bilgilendirmesi",
        "SON ODEME TARIHI" to "Ekstre ödeme hatırlatması",
        "ASGARI ODEME" to "Ekstre ödeme hatırlatması"
    )

    private val TRANSACTION_VERBS: List<String> = listOf(
        "HARCAMA",
        "ALISVERIS",
        "ISLEM GERCEKLESTIRIL",
        "ISLEM YAPIL",
        "TUTARINDA ISLEM",
        "PROVIZYON",
        "IADE",
        "IPTAL EDILMISTIR",
        "IPTAL ISLEMI"
    )

    private val CARD_PATTERNS: List<Regex> = listOf(
        // 4543****1234, 4543********1234, ****1234, •••• 1234, *1234
        Regex("""(?:\d{4}[\s*•xX]{2,10}|[*•xX]{1,10}\s*)(\d{4})\b"""),
        // 1234 nolu / numarali / ile biten kart
        Regex("""\b(\d{4})\s*(?:NO'?LU|NOLU|NUMARALI|ILE BITEN)\b"""),
        // son 4 hanesi 1234
        Regex("""SON\s*4\s*HANESI\s*(\d{4})\b"""),
        // Dijital Kartiniz (1234) / Kart (1234)
        Regex("""KART[A-Z']*\s*\(\s*(\d{4})\s*\)""")
    )

    private val AMOUNT_REGEX = Regex(
        """(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,](\d{1,2}))?\s*(TL|TRY|₺|USD|EUR|GBP|\$|€)\b""",
        RegexOption.IGNORE_CASE
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
        Regex("""TOPLAM\s+PUAN.*$"""),
        Regex("""WORLDPUAN\s+KAZAN.*$"""),
        Regex("""KAZANILAN\s+PUAN.*$""")
    )

    fun isSupportedSmsSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val normalized = CategoryClassifier.toAsciiUpper(sender)
        return SUPPORTED_SMS_SENDERS.any { normalized.contains(it) }
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

    fun parse(title: String?, body: String?): ParseOutcome {
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

        for ((keyword, reason) in NON_TRANSACTION_KEYWORDS) {
            if (asciiCombined.contains(keyword)) {
                return ParseOutcome.Ignored(reason)
            }
        }

        val hasTransactionSignal = TRANSACTION_VERBS.any { asciiCombined.contains(it) }
        if (!hasTransactionSignal) {
            return ParseOutcome.Ignored("Harcama veya iade ifadesi bulunamadı")
        }

        val cleanedBodyForAmount = stripTrailingLimitAndPoints(cleanBody.ifEmpty { cleanTitle })
        val amountPair = extractAmountAndCurrency(cleanedBodyForAmount)
            ?: extractAmountAndCurrency(combined)
            ?: return ParseOutcome.Unparsed("Tutar veya para birimi okunamadı")

        val (amount, currency) = amountPair
        if (amount <= 0.0) {
            return ParseOutcome.Unparsed("Geçersiz tutar: $amount")
        }

        val cardLast4 = extractCardLast4(asciiCombined) ?: "GENEL"
        val cardType = detectCardType(asciiCombined)
        val txType = detectTransactionType(asciiCombined)
        val installments = extractInstallments(asciiCombined)
        val merchant = extractMerchant(cleanTitle, cleanBody.ifEmpty { cleanTitle })

        return ParseOutcome.Success(
            ParsedTransaction(
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

    private fun stripTrailingLimitAndPoints(text: String): String {
        // Keep original indices aligned by matching on ASCII-upper version of equal length
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
            asciiText.contains("DIJITAL KART") || asciiText.contains("SANAL KART") -> DetectedCardType.VIRTUAL
            asciiText.contains("TLCARD") || asciiText.contains("BANKA KART") -> DetectedCardType.DEBIT
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
                    // Turkish style: 1.250,75
                    trimmed.replace(".", "").replace(',', '.')
                } else {
                    // US style: 1,250.75
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
                    // e.g. 19.99 USD or 542.5 TL
                    trimmed
                } else {
                    // e.g. 1.250 TL or 15.000 TL (thousands separator)
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

    private fun extractMerchant(title: String, body: String): String {
        // Pattern 1: "... tarihinde <MERCHANT> işyerinden / işyerinde / firmasında ..."
        val afterDateRegex = Regex(
            """(?i)tarihinde\s+(.+?)\s+(?:iş\s*yerinden|is\s*yerinden|iş\s*yerinde|is\s*yerinde|firmasından|firmasindan|üye\s*işyerinden|uye\s*isyerinden)"""
        )
        afterDateRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val cleaned = cleanMerchantName(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 2: "... kartınızla / kartınıza / ile <MERCHANT> işyerinden / işyerinde ..."
        val afterCardRegex = Regex(
            """(?i)(?:kartınızla|kartinizla|kartınıza|kartiniza|\(\d{4}\)\s*ile|olan\s+kartınızla|olan\s+kartinizla|biten\s+kartınızla|biten\s+kartinizla)\s+(?:\d{2}[./]\d{2}[./]\d{4}(?:\s+\d{2}:\d{2})?\s+(?:tarihinde\s+)?)?(.+?)\s+(?:iş\s*yerinden|is\s*yerinden|iş\s*yerinde|is\s*yerinde|firmasından|firmasindan|firmasında|firmasinda)"""
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
                .replace(Regex("""(?i)^.*?(?:kartınızla|kartinizla|tarihinde)\s+"""), "")
            val cleaned = cleanMerchantName(stripped)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 4: "... kartınızla <MERCHANT>'da / 'de / 'den / 'dan <AMOUNT> TL harcama"
        val apostropheRegex = Regex(
            """(?i)(?:kartınızla|kartinizla)\s+(.+?)['’](?:da|de|ta|te|dan|den|tan|ten)\s+\d"""
        )
        apostropheRegex.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
            val cleaned = cleanMerchantName(candidate)
            if (cleaned.isNotEmpty()) return cleaned
        }

        // Pattern 5: Useful non-generic notification title
        val genericTitles = setOf(
            "YAPI KREDI", "YAPI KREDI MOBIL", "WORLD MOBIL", "WORLDCARD",
            "HARCAMA BILGISI", "HARCAMA BILDIRIMI", "ISLEM BILGISI", "KARTSIZ ISLEM", "BILGILENDIRME"
        )
        val asciiTitle = CategoryClassifier.toAsciiUpper(title)
        if (title.isNotBlank() && asciiTitle !in genericTitles && title.length in 2..40) {
            return cleanMerchantName(title)
        }

        return "Yapı Kredi İşlemi"
    }

    private fun cleanMerchantName(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', ',', '.')
            .take(60)
    }
}
