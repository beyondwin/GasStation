package com.gasstation.startup

import android.app.Application
import javax.inject.Inject

class AppStartupRunner @Inject constructor(
    private val hooks: Set<@JvmSuppressWildcards AppStartupHook>,
) {
    fun run(application: Application) {
        hooks.forEach { hook -> hook.run(application) }
    }
}
