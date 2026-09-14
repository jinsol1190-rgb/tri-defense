package org.tridefense.android.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.tridefense.android.audio.AudioInput
import org.tridefense.android.audio.AudioSourceKind

class AiAnalyzerTest {
    @Test fun placeholdersNeverReturnSuccessfulInferenceOrMatch() = runBlocking {
        val input = AudioInput("synthetic", AudioSourceKind.SAMPLE, listOf(0f), 48000, 1, 0)
        val ai = UnavailableAiAnalyzer()
        assertTrue(ai.analyzeLightweight(input) is AnalysisResult.Unsupported)
        assertTrue(ai.analyzePrecise(input) is AnalysisResult.Unsupported)
        assertTrue(ai.matchVoiceprint(input, emptyList()) is AnalysisResult.Unsupported)
    }
    @Test fun scoreContractUsesBasisPoints() {
        for (score in listOf(0, 10_000)) {
            assertEquals(score, LightweightResult("s", "MOCK-fixture", score, false, 0, ModelMode.MOCK).riskScore)
        }
        for (score in listOf(-1, 10_001)) {
            assertThrows(IllegalArgumentException::class.java) {
                LightweightResult("s", "MOCK-fixture", score, false, 0, ModelMode.MOCK)
            }
        }
    }
}
