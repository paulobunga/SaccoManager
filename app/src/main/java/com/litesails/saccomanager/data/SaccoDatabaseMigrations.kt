package com.litesails.saccomanager.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SACCO Manager — Database Migration Definitions
 *
 * ══════════════════════════════════════════════════════════════════════════════
 * MIGRATION POLICY (mandatory reading before any schema change)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * 1. NEVER increment the database version without providing a corresponding
 *    Migration object in this file.
 *
 * 2. NEVER use `fallbackToDestructiveMigration()` for production versions.
 *    Only `fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)` is permitted,
 *    which limits data destruction to ancient, pre-production schema versions.
 *
 * 3. Every Migration object must be:
 *    a. Named MIGRATION_<from>_<to>  (e.g., MIGRATION_7_8)
 *    b. Added to the `ALL_MIGRATIONS` list at the bottom of this file
 *    c. Registered in SaccoDatabase.kt via `.addMigrations(MIGRATION_6_7, ...)`
 *
 * 4. SQLite does not support DROP COLUMN. To remove a column:
 *    - Add the new column(s) with ALTER TABLE ... ADD COLUMN
 *    - CREATE TABLE <name>_new with the desired final schema
 *    - INSERT INTO <name>_new SELECT <kept columns> FROM <name>
 *    - DROP TABLE <name>
 *    - ALTER TABLE <name>_new RENAME TO <name>
 *    See MIGRATION_6_7 below as the canonical example.
 *
 * 5. All column definitions in the CREATE TABLE statements must exactly match
 *    the corresponding Room @Entity class — including NOT NULL constraints,
 *    DEFAULT values, and PRIMARY KEY declarations.
 *
 * 6. After writing a migration, bump `version` in SaccoDatabase.kt and
 *    update the `fallbackToDestructiveMigrationFrom(...)` list if the old
 *    version was also a pre-production version (safe to destroy).
 *
 * ══════════════════════════════════════════════════════════════════════════════
 * VERSION HISTORY
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  v1–v5  Pre-production prototypes — destructive migration permitted.
 *  v6     Production baseline: SaccoUser includes a plaintext `password` field.
 *  v7     Auth integration: `password` column removed from sacco_users;
 *         `firebaseUid` column added. Firebase Auth now owned credentials.
 *  v8     Clerk Auth migration: `firebaseUid` column renamed to `clerkUserId`.
 *  v9     IOTEC integration: added `iotecRequestId`, `iotecStatus`,
 *         `contributionType` to `savings_payments`.
 *  v10    Long polling metadata: added `iotecPollingAttempts`,
 *         `iotecPollingStartedAt`, `iotecPollingCompletedAt`.
 * ══════════════════════════════════════════════════════════════════════════════
 */

/**
 * Migration 6 → 7: Auth Integration
 *
 * Summary of changes to the `sacco_users` table:
 *   ADDED:   firebaseUid TEXT NOT NULL DEFAULT ''
 *   REMOVED: password TEXT NOT NULL DEFAULT '123'
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE sacco_users ADD COLUMN firebaseUid TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            """
            CREATE TABLE sacco_users_new (
                id               TEXT PRIMARY KEY NOT NULL,
                email            TEXT NOT NULL,
                phone            TEXT NOT NULL,
                name             TEXT NOT NULL,
                role             TEXT NOT NULL,
                status           TEXT NOT NULL DEFAULT 'ACTIVE',
                membershipNumber TEXT NOT NULL DEFAULT '',
                firebaseUid      TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO sacco_users_new
                (id, email, phone, name, role, status, membershipNumber, firebaseUid)
            SELECT
                id, email, phone, name, role, status, membershipNumber, ''
            FROM sacco_users
            """.trimIndent()
        )
        db.execSQL("DROP TABLE sacco_users")
        db.execSQL("ALTER TABLE sacco_users_new RENAME TO sacco_users")
    }
}

/**
 * Migration 7 → 8: Clerk Auth Migration
 *
 * Summary of changes to the `sacco_users` table:
 *   RENAMED: firebaseUid → clerkUserId
 *
 * Rationale: Firebase Auth has been replaced by Clerk. The column is renamed
 * to reflect the new auth provider. Existing values (Supabase UIDs) are
 * preserved in place — they will be overwritten with Clerk user IDs on next
 * successful login.
 *
 * Migration strategy (SQLite cannot rename columns directly):
 *   Step 1 – Create sacco_users_new with clerkUserId instead of firebaseUid.
 *   Step 2 – Copy all data; firebaseUid value is carried into clerkUserId.
 *   Step 3 – Drop old table and rename new table.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sacco_users_new (
                id               TEXT PRIMARY KEY NOT NULL,
                email            TEXT NOT NULL,
                phone            TEXT NOT NULL,
                name             TEXT NOT NULL,
                role             TEXT NOT NULL,
                status           TEXT NOT NULL DEFAULT 'ACTIVE',
                membershipNumber TEXT NOT NULL DEFAULT '',
                clerkUserId      TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO sacco_users_new
                (id, email, phone, name, role, status, membershipNumber, clerkUserId)
            SELECT
                id, email, phone, name, role, status, membershipNumber, firebaseUid
            FROM sacco_users
            """.trimIndent()
        )
        db.execSQL("DROP TABLE sacco_users")
        db.execSQL("ALTER TABLE sacco_users_new RENAME TO sacco_users")
    }
}

/**
 * Migration 8 → 9: IOTEC Integration
 *
 * Summary of changes to the `savings_payments` table:
 *   ADDED: iotecRequestId TEXT NOT NULL DEFAULT ''
 *   ADDED: iotecStatus TEXT NOT NULL DEFAULT ''
 *   ADDED: contributionType TEXT NOT NULL DEFAULT 'SAVINGS'
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN iotecRequestId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN iotecStatus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN contributionType TEXT NOT NULL DEFAULT 'SAVINGS'")
    }
}

/**
 * Migration 9 → 10: Long Polling Metadata
 *
 * Summary of changes to the `savings_payments` table:
 *   ADDED: iotecPollingAttempts INTEGER NOT NULL DEFAULT 0
 *   ADDED: iotecPollingStartedAt TEXT
 *   ADDED: iotecPollingCompletedAt TEXT
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN iotecPollingAttempts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN iotecPollingStartedAt TEXT")
        db.execSQL("ALTER TABLE savings_payments ADD COLUMN iotecPollingCompletedAt TEXT")
    }
}

/**
 * Central registry of all migrations.
 *
 * Pass this array to `.addMigrations(*ALL_MIGRATIONS)` in SaccoDatabase.kt:
 *
 *   Room.databaseBuilder(context, SaccoDatabase::class.java, "sacco_db")
 *       .addMigrations(*ALL_MIGRATIONS)
 *       .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
 *       .build()
 *
 * Add each new Migration constant here as it is created.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10
)
