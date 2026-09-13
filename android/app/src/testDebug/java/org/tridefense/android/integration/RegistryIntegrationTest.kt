package org.tridefense.android.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tridefense.android.registry.ThreatDatabase
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RegistryIntegrationTest {
    private lateinit var db: ThreatDatabase
    private val registry = "0x" + "1".repeat(40)
    private val record = JSONObject("""{"threatId":"sample-id","voiceprintHash":"sample-hash","riskScore":9000,"registeredAt":"123","zkProof":"SAMPLE"}""")
    private fun page() = JSONObject("""{"chainId":"31337","registryAddress":"$registry","proofMode":"MOCK_PROOF","throughBlock":"5","nextCursor":"opaque+/=","events":[{"chainId":"31337","event":"ThreatRegistered","txHash":"sample-tx","logIndex":"0","blockNumber":"5","blockHash":"sample-block","args":{"threatId":"sample-id","voiceprintHash":"sample-hash","riskScore":9000,"registeredAt":"123"}}]}""")
    private var failure: Exception? = null
    private val paths = mutableListOf<String>()
    private val transport = object : BackendTransport {
        override fun request(path: String, body: JSONObject?): JSONObject {
            paths += path
            failure?.let { throw it }
            return if (path.startsWith("/v1/registry/events")) page() else record
        }
    }
    private fun worker() = RegistryIntegration(MockFixture("http://127.0.0.1:8000", registry, JSONObject()), transport, db)
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ThreatDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun repeatedPagesPreserveFullRecordAndOpaqueCursor() = runBlocking {
        assertEquals(1, worker().synchronize())
        assertEquals(1, worker().synchronize())
        assertEquals(1, db.cacheDao().eventCount("31337"))
        assertEquals("SAMPLE", db.cacheDao().findThreat("31337", "sample-id")?.zkProof)
        assertEquals("opaque+/=", db.cacheDao().findCursor("31337")?.backendCursor)
        assertTrue(paths.contains("/v1/registry/events?cursor=opaque%2B%2F%3D"))
        assertNull(db.cacheDao().findPhoneKey("31337", "sample-hash"))
    }
    @Test fun mismatchedRegistryRecordDoesNotAdvanceCache() = runBlocking {
        record.put("riskScore", 1)
        assertTrue(runCatching { worker().synchronize() }.isFailure)
        assertNull(db.cacheDao().findCursor("31337"))
        assertEquals(0, db.cacheDao().threatCount("31337"))
        assertEquals(0, db.cacheDao().eventCount("31337"))
    }
    @Test fun outagePreservesCacheButReorgInvalidatesIt() = runBlocking {
        worker().synchronize()
        failure = BackendFailure("DEPENDENCY_UNAVAILABLE", 503)
        assertTrue(runCatching { worker().synchronize() }.isFailure)
        assertEquals(1, db.cacheDao().threatCount("31337"))
        failure = BackendFailure("CURSOR_REORG", 409)
        assertTrue(runCatching { worker().synchronize() }.isFailure)
        assertEquals(0, db.cacheDao().threatCount("31337"))
        assertNull(db.cacheDao().findCursor("31337"))
    }
    @Test fun migrationPreservesVersionOneRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.tridefense.android.registry.ThreatDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, 0, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO sync_cursors VALUES ('31337', 5)")
            old.execSQL("INSERT INTO threats VALUES ('31337', 'old-id', 'SAMPLE', 9000, 123, 'SAMPLE')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, ThreatDatabase::class.java, name).addMigrations(ThreatDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("SAMPLE", migrated.cacheDao().findThreat("31337", "old-id")?.zkProof)
            assertEquals("", migrated.cacheDao().findCursor("31337")?.registryAddress)
            assertNull(migrated.cacheDao().findCursor("31337")?.backendCursor)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
