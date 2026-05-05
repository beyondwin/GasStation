package com.gasstation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemBarPolicyTest {
    @Test
    fun `system bar setup is owned by app activity`() {
        val mainActivity = projectFile("app/src/main/java/com/gasstation/MainActivity.kt")
            .readText()

        assertTrue(mainActivity.contains("enableEdgeToEdge("))
        assertTrue(mainActivity.contains("SystemBarStyle"))
        assertTrue(mainActivity.contains("useDarkIcons"))
        assertTrue(mainActivity.contains("SystemBarStyle.light("))
        assertTrue(mainActivity.contains("SystemBarStyle.dark("))
    }

    @Test
    fun `designsystem theme does not write deprecated status bar color`() {
        val theme = projectFile("core/designsystem/src/main/kotlin/com/gasstation/core/designsystem/Theme.kt")
            .readText()

        assertFalse(theme.contains("statusBarColor"))
        assertFalse(theme.contains("@Suppress(\"DEPRECATION\")"))
    }

    private fun projectFile(path: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull(File::exists)
            ?: error("Could not find project file: $path from $workingDirectory")
    }
}
