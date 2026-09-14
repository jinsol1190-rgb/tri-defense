package org.tridefense.android.screening

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tridefense.android.TriDefenseApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class ScreeningTest {
    @Test fun placeholderAllowsAndKeepsSystemLogAndNotification() {
        val response = TriDefenseCallScreeningService.buildResponse(PlaceholderScreeningPolicy())
        assertFalse(response.disallowCall)
        assertFalse(response.rejectCall)
        assertFalse(response.skipCallLog)
        assertFalse(response.skipNotification)
    }
    @Test fun failedPolicyDoesNotBlockAnUnverifiedCaller() {
        val response = TriDefenseCallScreeningService.buildResponse { error("test failure") }
        assertFalse(response.disallowCall)
    }
    @Test fun explicitTestPolicyMapsToAndroidBlockResponse() {
        // MOCK unit fixture only; production DI does not supply this policy.
        val response = TriDefenseCallScreeningService.buildResponse { true }
        assertTrue(response.disallowCall)
        assertTrue(response.rejectCall)
    }
    @Test fun serviceIsProtectedBySystemBindingPermission() {
        val app = ApplicationProvider.getApplicationContext<TriDefenseApplication>()
        val info = app.packageManager.getServiceInfo(ComponentName(app, TriDefenseCallScreeningService::class.java), 0)
        assertTrue(info.exported)
        assertEquals(Manifest.permission.BIND_SCREENING_SERVICE, info.permission)
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        // Debug integration is allowed HTTP; release manifest has no INTERNET permission.
        assertEquals(app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
            permissions.contains(Manifest.permission.INTERNET))
        assertFalse(permissions.contains(Manifest.permission.RECORD_AUDIO))
    }
}
