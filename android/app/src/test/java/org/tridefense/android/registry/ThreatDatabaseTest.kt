package org.tridefense.android.registry

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ThreatDatabaseTest {
    private lateinit var db: ThreatDatabase
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ThreatDatabase::class.java).build()
    }
    @After fun close() { db.close() }

    @Test fun cacheStartsEmptyAndDoesNotSeedThreatsOrNumbers() = runBlocking {
        assertNull(db.cacheDao().findThreat("31337", "fixture-id"))
        assertNull(db.cacheDao().findPhoneKey("31337", "fixture-key"))
        assertNull(db.cacheDao().findCursor("31337"))
    }
    @Test fun threatIdentityIsPerChainNotPerVoiceprint() = runBlocking {
        // SAMPLE persistence fixtures, not generated hashes or verified proofs.
        val first = CachedThreat("31337", "fixture-1", "sample-hash", 7500, 123, "SAMPLE-not-a-proof")
        db.cacheDao().insertThreat(first)
        db.cacheDao().insertThreat(first.copy(threatId = "fixture-2"))
        db.cacheDao().insertThreat(first.copy(chainId = "31338"))
        assertEquals(first, db.cacheDao().findThreat("31337", "fixture-1"))
        assertNotNull(db.cacheDao().findThreat("31337", "fixture-2"))
        assertNotNull(db.cacheDao().findThreat("31338", "fixture-1"))
    }
    @Test fun duplicateThreatDoesNotOverwriteOriginal() = runBlocking {
        val first = CachedThreat("31337", "fixture-1", "sample-hash", 7500, 123, "SAMPLE-not-a-proof")
        db.cacheDao().insertThreat(first)
        val failure = runCatching { db.cacheDao().insertThreat(first.copy(riskScore = 9000)) }
        assertTrue(failure.isFailure)
        assertEquals(first, db.cacheDao().findThreat("31337", "fixture-1"))
    }
    @Test fun duplicateEventsAreIgnoredAndCursorIsPersisted() = runBlocking {
        val event = CachedEvent("31337", "sample-tx", 0, 5, "sample-block")
        assertTrue(db.cacheDao().insertEvent(event) > 0)
        assertEquals(-1L, db.cacheDao().insertEvent(event))
        assertTrue(db.cacheDao().insertEvent(event.copy(logIndex = 1)) > 0)
        db.cacheDao().saveCursor(SyncCursor("31337", 5))
        assertEquals(5L, db.cacheDao().findCursor("31337")?.lastProcessedBlock)
    }
}
