package org.tridefense.android.integration

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.tridefense.android.R
import org.tridefense.android.registry.ThreatDatabase

/** Debug-only, visibly SAMPLE integration control. Absent from release builds. */
class IntegrationActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val gate = Mutex()
    private lateinit var state: TextView
    private lateinit var fixtureInput: EditText
    private lateinit var submitButton: Button
    private lateinit var syncButton: Button
    private var integration: RegistryIntegration? = null
    private var database: ThreatDatabase? = null
    private var submission: JSONObject? = null
    private var fixture: MockFixture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        val scroll = ScrollView(this).apply { addView(panel) }
        setContentView(scroll)
        panel.addView(TextView(this).apply { setText(R.string.integration_heading); textSize = 24f })
        panel.addView(TextView(this).apply { setText(R.string.integration_notice); textSize = 16f })
        fixtureInput = EditText(this).apply { setHint(R.string.integration_fixture_hint); minLines = 3; maxLines = 5 }
        panel.addView(fixtureInput)
        panel.addView(Button(this).apply { setText(R.string.integration_load); setOnClickListener { loadFixture() } })
        submitButton = Button(this).apply {
            setText(R.string.integration_submit); isEnabled = false
            setOnClickListener { perform(submit = true) }
        }
        panel.addView(submitButton)
        syncButton = Button(this).apply {
            setText(R.string.integration_sync); isEnabled = false
            setOnClickListener { perform(submit = false) }
        }
        panel.addView(syncButton)
        state = TextView(this).apply { setText(R.string.integration_waiting); textSize = 14f; setTextIsSelectable(true) }
        panel.addView(state)
        intent.getStringExtra("fixture")?.let { fixtureInput.setText(it); loadFixture() }
    }

    private fun loadFixture() {
        if (integration != null) return // Restart this debug screen to change deployment context.
        try {
            val config = MockFixture.parse(fixtureInput.text.toString())
            val db = Room.databaseBuilder(applicationContext, ThreatDatabase::class.java, "mock-integration-${config.registryAddress}.db")
                .addMigrations(ThreatDatabase.MIGRATION_1_2).build()
            fixture = config
            database = db
            integration = RegistryIntegration(config, HttpBackendClient(config), db)
            fixtureInput.isEnabled = false
            submitButton.isEnabled = true
            syncButton.isEnabled = true
            state.text = "MOCK_PROOF fixture loaded. Registry: ${config.registryAddress}\nNo submission sent."
        } catch (error: Exception) { state.text = "Fixture error: ${error.message}" }
    }

    private fun perform(submit: Boolean) {
        val worker = integration ?: return
        submitButton.isEnabled = false
        syncButton.isEnabled = false
        state.text = if (submit) "MOCK_PROOF · submitting to Backend…" else "MOCK_PROOF · refreshing event stream…"
        scope.launch {
            try {
                val evidence = withContext(Dispatchers.IO) {
                    gate.withLock {
                        if (submit) submission = worker.submit()
                        var current = submission
                        if (current != null) {
                            repeat(20) {
                                if (current?.getString("status") == "SUBMITTED") {
                                    delay(250)
                                    current = worker.status(current!!.getString("submissionId"))
                                }
                            }
                            submission = current
                        }
                        var count = worker.synchronize()
                        // A Backend page covers 100 blocks. Continue a bounded P0 catch-up if needed.
                        val id = current?.getString("threatId")
                        repeat(20) {
                            if (id != null && current?.getString("status") == "REGISTERED" && database!!.cacheDao().findThreat("31337", id) == null) {
                                count = worker.synchronize()
                            }
                        }
                        val record = id?.let { database!!.cacheDao().findThreat("31337", it) }
                        JSONObject().apply {
                            put("proofMode", "MOCK_PROOF")
                            put("status", current?.getString("status") ?: "SYNCED")
                            put("submissionId", current?.getString("submissionId"))
                            put("txHash", current?.optString("txHash"))
                            put("threatId", id)
                            put("roomThreatCount", count)
                            put("roomEventCount", database!!.cacheDao().eventCount("31337"))
                            put("roomThreatId", record?.threatId)
                            put("roomVoiceprintHash", record?.voiceprintHash)
                            put("roomProof", record?.zkProof)
                            put("backendCursor", database!!.cacheDao().findCursor("31337")?.backendCursor)
                        }.also {
                            val temporary = applicationContext.getFileStreamPath("integration-evidence.tmp")
                            temporary.writeText(it.toString(2))
                            check(temporary.renameTo(applicationContext.getFileStreamPath("integration-evidence.json")))
                        }
                    }
                }
                state.text = "MOCK_PROOF · Backend / Room result\n${evidence.toString(2)}"
            } catch (error: Exception) { state.text = "MOCK_PROOF · connection failed; cache is not current\n${error.message}" }
            finally { submitButton.isEnabled = true; syncButton.isEnabled = true }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        // Room is scoped to this short-lived debug activity; close after active IO work releases its gate.
        val db = database
        CoroutineScope(Dispatchers.IO).launch { gate.withLock { db?.close() } }
        super.onDestroy()
    }
}
