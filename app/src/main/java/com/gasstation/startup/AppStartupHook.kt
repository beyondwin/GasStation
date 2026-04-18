package com.gasstation.startup

import android.app.Application

fun interface AppStartupHook {
    fun run(application: Application)
}
