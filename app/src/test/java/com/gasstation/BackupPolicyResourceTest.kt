package com.gasstation

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupPolicyResourceTest {
    @Test
    fun `application disables android backup and data extraction`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
            .readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:fullBackupContent="))
        assertFalse(manifest.contains("android:dataExtractionRules="))
    }

    @Test
    fun `sample backup rule resources are not kept without a backup policy`() {
        assertFalse(projectFileExists("app/src/main/res/xml/backup_rules.xml", "src/main/res/xml/backup_rules.xml"))
        assertFalse(
            projectFileExists(
                "app/src/main/res/xml/data_extraction_rules.xml",
                "src/main/res/xml/data_extraction_rules.xml",
            ),
        )
    }

    private fun projectFile(vararg candidates: String): File =
        candidates.map(::File).firstOrNull(File::exists)
            ?: error("Could not find any project file candidate: ${candidates.joinToString()}")

    private fun projectFileExists(vararg candidates: String): Boolean =
        candidates.any { File(it).exists() }
}
