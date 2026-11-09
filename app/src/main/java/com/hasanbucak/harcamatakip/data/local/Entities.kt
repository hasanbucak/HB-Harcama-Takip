package com.hasanbucak.harcamatakip.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class UnknownCardPolicy(val labelTr: String, val descriptionTr: String) {
    AUTO_ADD(
        "Otomatik Ekle",
        "Bu bankadan yeni bir kartla harcama gelirse kartı otomatik oluşturur ve kaydeder."
    ),
    ASK(
        "Bekleyenlere Düşür",
        "Tanımsız kart harcamalarını silmez; tek dokunuşla onaylaman için Bekleyenler kutusuna alır."
    ),
    IGNORE(
        "Kayıtlı Olmayanı Yoksay",
        "Yalnızca bu bankada elle eklediğin kartları kaydeder, diğer kartları sessizce atlar."
    );

    companion object {
        fun fromCode(code: String?): UnknownCardPolicy {
            return entries.find { it.name == code } ?: ASK
        }
    }
}

@Entity(tableName = "card_groups")
data class CardGroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val colorHex: Long,
    val sortOrder: Int
) {
    companion object {
        val DEFAULT_GROUPS: List<CardGroupEntity> = listOf(
            CardGroupEntity("KISISEL", "Kişisel", 0xFF1D4ED8, 0),
            CardGroupEntity("EV", "Ev", 0xFF15803D, 1),
            CardGroupEntity("IS", "İş / Şirket", 0xFFB45309, 2)
        )
    }
}

@Entity(tableName = "bank_rules")
data class BankRuleEntity(
    @PrimaryKey val bankCode: String,
    val unknownCardPolicy: String = UnknownCardPolicy.ASK.name
)

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val cardKey: String, // e.g. YAPI_KREDI:4821, VAKIFBANK:6251
    val bankCode: String,
    val bankName: String,
    val last4: String,
    val alias: String,
    val cardType: String,            // CREDIT, VIRTUAL, DEBIT, CASH
    val defaultGroupId: String? = null,
    val isBlocked: Boolean = false,
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
        Index(value = ["uuid"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["cardKey"]),
        Index(value = ["bankCode"]),
        Index(value = ["normalizedMerchant"]),
        Index(value = ["groupId", "timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val cardKey: String,
    val bankCode: String,
    val bankName: String,
    val cardLast4: String,
    val cardType: String,
    val groupId: String? = null,
    val groupManual: Boolean = false,
    val merchant: String,
    val normalizedMerchant: String,
    val amount: Double,
    val currency: String,
    val secondaryAmountTl: Double? = null,
    val category: String,
    val categoryManual: Boolean = false,
    val transactionType: String, // EXPENSE, REFUND
    val installmentCount: Int = 1,
    val source: String,
    val seenPackages: String = "",
    val txClockMinute: Int? = null,
    val isDemo: Boolean = false,
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
    tableName = "processed_messages",
    indices = [Index(value = ["firstSeenAt"])]
)
data class ProcessedMessageEntity(
    @PrimaryKey val fingerprint: String,
    val sourcePackage: String,
    val outcome: String, // SAVED, MERGED, PENDING, IGNORED
    val txUuid: String? = null,
    val firstSeenAt: Long
)

@Entity(
    tableName = "pending_reviews",
    indices = [
        Index(value = ["resolved", "timestamp"])
    ]
)
data class PendingReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val sourceChannel: String,
    val sourcePackage: String,
    val bankCode: String,
    val cardLast4: String,
    val detectedCardType: String,
    val merchant: String,
    val amount: Double,
    val currency: String,
    val transactionType: String,
    val installmentCount: Int,
    val txClockMinute: Int? = null,
    val reasonType: String, // PENDING_CARD, UNPARSED, POSSIBLE_DUPLICATE
    val reasonDetail: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val resolved: Boolean = false
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
    val status: String, // SAVED, DUPLICATE, PENDING, IGNORED, UNPARSED
    val detail: String,
    val timestamp: Long
)
