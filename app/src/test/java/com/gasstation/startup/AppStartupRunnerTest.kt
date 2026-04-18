package com.gasstation.startup

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStartupRunnerTest {
    @Test
    fun `runner executes every registered hook`() {
        val calls = mutableListOf<String>()
        val runner = AppStartupRunner(
            hooks = setOf(
                AppStartupHook { calls += "demo" },
                AppStartupHook { calls += "prod" },
            ),
        )

        runner.run(application = Application())

        assertEquals(setOf("demo", "prod"), calls.toSet())
    }
}
