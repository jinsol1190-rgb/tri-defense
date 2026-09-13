package org.tridefense.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.tridefense.android.registry.ThreatDatabase

/** REAL device storage smoke check; does not simulate a carrier call or proof. */
@RunWith(AndroidJUnit4::class)
class DatabaseDeviceTest {
    @Test fun emptyCacheOnDevice() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ThreatDatabase::class.java).build()
        try { assertNull(db.cacheDao().findThreat("31337", "sample-id")) } finally { db.close() }
    }
}
