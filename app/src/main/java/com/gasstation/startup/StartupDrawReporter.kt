package com.gasstation.startup

class StartupDrawReporter(private val reportFullyDrawn: () -> Unit) {
    private var reported = false

    fun reportFirstContentDrawn() {
        if (reported) return
        reported = true
        reportFullyDrawn()
    }
}
