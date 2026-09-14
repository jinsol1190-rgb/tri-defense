package org.tridefense.android

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tridefense.android.integration.IntegrationActivity

/** REAL Android HTTP + Room + existing Backend/Mock contract integration, not a host substitute. */
@RunWith(AndroidJUnit4::class)
class MockIntegrationDeviceTest {
    @Test fun androidSubmitsAndReceivesSameRegisteredThreat() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val text = arguments.getString("fixture")
        assumeTrue("Run integration/run.py to supply the live MOCK fixture", text != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = context.getFileStreamPath("integration-evidence.json")
        file.delete()
        val intent = Intent(context, IntegrationActivity::class.java).putExtra("fixture", text)
        ActivityScenario.launch<IntegrationActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                fun click(view: android.view.View): Boolean {
                    if (view is android.widget.Button && view.text.toString() == "Submit MOCK threat") { view.performClick(); return true }
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) if (click(view.getChildAt(i))) return true
                    return false
                }
                assertTrue(click(activity.window.decorView))
            }
            val deadline = System.currentTimeMillis() + 45000
            while (!file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("Android did not produce integration evidence", file.exists())
            val result = JSONObject(file.readText())
            assertEquals("MOCK_PROOF", result.getString("proofMode"))
            assertEquals("REGISTERED", result.getString("status"))
            assertEquals(result.getString("threatId"), result.getString("roomThreatId"))
            assertEquals(JSONObject(text!!).getJSONObject("submission").getString("voiceprintHash"), result.getString("roomVoiceprintHash"))
            assertEquals(JSONObject(text).getJSONObject("submission").getString("proof"), result.getString("roomProof"))
            assertEquals(1, result.getInt("roomThreatCount"))
            assertEquals(1, result.getInt("roomEventCount"))
        }
    }
}
