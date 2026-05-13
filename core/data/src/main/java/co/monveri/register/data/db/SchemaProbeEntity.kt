package co.monveri.register.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Placeholder entity + DAO so Room's KSP processor accepts the Phase 2 database (it rejects
 * a `@Database` with zero entities). Phase 3 deletes this file when the real catalog snapshot,
 * queued tickets, etc. land. The table name is prefixed with `_` to flag it as internal.
 */
@Entity(tableName = "_room_schema_probe")
data class SchemaProbeEntity(
    @PrimaryKey val id: Int = 0,
)

@Dao
interface SchemaProbeDao {
    @Query("SELECT 1") suspend fun ping(): Int
}
