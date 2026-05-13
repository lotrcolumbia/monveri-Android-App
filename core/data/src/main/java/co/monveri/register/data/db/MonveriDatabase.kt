package co.monveri.register.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Phase 2 scaffold. Registers a single [SchemaProbeEntity] placeholder so Room's KSP processor
 * accepts the database; the schema versioning + migration framework is in place so Phase 3+
 * entities (catalog snapshot, queued tickets, expenses) can land with proper migration paths.
 *
 * Phase 3 deletes [SchemaProbeEntity] and replaces it with the real entity list.
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
