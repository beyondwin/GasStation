package com.gasstation

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupPolicyResourceTest {
    @Test
    fun `application disables android backup and data extraction`() {
        val manifestFile = projectFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
        val manifest = manifestFile.readText()
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
        val application = document.getElementsByTagName("application").item(0)

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:fullBackupContent="))
        assertFalse(manifest.contains("android:dataExtractionRules="))
        assertEquals(
            "DataExtractionRules",
            application.attributes.getNamedItemNS(TOOLS_NAMESPACE, "ignore")?.nodeValue,
        )
        assertEquals(1, Regex("tools:ignore=\"DataExtractionRules\"").findAll(manifest).count())
        assertTrue(
            manifest.contains(
                "Backup is disabled, so backup/data-transfer rule resources are intentionally absent.",
            ),
        )
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

    private fun projectFile(vararg candidates: String): File = candidates.map(::File).firstOrNull(File::exists)
        ?: error("Could not find any project file candidate: ${candidates.joinToString()}")

    private fun projectFileExists(vararg candidates: String): Boolean = candidates.any { File(it).exists() }

    companion object {
        private const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
    }
}
