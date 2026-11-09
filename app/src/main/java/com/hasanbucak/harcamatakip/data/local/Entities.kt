package com.hasanbucak.harcamatakip.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val last4: String,
    val alias: String,
    val cardType: String, // CREDIT, VIRTUAL, DEBIT, CASH
    val statementDay: Int = 1,
    val colorHex: Long = 0xFF1E3A8A,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["cardLast4"]),
        Index(value = ["normalizedMerchant"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardLast4: String,
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val transactionType: String, // EXPENSE, REFUND
    val installmentCount: Int = 1,
    val source: String, // PUSH_YKB, PUSH_WORLD, SMS, MANUAL
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
