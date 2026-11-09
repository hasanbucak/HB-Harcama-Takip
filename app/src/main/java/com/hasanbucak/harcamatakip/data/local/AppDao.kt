package com.hasanbucak.harcamatakip.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardGroupDao {
    @Query("SELECT * FROM card_groups ORDER BY sortOrder ASC, name ASC")
    fun observeAllGroups(): Flow<List<CardGroupEntity>>

    @Query("SELECT * FROM card_groups ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllGroupsSnapshot(): List<CardGroupEntity>

    @Query("SELECT * FROM card_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): CardGroupEntity?

    @Upsert
    suspend fun upsert(group: CardGroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(groups: List<CardGroupEntity>)

    @Query("DELETE FROM card_groups WHERE groupId = :groupId")
    suspend fun deleteById(groupId: String)
}

@Dao
interface BankRuleDao {
    @Query("SELECT * FROM bank_rules")
    fun observeAllRules(): Flow<List<BankRuleEntity>>

    @Query("SELECT * FROM bank_rules")
    suspend fun getAllRulesSnapshot(): List<BankRuleEntity>

    @Query("SELECT * FROM bank_rules WHERE bankCode = :bankCode LIMIT 1")
    suspend fun getRule(bankCode: String): BankRuleEntity?

    @Upsert
    suspend fun upsert(rule: BankRuleEntity)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE isBlocked = 0 ORDER BY createdAt ASC")
    fun observeActiveCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY createdAt ASC")
    fun observeAllCardsIncludingBlocked(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY createdAt ASC")
    suspend fun getAllCardsSnapshot(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE bankCode = :bankCode AND isBlocked = 0 ORDER BY createdAt ASC")
    suspend fun getActiveCardsForBank(bankCode: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE cardKey = :cardKey LIMIT 1")
    suspend fun getCard(cardKey: String): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE isBlocked = 0")
    suspend fun countActiveCards(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(card: CardEntity): Long

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Query("UPDATE cards SET defaultGroupId = :newGroupId WHERE defaultGroupId = :oldGroupId")
    suspend fun reassignGroupForCards(oldGroupId: String, newGroupId: String?)

    @Query("DELETE FROM cards WHERE cardKey = :cardKey")
    suspend fun deleteByKey(cardKey: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDemo = 0 ORDER BY timestamp DESC")
    suspend fun getNonDemoTransactionsSnapshot(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): TransactionEntity?

    @Query(
        """
        SELECT * FROM transactions
        WHERE isDemo = 0
          AND bankCode = :bankCode
          AND transactionType = :transactionType
          AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        ORDER BY timestamp DESC
        """
    )
    suspend fun findCandidatesInWindow(
        bankCode: String,
        transactionType: String,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE cardKey = :cardKey")
    suspend fun deleteByCardKey(cardKey: String)

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    suspend fun deleteDemoTransactions()

    @Query("UPDATE transactions SET category = :category WHERE normalizedMerchant = :normalizedMerchant AND categoryManual = 0")
    suspend fun updateCategoryForMerchant(normalizedMerchant: String, category: String)

    @Query("UPDATE transactions SET cardType = :cardType, bankName = :bankName WHERE cardKey = :cardKey")
    suspend fun updateCardMetadataForTransactions(cardKey: String, cardType: String, bankName: String)

    @Query("UPDATE transactions SET groupId = :newGroupId WHERE cardKey = :cardKey AND groupManual = 0")
    suspend fun updateNonManualGroupForCard(cardKey: String, newGroupId: String?)

    @Query("UPDATE transactions SET groupId = :targetGroupId, groupManual = 0 WHERE groupId = :deletedGroupId")
    suspend fun moveGroupTransactionsOnGroupDelete(deletedGroupId: String, targetGroupId: String?)

    @Query("UPDATE transactions SET rawText = '' WHERE timestamp < :olderThanMillis AND rawText != ''")
    suspend fun scrubOldRawTexts(olderThanMillis: Long)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun getAllRulesSnapshot(): List<MerchantRuleEntity>

    @Query("SELECT category FROM merchant_rules WHERE normalizedMerchant = :normalizedMerchant LIMIT 1")
    suspend fun findCategory(normalizedMerchant: String): String?

    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)
}

@Dao
interface ProcessedMessageDao {
    @Query("SELECT * FROM processed_messages WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun find(fingerprint: String): ProcessedMessageEntity?

    @Upsert
    suspend fun upsert(entity: ProcessedMessageEntity)

    @Query("DELETE FROM processed_messages WHERE firstSeenAt < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)
}

@Dao
interface PendingReviewDao {
    @Query("SELECT * FROM pending_reviews WHERE resolved = 0 ORDER BY timestamp DESC")
    fun observeUnresolved(): Flow<List<PendingReviewEntity>>

    @Query("SELECT * FROM pending_reviews WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingReviewEntity?

    @Insert
    suspend fun insert(entity: PendingReviewEntity): Long

    @Query("UPDATE pending_reviews SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)

    @Query("DELETE FROM pending_reviews WHERE resolved = 1 AND timestamp < :olderThanMillis")
    suspend fun pruneResolvedOlderThan(olderThanMillis: Long)
}

@Dao
interface RawLogDao {
    @Query("SELECT * FROM raw_notification_logs ORDER BY timestamp DESC LIMIT 250")
    fun observeRecentLogs(): Flow<List<RawNotificationLogEntity>>

    @Insert
    suspend fun insert(log: RawNotificationLogEntity): Long

    @Query("UPDATE raw_notification_logs SET status = :status, detail = :detail WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, detail: String)

    @Query(
        """
        DELETE FROM raw_notification_logs
        WHERE status NOT IN ('UNPARSED', 'PENDING')
          AND id NOT IN (
              SELECT id FROM raw_notification_logs ORDER BY timestamp DESC LIMIT 300
          )
        """
    )
    suspend fun pruneOldLogs()

    @Query("DELETE FROM raw_notification_logs")
    suspend fun clearAll()
}
