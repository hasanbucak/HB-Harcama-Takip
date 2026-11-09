package com.hasanbucak.harcamatakip.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val cardKey: String, // e.g. YAPI_KREDI:4821, VAKIFBANK:6251, TOM_BANK:1234
    val bankCode: String,            // YAPI_KREDI, VAKIFBANK, TOM_BANK, OTHER
    val bankName: String,            // Yapı Kredi, VakıfBank, TOM Bank, Diğer
    val last4: String,
    val alias: String,
    val cardType: String,            // CREDIT, VIRTUAL, DEBIT, CASH
    val statementDay: Int = 1,
    val colorHex: Long = 0xFF1E3A8A,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun buildKey(bankCode: String, last4: String): String {
            return "${bankCode.trim()}:${last4.trim()}"
        }
    }
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["cardKey"]),
        Index(value = ["bankCode"]),
        Index(value = ["normalizedMerchant"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardKey: String,
    val bankCode: String,
    val bankName: String,
    val cardLast4: String,
    val cardType: String, // CREDIT, VIRTUAL, DEBIT, CASH
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val transactionType: String, // EXPENSE, REFUND
    val installmentCount: Int = 1,
    val source: String, // PUSH_YKB, PUSH_WORLD, PUSH_VAKIF, PUSH_TOM, SMS, RCS_MSG, MANUAL
    val timestamp: Long,
    val rawText: String,
    val note: String = ""
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val normalizedMerchant: String,
    val category: String
)

@Entity(
    tableName = "raw_notification_logs",
    indices = [Index(value = ["timestamp"])]
)
data class RawNotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceChannel: String,
    val title: String,
    val body: String,
    val status: String, // SAVED, DUPLICATE, IGNORED, UNPARSED
    val detail: String,
    val timestamp: Long
)
