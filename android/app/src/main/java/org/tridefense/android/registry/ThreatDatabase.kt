package org.tridefense.android.registry

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** REAL cache storage. Mock integration ingests Backend records; never a chain authority. */
@Entity(tableName = "threats", primaryKeys = ["chainId", "threatId"])
data class CachedThreat(
    val chainId: String,
    val threatId: String,
    val voiceprintHash: String,
    val riskScore: Int,
    val registeredAt: Long,
    val zkProof: String,
)

@Entity(tableName = "promoted_phone_keys", primaryKeys = ["chainId", "phoneKey"])
data class CachedPhoneKey(val chainId: String, val phoneKey: String, val threatId: String)

@Entity(tableName = "events", primaryKeys = ["chainId", "txHash", "logIndex"])
data class CachedEvent(
    val chainId: String,
    val txHash: String,
    val logIndex: Int,
    val blockNumber: Long,
    val blockHash: String,
)

@Entity(tableName = "sync_cursors", primaryKeys = ["chainId"])
data class SyncCursor(
    val chainId: String,
    val lastProcessedBlock: Long,
    @androidx.room.ColumnInfo(defaultValue = "''") val registryAddress: String = "",
    val backendCursor: String? = null,
)

@Dao
interface ThreatCacheDao {
    @Query("DELETE FROM threats WHERE chainId = :chainId")
    suspend fun clearThreats(chainId: String)

    @Query("DELETE FROM events WHERE chainId = :chainId")
    suspend fun clearEvents(chainId: String)

    @Query("DELETE FROM promoted_phone_keys WHERE chainId = :chainId")
    suspend fun clearPhoneKeys(chainId: String)

    @Query("DELETE FROM sync_cursors WHERE chainId = :chainId")
    suspend fun clearCursor(chainId: String)

    @Query("SELECT count(*) FROM threats WHERE chainId = :chainId")
    suspend fun threatCount(chainId: String): Int

    @Query("SELECT count(*) FROM events WHERE chainId = :chainId")
    suspend fun eventCount(chainId: String): Int

    @Transaction
    suspend fun resetChain(chainId: String) {
        clearThreats(chainId)
        clearEvents(chainId)
        clearPhoneKeys(chainId)
        clearCursor(chainId)
    }

    @Transaction
    suspend fun applyPage(threats: List<CachedThreat>, events: List<CachedEvent>, cursor: SyncCursor) {
        for (threat in threats) {
            val previous = findThreat(threat.chainId, threat.threatId)
            check(previous == null || previous == threat) { "Cached record differs; rebuild required" }
            if (previous == null) insertThreat(threat)
        }
        events.forEach { insertEvent(it) }
        saveCursor(cursor)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertThreat(threat: CachedThreat)

    @Query("SELECT * FROM threats WHERE chainId = :chainId AND threatId = :threatId")
    suspend fun findThreat(chainId: String, threatId: String): CachedThreat?

    @Query("SELECT * FROM promoted_phone_keys WHERE chainId = :chainId AND phoneKey = :phoneKey")
    suspend fun findPhoneKey(chainId: String, phoneKey: String): CachedPhoneKey?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: CachedEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCursor(cursor: SyncCursor)

    @Query("SELECT * FROM sync_cursors WHERE chainId = :chainId")
    suspend fun findCursor(chainId: String): SyncCursor?
}

@Database(
    entities = [CachedThreat::class, CachedPhoneKey::class, CachedEvent::class, SyncCursor::class],
    version = 2,
    exportSchema = true,
)
abstract class ThreatDatabase : RoomDatabase() {
    abstract fun cacheDao(): ThreatCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_cursors ADD COLUMN registryAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sync_cursors ADD COLUMN backendCursor TEXT")
            }
        }
    }
}
