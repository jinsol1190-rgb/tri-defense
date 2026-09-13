package org.tridefense.android.di

import android.content.Context
import androidx.room.Room
import org.tridefense.android.ai.AiAnalyzer
import org.tridefense.android.ai.UnavailableAiAnalyzer
import org.tridefense.android.audio.AudioSource
import org.tridefense.android.audio.LiveAudioSource
import org.tridefense.android.audio.SampleAudioSource
import org.tridefense.android.registry.ThreatDatabase
import org.tridefense.android.screening.PlaceholderScreeningPolicy
import org.tridefense.android.screening.ScreeningPolicy

/** Manual constructor injection: no service locator outside the composition roots. */
interface AppContainer {
    val database: ThreatDatabase
    val liveAudioSource: AudioSource
    val sampleAudioSource: AudioSource
    val aiAnalyzer: AiAnalyzer
    val screeningPolicy: ScreeningPolicy
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val applicationContext = context.applicationContext
    // Lazy: an incoming screening callback must not open a database on the main thread.
    override val database: ThreatDatabase by lazy {
        Room.databaseBuilder(applicationContext, ThreatDatabase::class.java, "threat-cache.db")
            .addMigrations(ThreatDatabase.MIGRATION_1_2)
            .build()
    }
    override val liveAudioSource: AudioSource = LiveAudioSource()
    override val sampleAudioSource: AudioSource = SampleAudioSource()
    override val aiAnalyzer: AiAnalyzer = UnavailableAiAnalyzer()
    override val screeningPolicy: ScreeningPolicy = PlaceholderScreeningPolicy()
}
