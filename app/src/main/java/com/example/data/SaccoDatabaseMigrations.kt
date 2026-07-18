package com.example.data

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
 *  v7     Firebase Auth integration: `password` column removed from sacco_users;
 *         `firebaseUid` column added. Firebase Auth now owns all credentials.
 *
 * ══════════════════════════════════════════════════════════════════════════════
 */

/**
 * Migration 6 → 7: Firebase Auth Integration
 *
 * Summary of changes to the `sacco_users` table:
 *   ADDED:   firebaseUid TEXT NOT NULL DEFAULT ''
 *   REMOVED: password TEXT NOT NULL DEFAULT '123'
 *
 * Rationale: Firebase Authentication now owns all user credentials. Storing
 * passwords locally (even hashed) is unnecessary and a security liability.
 * The `firebaseUid` column links each local SaccoUser record to its Firebase
 * Auth account so session restoration and profile lookups work offline.
 *
 * Migration strategy (required because SQLite cannot DROP COLUMN directly):
 *   Step 1 – Add `firebaseUid` to the existing table via ALTER TABLE.
 *             This keeps the table intact while we prepare the new schema.
 *   Step 2 – Create a new table `sacco_users_new` with the final schema
 *             (password column absent, firebaseUid present).
 *   Step 3 – Copy all retained columns from the old table. The firebaseUid
 *             column is seeded with an empty string; existing users will have
 *             their UID populated the next time they log in via Firebase Auth.
 *   Step 4 – Drop the old table.
 *   Step 5 – Rename the new table to `sacco_users`.
 *
 * Data safety: All user records, roles, statuses, and membership numbers are
 * preserved. Only the password column is discarded (it held plaintext values
 * of little security value — real auth now lives in Firebase).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1: Add firebaseUid to the existing table.
        // Using DEFAULT '' so the ALTER TABLE succeeds without touching existing rows.
        db.execSQL(
            "ALTER TABLE sacco_users ADD COLUMN firebaseUid TEXT NOT NULL DEFAULT ''"
        )

        // Step 2: Create the replacement table with the final v7 schema.
        // Column order and types must match the SaccoUser @Entity exactly.
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

        // Step 3: Copy all retained columns.
        // firebaseUid is populated with '' (the default); existing users
        // will have their UID filled on next Firebase Auth login.
        db.execSQL(
            """
            INSERT INTO sacco_users_new
                (id, email, phone, name, role, status, membershipNumber, firebaseUid)
            SELECT
                id, email, phone, name, role, status, membershipNumber, ''
            FROM sacco_users
            """.trimIndent()
        )

        // Step 4: Remove the old table (which still has the password column).
        db.execSQL("DROP TABLE sacco_users")

        // Step 5: Promote the new table.
        db.execSQL("ALTER TABLE sacco_users_new RENAME TO sacco_users")
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
    MIGRATION_6_7
    // MIGRATION_7_8,  // ← add future migrations here
)
