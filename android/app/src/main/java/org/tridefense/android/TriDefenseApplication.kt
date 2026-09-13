package org.tridefense.android

import android.app.Application
import org.tridefense.android.di.AppContainer
import org.tridefense.android.di.DefaultAppContainer

/** REAL composition root. Dependencies remain inside the Android module. */
class TriDefenseApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
