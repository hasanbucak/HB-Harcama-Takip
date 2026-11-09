package com.hasanbucak.harcamatakip.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CardGroupEntity::class,
        BankRuleEntity::class,
        CardEntity::class,
        TransactionEntity::class,
        MerchantRuleEntity::class,
        ProcessedMessageEntity::class,
        PendingReviewEntity::class,
        RawNotificationLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cardGroupDao(): CardGroupDao
    abstract fun bankRuleDao(): BankRuleDao
    abstract fun cardDao(): CardDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun processedMessageDao(): ProcessedMessageDao
    abstract fun pendingReviewDao(): PendingReviewDao
    abstract fun rawLogDao(): RawLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun createNewV3SupportTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_groups` (
                    `groupId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorHex` INTEGER NOT NULL,
                    `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`groupId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `card_groups` (`groupId`, `name`, `colorHex`, `sortOrder`) VALUES
                ('KISISEL', 'Kişisel', ${0xFF1D4ED8L}, 0),
                ('EV', 'Ev', ${0xFF15803DL}, 1),
                ('IS', 'İş / Şirket', ${0xFFB45309L}, 2)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bank_rules` (
                    `bankCode` TEXT NOT NULL,
                    `unknownCardPolicy` TEXT NOT NULL,
                    PRIMARY KEY(`bankCode`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `processed_messages` (
                    `fingerprint` TEXT NOT NULL,
                    `sourcePackage` TEXT NOT NULL,
                    `outcome` TEXT NOT NULL,
                    `txUuid` TEXT,
                    `firstSeenAt` INTEGER NOT NULL,
                    PRIMARY KEY(`fingerprint`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_processed_messages_firstSeenAt` ON `processed_messages` (`firstSeenAt`)"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_reviews` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fingerprint` TEXT NOT NULL,
                    `sourceChannel` TEXT NOT NULL,
                    `sourcePackage` TEXT NOT NULL,
                    `bankCode` TEXT NOT NULL,
                    `cardLast4` TEXT NOT NULL,
                    `detectedCardType` TEXT NOT NULL,
                    `merchant` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `currency` TEXT NOT NULL,
                    `transactionType` TEXT NOT NULL,
                    `installmentCount` INTEGER NOT NULL,
                    `txClockMinute` INTEGER,
                    `reasonType` TEXT NOT NULL,
                    `reasonDetail` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `resolved` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_reviews_resolved_timestamp` ON `pending_reviews` (`resolved`, `timestamp`)"
            )
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createNewV3SupportTables(db)

                // Preserve existing v1 YKB user behavior on upgrade
                db.execSQL(
                    "INSERT OR IGNORE INTO `bank_rules` (`bankCode`, `unknownCardPolicy`) VALUES ('YAPI_KREDI', 'AUTO_ADD')"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cards_new` (
                        `cardKey` TEXT NOT NULL,
                        `bankCode` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        `alias` TEXT NOT NULL,
                        `cardType` TEXT NOT NULL,
                        `defaultGroupId` TEXT,
                        `isBlocked` INTEGER NOT NULL,
                        `statementDay` INTEGER NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cardKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cards_new` (
                        `cardKey`, `bankCode`, `bankName`, `last4`, `alias`,
                        `cardType`, `defaultGroupId`, `isBlocked`, `statementDay`, `colorHex`, `createdAt`
                    )
                    SELECT
                        CASE WHEN `last4` = 'NAKIT' THEN 'OTHER:NAKIT' ELSE 'YAPI_KREDI:' || `last4` END,
                        CASE WHEN `last4` = 'NAKIT' THEN 'OTHER' ELSE 'YAPI_KREDI' END,
                        CASE WHEN `last4` = 'NAKIT' THEN 'Diğer' ELSE 'Yapı Kredi' END,
                        `last4`, `alias`, `cardType`, NULL, 0, `statementDay`, `colorHex`, `createdAt`
                    FROM `cards`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `cards`")
                db.execSQL("ALTER TABLE `cards_new` RENAME TO `cards`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `cardKey` TEXT NOT NULL,
                        `bankCode` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `cardLast4` TEXT NOT NULL,
                        `cardType` TEXT NOT NULL,
                        `groupId` TEXT,
                        `groupManual` INTEGER NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `normalizedMerchant` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `secondaryAmountTl` REAL,
                        `category` TEXT NOT NULL,
                        `categoryManual` INTEGER NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `installmentCount` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `seenPackages` TEXT NOT NULL,
                        `txClockMinute` INTEGER,
                        `isDemo` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `note` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        `id`, `uuid`, `cardKey`, `bankCode`, `bankName`, `cardLast4`, `cardType`,
                        `groupId`, `groupManual`, `merchant`, `normalizedMerchant`, `amount`, `currency`, `secondaryAmountTl`,
                        `category`, `categoryManual`, `transactionType`, `installmentCount`, `source`,
                        `seenPackages`, `txClockMinute`, `isDemo`, `timestamp`, `rawText`, `note`
                    )
                    SELECT
                        `id`,
                        'v1-' || `id` || '-' || `timestamp`,
                        CASE WHEN `cardLast4` = 'NAKIT' THEN 'OTHER:NAKIT' ELSE 'YAPI_KREDI:' || `cardLast4` END,
                        CASE WHEN `cardLast4` = 'NAKIT' THEN 'OTHER' ELSE 'YAPI_KREDI' END,
                        CASE WHEN `cardLast4` = 'NAKIT' THEN 'Nakit' ELSE 'Yapı Kredi' END,
                        `cardLast4`,
                        CASE WHEN `cardLast4` = 'NAKIT' THEN 'CASH' ELSE 'CREDIT' END,
                        NULL, 0, `merchant`, `normalizedMerchant`, `amount`, `currency`, NULL,
                        `category`, 0, `transactionType`, `installmentCount`, `source`,
                        '', NULL, 0, `timestamp`, `rawText`, `note`
                    FROM `transactions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                createTransactionIndices(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createNewV3SupportTables(db)

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `bank_rules` (`bankCode`, `unknownCardPolicy`) VALUES
                    ('YAPI_KREDI', 'AUTO_ADD'),
                    ('TOM_BANK', 'AUTO_ADD'),
                    ('VAKIFBANK', 'IGNORE')
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cards_new` (
                        `cardKey` TEXT NOT NULL,
                        `bankCode` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `last4` TEXT NOT NULL,
                        `alias` TEXT NOT NULL,
                        `cardType` TEXT NOT NULL,
                        `defaultGroupId` TEXT,
                        `isBlocked` INTEGER NOT NULL,
                        `statementDay` INTEGER NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cardKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `cards_new` (
                        `cardKey`, `bankCode`, `bankName`, `last4`, `alias`,
                        `cardType`, `defaultGroupId`, `isBlocked`, `statementDay`, `colorHex`, `createdAt`
                    )
                    SELECT
                        `cardKey`, `bankCode`, `bankName`, `last4`, `alias`,
                        `cardType`, NULL, 0, `statementDay`, `colorHex`, `createdAt`
                    FROM `cards`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `cards`")
                db.execSQL("ALTER TABLE `cards_new` RENAME TO `cards`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uuid` TEXT NOT NULL,
                        `cardKey` TEXT NOT NULL,
                        `bankCode` TEXT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `cardLast4` TEXT NOT NULL,
                        `cardType` TEXT NOT NULL,
                        `groupId` TEXT,
                        `groupManual` INTEGER NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `normalizedMerchant` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `secondaryAmountTl` REAL,
                        `category` TEXT NOT NULL,
                        `categoryManual` INTEGER NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `installmentCount` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `seenPackages` TEXT NOT NULL,
                        `txClockMinute` INTEGER,
                        `isDemo` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `note` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_new` (
                        `id`, `uuid`, `cardKey`, `bankCode`, `bankName`, `cardLast4`, `cardType`,
                        `groupId`, `groupManual`, `merchant`, `normalizedMerchant`, `amount`, `currency`, `secondaryAmountTl`,
                        `category`, `categoryManual`, `transactionType`, `installmentCount`, `source`,
                        `seenPackages`, `txClockMinute`, `isDemo`, `timestamp`, `rawText`, `note`
                    )
                    SELECT
                        `id`, 'v2-' || `id` || '-' || `timestamp`,
                        `cardKey`, `bankCode`, `bankName`, `cardLast4`, `cardType`,
                        NULL, 0, `merchant`, `normalizedMerchant`, `amount`, `currency`, NULL,
                        `category`, 0, `transactionType`, `installmentCount`, `source`,
                        '', NULL, 0, `timestamp`, `rawText`, `note`
                    FROM `transactions`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                createTransactionIndices(db)
            }
        }

        private fun createTransactionIndices(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_uuid` ON `transactions` (`uuid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_cardKey` ON `transactions` (`cardKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_bankCode` ON `transactions` (`bankCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_normalizedMerchant` ON `transactions` (`normalizedMerchant`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_groupId_timestamp` ON `transactions` (`groupId`, `timestamp`)")
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hb_harcama_takip.db"
                )
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3)
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                db.execSQL(
                                    """
                                    INSERT OR IGNORE INTO `card_groups` (`groupId`, `name`, `colorHex`, `sortOrder`) VALUES
                                    ('KISISEL', 'Kişisel', ${0xFF1D4ED8L}, 0),
                                    ('EV', 'Ev', ${0xFF15803DL}, 1),
                                    ('IS', 'İş / Şirket', ${0xFFB45309L}, 2)
                                    """.trimIndent()
                                )
                            }
                        }
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
