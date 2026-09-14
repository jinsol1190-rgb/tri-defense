package org.tridefense.android.ui

import android.app.Activity
import android.app.role.RoleManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.tridefense.android.R

/** SAMPLE placeholder UI: exposes real role state, never fabricated detection/protection. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        findViewById<Button>(R.id.request_role).setOnClickListener {
            val manager = getSystemService(RoleManager::class.java)
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                !manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                @Suppress("DEPRECATION")
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), ROLE_REQUEST)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(RoleManager::class.java)
        val available = manager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        val held = available && manager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        findViewById<TextView>(R.id.role_status).setText(when {
            held -> R.string.role_held
            available -> R.string.role_available
            else -> R.string.role_unavailable
        })
        findViewById<Button>(R.id.request_role).isEnabled = available && !held
    }

    companion object { private const val ROLE_REQUEST = 1 }
}
