package org.tridefense.android.screening

/** Nonblocking local decision boundary. Phone lookup/promotion policy is not connected in P0. */
fun interface ScreeningPolicy {
    fun shouldBlock(): Boolean
}

/** SAMPLE placeholder. Missing data must not be interpreted as a malicious caller. */
class PlaceholderScreeningPolicy : ScreeningPolicy {
    override fun shouldBlock() = false
}
