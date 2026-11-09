package com.hasanbucak.harcamatakip.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY createdAt ASC")
    fun observeAllCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE last4 = :last4 LIMIT 1")
    suspend fun getCard(last4: String): CardEntity?

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun countCards(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(card: CardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<TransactionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE cardLast4 = :cardLast4
          AND currency = :currency
          AND transactionType = :transactionType
          AND ABS(amount - :amount) < 0.01
          AND timestamp >= :sinceTimestamp
        """
    )
    suspend fun countRecentDuplicate(
        cardLast4: String,
        amount: Double,
        currency: String,
        transactionType: String,
        sinceTimestamp: Long
    ): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun updateCategoryForMerchant(normalizedMerchant: String, category: String)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT category FROM merchant_rules WHERE normalizedMerchant = :normalizedMerchant LIMIT 1")
    suspend fun findCategory(normalizedMerchant: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)
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
        WHERE id NOT IN (
            SELECT id FROM raw_notification_logs ORDER BY timestamp DESC LIMIT 400
        )
        """
    )
    suspend fun pruneOldLogs()

    @Query("DELETE FROM raw_notification_logs")
    suspend fun clearAll()
}
