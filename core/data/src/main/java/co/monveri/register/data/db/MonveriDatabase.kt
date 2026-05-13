package co.monveri.register.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Phase 2 scaffold — empty entity list, but the schema versioning + migration framework is in
 * place so Phase 3+ entities (catalog snapshot, queued tickets, expenses) can land with proper
 * migration paths.
 *
 * Schema is exported to `core/data/schemas/<version>.json` for diff-able review (configured in
 * `core/data/build.gradle.kts`).
 */
@Database(
    entities = [SchemaProbeEntity::class],
    version = MonveriDatabase.VERSION,
    exportSchema = true,
)
abstract class MonveriDatabase : RoomDatabase() {

    abstract fun schemaProbeDao(): SchemaProbeDao

    companion object {
        const val VERSION = 1
        const val NAME = "monveri.db"
    }
}
