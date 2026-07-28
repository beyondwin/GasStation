package com.gasstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppIconSourceContractTest {
    @Test
    fun `launcher palette uses exact urban signal tokens`() {
        val colors = projectFile("app/src/main/res/values/colors.xml").readText()

        assertTrue(colors.contains("""<color name="ic_launcher_background">#FFDC00</color>"""))
        assertTrue(colors.contains("""<color name="ic_launcher_foreground_fill">#FF222222</color>"""))
        assertFalse(colors.contains("#FFFED70A"))
        assertFalse(colors.contains("#FF111111"))
    }

    @Test
    fun `static animated and monochrome icons share one refined silhouette`() {
        val colorVector = projectFile("app/src/main/res/drawable/ic_brand_drop.xml").readText()
        val monochromeVector = projectFile("app/src/main/res/drawable/ic_brand_drop_monochrome.xml").readText()
        val avd = projectFile("app/src/main/res/drawable-v31/ic_splash_foreground.xml").readText()

        assertTrue(colorVector.contains("""android:name="drop_group""""))
        assertTrue(colorVector.contains("""android:name="drop_path""""))
        assertEquals(pathData(colorVector), pathData(monochromeVector))
        assertTrue(avd.contains("""android:drawable="@drawable/ic_brand_drop""""))
        assertFalse(avd.contains("ring"))
        assertFalse(projectFileExists("app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_alpha.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_scale.xml"))
    }

    private fun pathData(xml: String): String = requireNotNull(
        Regex("""android:pathData="([^"]+)"""").find(xml),
    ).groupValues[1]

    private fun projectFile(path: String): File = File(projectRoot(), path)
        .takeIf(File::exists)
        ?: error("Could not find project file: $path from ${projectRoot()}")

    private fun projectFileExists(path: String): Boolean = File(projectRoot(), path).exists()

    private fun projectRoot(): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Could not find project root from $workingDirectory")
    }
}
