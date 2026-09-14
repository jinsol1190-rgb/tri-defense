package org.tridefense.android.ui

import android.app.role.RoleManager
import org.robolectric.Shadows.shadowOf
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.tridefense.android.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35])
class MainActivityTest {
    @Test fun ungrantedRoleDoesNotClaimProtection() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            shadowOf(activity.getSystemService(RoleManager::class.java)).addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
            controller.pause().resume()
            assertEquals(activity.getString(R.string.role_available), activity.findViewById<TextView>(R.id.role_status).text.toString())
            assertTrue(activity.findViewById<Button>(R.id.request_role).isEnabled)
        }
    }
    @Test fun heldRoleIsVisibleWithoutClaimingBlocking() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val role = shadowOf(activity.getSystemService(RoleManager::class.java))
            role.addAvailableRole(RoleManager.ROLE_CALL_SCREENING)
            role.addHeldRole(RoleManager.ROLE_CALL_SCREENING)
            controller.pause().resume()
            assertEquals(activity.getString(R.string.role_held), activity.findViewById<TextView>(R.id.role_status).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.request_role).isEnabled)
        }
    }
    @Test fun unavailableRoleIsVisibleAndCannotBeRequested() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertEquals(activity.getString(R.string.role_unavailable), activity.findViewById<TextView>(R.id.role_status).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.request_role).isEnabled)
            assertTrue(activity.getString(R.string.capabilities).contains("모든 통화 허용"))
        }
    }
}
