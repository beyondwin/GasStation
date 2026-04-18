package com.gasstation

import android.app.Application
import com.gasstation.startup.AppStartupRunner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var appStartupRunner: AppStartupRunner

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("TimberInitializer is initialized.")
        }
        appStartupRunner.run(this)
    }
}
