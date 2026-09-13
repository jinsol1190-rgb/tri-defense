package org.tridefense.android.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioSourceTest {
    private fun sample() = AudioInput("sample-session", AudioSourceKind.SAMPLE, listOf(0.1f, -0.1f), 48000, 1, 0)

    @Test fun liveCaptureIsExplicitlyUnavailable() = runBlocking {
        assertTrue(LiveAudioSource().read() is AudioReadResult.Unavailable)
    }
    @Test fun missingSampleIsNotFabricated() = runBlocking {
        assertTrue(SampleAudioSource().read() is AudioReadResult.Unavailable)
    }
    @Test fun suppliedSampleIsReturnedWithoutTransformation() = runBlocking {
        val input = sample()
        assertSame(input, (SampleAudioSource(input).read() as AudioReadResult.Available).input)
    }
    @Test fun liveInputCannotBeRelabeledAsSample() {
        assertThrows(IllegalArgumentException::class.java) {
            SampleAudioSource(sample().copy(source = AudioSourceKind.LIVE))
        }
    }
    @Test fun malformedAudioIsRejected() {
        for (samples in listOf(emptyList(), listOf(Float.NaN), listOf(Float.POSITIVE_INFINITY), listOf(1.1f))) {
            assertThrows(IllegalArgumentException::class.java) { sample().copy(samples = samples) }
        }
        assertThrows(IllegalArgumentException::class.java) { sample().copy(sampleRateHz = 0) }
        assertThrows(IllegalArgumentException::class.java) { sample().copy(channels = 0) }
        assertThrows(IllegalArgumentException::class.java) { sample().copy(channels = 3) }
        assertThrows(IllegalArgumentException::class.java) { sample().copy(sessionId = " ") }
    }
}
