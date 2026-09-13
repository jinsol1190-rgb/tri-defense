package org.tridefense.android.audio

/** Android-local mirror of specification section 7; no API serialization yet. */
enum class AudioSourceKind { LIVE, SAMPLE }

data class AudioInput(
    val sessionId: String,
    val source: AudioSourceKind,
    val samples: List<Float>,
    val sampleRateHz: Int,
    val channels: Int,
    val capturedAt: Long,
) {
    init {
        require(sessionId.isNotBlank())
        require(sampleRateHz > 0 && channels > 0)
        require(samples.isNotEmpty() && samples.size % channels == 0)
        require(samples.all { it.isFinite() && it in -1f..1f })
        require(capturedAt >= 0)
    }
}

sealed interface AudioReadResult {
    data class Available(val input: AudioInput) : AudioReadResult
    data class Unavailable(val reason: String) : AudioReadResult
}

interface AudioSource {
    val kind: AudioSourceKind
    suspend fun read(): AudioReadResult
}

/** SAMPLE placeholder: no AudioRecord, microphone permission or call capture. */
class LiveAudioSource : AudioSource {
    override val kind = AudioSourceKind.LIVE
    override suspend fun read(): AudioReadResult = AudioReadResult.Unavailable(
        "LIVE capture is not implemented; device input and permissions require verification.",
    )
}

/** SAMPLE adapter: returns only explicitly supplied samples, never fabricated input. */
class SampleAudioSource(private val input: AudioInput? = null) : AudioSource {
    init { require(input == null || input.source == AudioSourceKind.SAMPLE) }
    override val kind = AudioSourceKind.SAMPLE
    override suspend fun read(): AudioReadResult = input?.let(AudioReadResult::Available)
        ?: AudioReadResult.Unavailable("No SAMPLE audio has been supplied.")
}
