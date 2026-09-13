package org.tridefense.android.integration

import org.json.JSONObject
import org.tridefense.android.registry.CachedEvent
import org.tridefense.android.registry.CachedThreat
import org.tridefense.android.registry.SyncCursor
import org.tridefense.android.registry.ThreatDatabase

/** Coordinates existing API calls and Room cache writes. Runs on an IO coroutine, never a screening callback. */
class RegistryIntegration(
    private val fixture: MockFixture,
    private val transport: BackendTransport,
    private val database: ThreatDatabase,
) {
    fun submit(): JSONObject = transport.request("/v1/threat-submissions", fixture.request)
    fun status(id: String): JSONObject = transport.request("/v1/threat-submissions/" + encoded(id))

    suspend fun synchronize(): Int {
        val dao = database.cacheDao()
        val previous = dao.findCursor("31337")
        if (previous != null && previous.registryAddress != fixture.registryAddress) dao.resetChain("31337")
        val cursor = dao.findCursor("31337")?.backendCursor
        val page = try {
            transport.request("/v1/registry/events" + (cursor?.let { "?cursor=" + encoded(it) } ?: ""))
        } catch (error: BackendFailure) {
            if (error.code in setOf("CURSOR_REORG", "CHAIN_CHANGED", "INVALID_CURSOR")) dao.resetChain("31337")
            throw error
        }
        require(page.getString("chainId") == "31337" && page.getString("registryAddress").lowercase() == fixture.registryAddress &&
            page.getString("proofMode") == "MOCK_PROOF")
        val threats = mutableListOf<CachedThreat>()
        val events = mutableListOf<CachedEvent>()
        val list = page.getJSONArray("events")
        for (index in 0 until list.length()) {
            val event = list.getJSONObject(index)
            require(event.getString("chainId") == "31337")
            val args = event.getJSONObject("args")
            when (event.getString("event")) {
                "ThreatRegistered" -> {
                    val id = args.getString("threatId")
                    val record = transport.request("/v1/registry/threats/" + encoded(id))
                    require(record.getString("threatId") == id && record.getString("voiceprintHash") == args.getString("voiceprintHash") &&
                        record.getInt("riskScore") == args.getInt("riskScore") && record.getString("registeredAt") == args.getString("registeredAt")) {
                        "Event and Registry record mismatch"
                    }
                    require(record.getInt("riskScore") in 0..10000)
                    threats += CachedThreat("31337", id, record.getString("voiceprintHash"), record.getInt("riskScore"),
                        record.getString("registeredAt").toLong(), record.getString("zkProof"))
                }
                "BlacklistPromoted" -> Unit // Event observed only. Call blocking/promotion integration is outside this task.
                else -> error("Unsupported Registry event")
            }
            events += CachedEvent("31337", event.getString("txHash"), event.getString("logIndex").toInt(),
                event.getString("blockNumber").toLong(), event.getString("blockHash"))
        }
        dao.applyPage(threats, events, SyncCursor("31337", page.getString("throughBlock").toLong(), fixture.registryAddress, page.getString("nextCursor")))
        return dao.threatCount("31337")
    }
}
