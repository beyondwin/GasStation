package com.gasstation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

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
    fun `static splash and monochrome icons share one refined silhouette`() {
        val colorVector = projectFile("app/src/main/res/drawable/ic_brand_drop.xml").readText()
        val monochromeVector = projectFile("app/src/main/res/drawable/ic_brand_drop_monochrome.xml").readText()
        val splashForeground = projectFile("app/src/main/res/drawable/ic_splash_foreground.xml").readText()

        assertTrue(colorVector.contains("""android:name="drop_group""""))
        assertTrue(colorVector.contains("""android:name="drop_path""""))
        assertEquals(pathData(colorVector), pathData(monochromeVector))
        assertTrue(splashForeground.contains("""android:drawable="@drawable/ic_brand_drop""""))
        assertFalse(projectFileExists("app/src/main/res/drawable-v31/ic_splash_signal_pulse_vector.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_alpha.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_ring_scale.xml"))
    }

    @Test
    fun `android 12 splash has no startup blocking animator override`() {
        assertFalse(projectFileExists("app/src/main/res/drawable-v31/ic_splash_foreground.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_drop_scale.xml"))
        assertFalse(projectFileExists("app/src/main/res/animator-v31/splash_drop_alpha.xml"))
    }

    @Test
    fun `legacy launcher pngs are regenerated at canonical densities`() {
        val oldHashes = setOf(
            "0a50c3a058b382319ca5c2bcd3d72e682dc41f3eb80db2c29a2d43267e95faa7",
            "97ae665b121e5c0ef66ffccee8809fb48c4c34e9db171aab8b9be78f647a0db2",
            "82abb7f6f27e575830b6c4c04b415edc6e388fff172bf63dae4104e393a00dc5",
            "b790283d9fcc5ba770dc9e4b496ab12c9bef010a10434ad18bc0b358333a392a",
            "5a1660bc3a8967bb60d85de229a3e8e603a5eaed53520044d0b5f59131831197",
        )
        val expectedSizes = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )

        expectedSizes.forEach { (density, expectedSize) ->
            val file = projectFile("app/src/main/res/mipmap-$density/ic_launcher.png")
            val image = ImageIO.read(file)
            assertNotNull("$density launcher must decode", image)
            assertEquals("$density width", expectedSize, image.width)
            assertEquals("$density height", expectedSize, image.height)
            assertFalse("$density launcher still uses the old asset", sha256(file) in oldHashes)
        }
    }

    private fun pathData(xml: String): String = requireNotNull(
        Regex("""android:pathData="([^"]+)"""").find(xml),
    ).groupValues[1]

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
