package org.tridefense.android.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import org.tridefense.android.TriDefenseApplication

/** REAL Android service wiring; SAMPLE allow-all policy, no protection claims. */
class TriDefenseCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        // Immediate bounded path: no database open, I/O, inference or network call.
        val policy = (application as TriDefenseApplication).container.screeningPolicy
        respondToCall(callDetails, buildResponse(policy))
    }

    companion object {
        internal fun buildResponse(policy: ScreeningPolicy): CallResponse {
            val block = try { policy.shouldBlock() } catch (_: RuntimeException) { false }
            return CallResponse.Builder()
                .setDisallowCall(block)
                .setRejectCall(block)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        }
    }
}
