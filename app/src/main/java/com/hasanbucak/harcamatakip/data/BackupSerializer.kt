package com.hasanbucak.harcamatakip.data

import com.hasanbucak.harcamatakip.data.local.BankRuleEntity
import com.hasanbucak.harcamatakip.data.local.CardEntity
import com.hasanbucak.harcamatakip.data.local.CardGroupEntity
import com.hasanbucak.harcamatakip.data.local.MerchantRuleEntity
import com.hasanbucak.harcamatakip.data.local.TransactionEntity
import com.hasanbucak.harcamatakip.domain.parser.DetectedCardType
import com.hasanbucak.harcamatakip.domain.parser.ParsedTransactionType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val groups: List<CardGroupEntity>,
    val bankRules: List<BankRuleEntity>,
    val cards: List<CardEntity>,
    val merchantRules: List<MerchantRuleEntity>,
    val transactions: List<TransactionEntity>
)

/**
 * Zero-dependency RFC-8259 JSON backup serializer/parser and Excel-safe CSV exporter.
 * Works identically on Android runtime and pure JVM unit tests without android.jar org.json stubs.
 */
object BackupSerializer {

    private const val BACKUP_FORMAT_VERSION = 3

    private val csvAmountFormat = DecimalFormat(
        "0.00",
        DecimalFormatSymbols(Locale.forLanguageTag("tr-TR")).apply {
            decimalSeparator = ','
        }
    )

    fun exportCsv(
        transactions: List<TransactionEntity>,
        cardsByKey: Map<String, CardEntity>,
        groupsById: Map<String, CardGroupEntity>
    ): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("Tarih;Bolum;Banka;KartSon4;KartAdi;KartTuru;Isyeri;Kategori;IslemTipi;Taksit;Tutar;ParaBirimi;Kaynak;Not")

        for (tx in transactions) {
            val card = cardsByKey[tx.cardKey]
            val groupName = tx.groupId?.let { groupsById[it]?.name } ?: "Bölümsüz"
            val alias = card?.alias ?: tx.cardLast4
            val cardTypeLabel = DetectedCardType.fromCode(tx.cardType).labelTr
            val signedAmount = if (tx.transactionType == ParsedTransactionType.REFUND.name) {
                -tx.amount
            } else {
                tx.amount
            }

            sb.appendLine(
                listOf(
                    dateFmt.format(Date(tx.timestamp)),
                    escapeCsvSafe(groupName),
                    escapeCsvSafe(tx.bankName),
                    escapeCsvSafe(tx.cardLast4),
                    escapeCsvSafe(alias),
                    escapeCsvSafe(cardTypeLabel),
                    escapeCsvSafe(tx.merchant),
                    escapeCsvSafe(tx.category),
                    escapeCsvSafe(tx.transactionType),
                    tx.installmentCount.toString(),
                    csvAmountFormat.format(signedAmount),
                    escapeCsvSafe(tx.currency),
                    escapeCsvSafe(tx.source),
                    escapeCsvSafe(tx.note)
                ).joinToString(";")
            )
        }
        return sb.toString()
    }

    fun escapeCsvSafe(value: String): String {
        val singleLine = value.replace("\r", " ").replace("\n", " ").trim()
        val formulaNeutralized = if (
            singleLine.isNotEmpty() &&
            (singleLine[0] == '=' || singleLine[0] == '+' || singleLine[0] == '-' || singleLine[0] == '@')
        ) {
            "'$singleLine"
        } else {
            singleLine
        }
        return if (formulaNeutralized.contains(';') || formulaNeutralized.contains('"')) {
            "\"${formulaNeutralized.replace("\"", "\"\"")}\""
        } else {
            formulaNeutralized
        }
    }

    fun exportJson(
        groups: List<CardGroupEntity>,
        bankRules: List<BankRuleEntity>,
        cards: List<CardEntity>,
        merchantRules: List<MerchantRuleEntity>,
        transactions: List<TransactionEntity>,
        includeRawText: Boolean = false,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"schemaVersion\":").append(BACKUP_FORMAT_VERSION).append(",")
        sb.append("\"exportedAt\":").append(exportedAt).append(",")

        sb.append("\"groups\":[")
        groups.forEachIndexed { i, g ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"groupId\":").append(jsonStr(g.groupId)).append(",")
                .append("\"name\":").append(jsonStr(g.name)).append(",")
                .append("\"colorHex\":").append(g.colorHex).append(",")
                .append("\"sortOrder\":").append(g.sortOrder)
                .append("}")
        }
        sb.append("],")

        sb.append("\"bankRules\":[")
        bankRules.forEachIndexed { i, r ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"bankCode\":").append(jsonStr(r.bankCode)).append(",")
                .append("\"unknownCardPolicy\":").append(jsonStr(r.unknownCardPolicy))
                .append("}")
        }
        sb.append("],")

        sb.append("\"cards\":[")
        cards.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"cardKey\":").append(jsonStr(c.cardKey)).append(",")
                .append("\"bankCode\":").append(jsonStr(c.bankCode)).append(",")
                .append("\"bankName\":").append(jsonStr(c.bankName)).append(",")
                .append("\"last4\":").append(jsonStr(c.last4)).append(",")
                .append("\"alias\":").append(jsonStr(c.alias)).append(",")
                .append("\"cardType\":").append(jsonStr(c.cardType)).append(",")
                .append("\"defaultGroupId\":").append(c.defaultGroupId?.let { jsonStr(it) } ?: "null").append(",")
                .append("\"isBlocked\":").append(c.isBlocked).append(",")
                .append("\"statementDay\":").append(c.statementDay).append(",")
                .append("\"colorHex\":").append(c.colorHex).append(",")
                .append("\"createdAt\":").append(c.createdAt)
                .append("}")
        }
        sb.append("],")

        sb.append("\"merchantRules\":[")
        merchantRules.forEachIndexed { i, m ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"normalizedMerchant\":").append(jsonStr(m.normalizedMerchant)).append(",")
                .append("\"category\":").append(jsonStr(m.category))
                .append("}")
        }
        sb.append("],")

        sb.append("\"transactions\":[")
        transactions.forEachIndexed { i, t ->
            if (i > 0) sb.append(",")
            sb.append("{")
                .append("\"uuid\":").append(jsonStr(t.uuid)).append(",")
                .append("\"cardKey\":").append(jsonStr(t.cardKey)).append(",")
                .append("\"bankCode\":").append(jsonStr(t.bankCode)).append(",")
                .append("\"bankName\":").append(jsonStr(t.bankName)).append(",")
                .append("\"cardLast4\":").append(jsonStr(t.cardLast4)).append(",")
                .append("\"cardType\":").append(jsonStr(t.cardType)).append(",")
                .append("\"groupId\":").append(t.groupId?.let { jsonStr(it) } ?: "null").append(",")
                .append("\"groupManual\":").append(t.groupManual).append(",")
                .append("\"merchant\":").append(jsonStr(t.merchant)).append(",")
                .append("\"normalizedMerchant\":").append(jsonStr(t.normalizedMerchant)).append(",")
                .append("\"amount\":").append(String.format(Locale.US, "%.2f", t.amount)).append(",")
                .append("\"currency\":").append(jsonStr(t.currency)).append(",")
                .append("\"secondaryAmountTl\":").append(t.secondaryAmountTl?.let { String.format(Locale.US, "%.2f", it) } ?: "null").append(",")
                .append("\"category\":").append(jsonStr(t.category)).append(",")
                .append("\"categoryManual\":").append(t.categoryManual).append(",")
                .append("\"transactionType\":").append(jsonStr(t.transactionType)).append(",")
                .append("\"installmentCount\":").append(t.installmentCount).append(",")
                .append("\"source\":").append(jsonStr(t.source)).append(",")
                .append("\"seenPackages\":").append(jsonStr(t.seenPackages)).append(",")
                .append("\"txClockMinute\":").append(t.txClockMinute?.toString() ?: "null").append(",")
                .append("\"timestamp\":").append(t.timestamp).append(",")
                .append("\"rawText\":").append(jsonStr(if (includeRawText) t.rawText else "")).append(",")
                .append("\"note\":").append(jsonStr(t.note))
                .append("}")
        }
        sb.append("]}")
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun importJson(jsonText: String): BackupPayload {
        val parser = MiniJsonParser(jsonText.trimStart('\uFEFF'))
        val root = parser.parseObjectAndEnsureEof()
        val schemaVer = (root["schemaVersion"] as? Number)?.toInt() ?: 1
        require(schemaVer in 1..10) { "Desteklenmeyen yedek sürümü: $schemaVer" }
        val exportedAt = (root["exportedAt"] as? Number)?.toLong() ?: 0L

        val groups = (root["groups"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val id = m["groupId"] as? String ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            CardGroupEntity(
                groupId = id,
                name = name,
                colorHex = (m["colorHex"] as? Number)?.toLong() ?: 0xFF1E3A8A,
                sortOrder = (m["sortOrder"] as? Number)?.toInt() ?: 0
            )
        }

        val bankRules = (root["bankRules"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val code = m["bankCode"] as? String ?: return@mapNotNull null
            val policy = m["unknownCardPolicy"] as? String ?: "ASK"
            BankRuleEntity(bankCode = code, unknownCardPolicy = policy)
        }

        val cards = (root["cards"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val key = m["cardKey"] as? String ?: return@mapNotNull null
            CardEntity(
                cardKey = key,
                bankCode = m["bankCode"] as? String ?: "OTHER",
                bankName = m["bankName"] as? String ?: "Diğer",
                last4 = m["last4"] as? String ?: "GENEL",
                alias = m["alias"] as? String ?: key,
                cardType = m["cardType"] as? String ?: "CREDIT",
                defaultGroupId = m["defaultGroupId"] as? String,
                isBlocked = m["isBlocked"] as? Boolean ?: false,
                statementDay = (m["statementDay"] as? Number)?.toInt() ?: 1,
                colorHex = (m["colorHex"] as? Number)?.toLong() ?: 0xFF1E3A8A,
                createdAt = (m["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        val merchantRules = (root["merchantRules"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val norm = m["normalizedMerchant"] as? String ?: return@mapNotNull null
            val cat = m["category"] as? String ?: return@mapNotNull null
            MerchantRuleEntity(normalizedMerchant = norm, category = cat)
        }

        val transactions = (root["transactions"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val cardKey = m["cardKey"] as? String ?: return@mapNotNull null
            val amount = (m["amount"] as? Number)?.toDouble() ?: return@mapNotNull null
            TransactionEntity(
                uuid = (m["uuid"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                cardKey = cardKey,
                bankCode = m["bankCode"] as? String ?: "OTHER",
                bankName = m["bankName"] as? String ?: "Diğer",
                cardLast4 = m["cardLast4"] as? String ?: "GENEL",
                cardType = m["cardType"] as? String ?: "CREDIT",
                groupId = m["groupId"] as? String,
                groupManual = m["groupManual"] as? Boolean ?: false,
                merchant = m["merchant"] as? String ?: "İşlem",
                normalizedMerchant = m["normalizedMerchant"] as? String ?: "",
                amount = amount,
                currency = m["currency"] as? String ?: "TL",
                secondaryAmountTl = (m["secondaryAmountTl"] as? Number)?.toDouble(),
                category = m["category"] as? String ?: "Diğer",
                categoryManual = m["categoryManual"] as? Boolean ?: false,
                transactionType = m["transactionType"] as? String ?: "EXPENSE",
                installmentCount = (m["installmentCount"] as? Number)?.toInt() ?: 1,
                source = m["source"] as? String ?: "IMPORT",
                seenPackages = m["seenPackages"] as? String ?: "",
                txClockMinute = (m["txClockMinute"] as? Number)?.toInt(),
                isDemo = false,
                timestamp = (m["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                rawText = m["rawText"] as? String ?: "",
                note = m["note"] as? String ?: ""
            )
        }

        return BackupPayload(
            schemaVersion = schemaVer,
            exportedAt = exportedAt,
            groups = groups,
            bankRules = bankRules,
            cards = cards,
            merchantRules = merchantRules,
            transactions = transactions
        )
    }

    private fun jsonStr(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseObjectAndEnsureEof(): Map<String, Any?> {
            val obj = parseObject()
            skipWs()
            if (pos != src.length) {
                throw IllegalArgumentException("Trailing content after JSON root at $pos")
            }
            return obj
        }

        fun parseObject(): Map<String, Any?> {
            skipWs()
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                skipWs()
                val value = parseValue()
                map[key] = value
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Unexpected char at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            skipWs()
            expect('[')
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                list.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Unexpected char in array at $pos")
                }
            }
        }

        private fun parseValue(): Any? {
            skipWs()
            return when (val ch = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> {
                    expectWord("true")
                    true
                }
                'f' -> {
                    expectWord("false")
                    false
                }
                'n' -> {
                    expectWord("null")
                    null
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Invalid JSON value char '$ch' at $pos")
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (src[pos] == '-') pos++
            var isFloat = false
            while (pos < src.length) {
                val c = src[pos]
                if (c in '0'..'9') {
                    pos++
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true
                    pos++
                } else {
                    break
                }
            }
            val token = src.substring(start, pos)
            return if (isFloat) token.toDouble() else token.toLong()
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char {
            if (pos >= src.length) throw IllegalArgumentException("Unexpected EOF")
            return src[pos]
        }

        private fun expect(expected: Char) {
            val actual = peek()
            if (actual != expected) {
                throw IllegalArgumentException("Expected '$expected' but got '$actual' at $pos")
            }
            pos++
        }

        private fun expectWord(word: String) {
            if (!src.startsWith(word, pos)) {
                throw IllegalArgumentException("Expected '$word' at $pos")
            }
            pos += word.length
        }
    }
}
