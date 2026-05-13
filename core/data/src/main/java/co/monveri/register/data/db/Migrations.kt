package co.monveri.register.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Manual migration registry. Auto-migrations cover most schema deltas (set
 * `@Database(autoMigrations = [...])` on [MonveriDatabase]); register hand-written ones here
 * when a column rename, data backfill, or table split is needed.
 *
 * Empty in Phase 2 — the first real migration lands when Phase 3 adds the catalog snapshot.
 */
object MonveriMigrations {
    /** Returns the array passed to `Room.databaseBuilder(...).addMigrations(...)`. */
    fun all(): Array<Migration> = arrayOf<Migration>()

    /**
     * Placeholder example for Phase 3's first real entity — keep here so future devs can copy
     * the pattern without searching Stack Overflow.
     */
    @Suppress("unused")
    val MIGRATION_1_TO_2_EXAMPLE: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // db.execSQL("CREATE TABLE catalog_snapshot (...)")
        }
    }
}
