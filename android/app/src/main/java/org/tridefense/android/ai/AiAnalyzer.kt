package org.tridefense.android.ai

import org.tridefense.android.audio.AudioInput

const val MAX_RISK_SCORE = 10_000

enum class ModelMode { SAMPLE, MOCK, REAL_MODEL }
enum class WatermarkStatus { DETECTED, NOT_DETECTED, UNSUPPORTED }

data class LightweightResult(
    val sessionId: String,
    val modelVersion: String,
    val riskScore: Int,
    val needsPrecise: Boolean,
    val elapsedMs: Long,
    val mode: ModelMode,
) {
    init { require(riskScore in 0..MAX_RISK_SCORE && elapsedMs >= 0) }
}

data class PreciseResult(
    val sessionId: String,
    val modelVersion: String,
    val preprocessingVersion: String,
    val riskScore: Int,
    val voiceprintHash: String,
    val watermarkStatus: WatermarkStatus,
    val artifactScore: Double,
    val elapsedMs: Long,
    val mode: ModelMode,
) {
    init {
        require(riskScore in 0..MAX_RISK_SCORE && elapsedMs >= 0)
        require(artifactScore.isFinite()) // Scale is unspecified; do not invent one.
    }
}

data class VoiceprintMatch(
    val matched: Boolean,
    val matchedHash: String?,
    val methodVersion: String,
    val elapsedMs: Long,
) {
    init {
        require(elapsedMs >= 0)
        require(matched == (matchedHash != null))
    }
}

sealed interface AnalysisResult<out T> {
    data class Available<T>(val value: T) : AnalysisResult<T>
    data class Unsupported(val reason: String) : AnalysisResult<Nothing>
}

/** Interface contracts only. No runtime model or risk threshold is selected. */
interface AiAnalyzer {
    suspend fun analyzeLightweight(input: AudioInput): AnalysisResult<LightweightResult>
    suspend fun analyzePrecise(input: AudioInput): AnalysisResult<PreciseResult>
    suspend fun matchVoiceprint(input: AudioInput, knownVoiceprints: List<String>): AnalysisResult<VoiceprintMatch>
}

/** SAMPLE placeholder: unsupported is never a low score, a match, or a HUMAN verdict. */
class UnavailableAiAnalyzer : AiAnalyzer {
    override suspend fun analyzeLightweight(input: AudioInput) = unsupported()
    override suspend fun analyzePrecise(input: AudioInput) = unsupported()
    override suspend fun matchVoiceprint(input: AudioInput, knownVoiceprints: List<String>) = unsupported()
    private fun unsupported() = AnalysisResult.Unsupported("AI and voiceprint matching are not implemented.")
}
