package com.gasstation.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BrandAssetLintPolicyTest {
    @Test
    fun `single density brand assets keep one path-scoped lint exception`() {
        val moduleRoot = projectFile("core/designsystem", ".")
        val lintPolicy = moduleRoot.resolve("lint.xml")
        assertTrue(lintPolicy.isFile)

        val document =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }.newDocumentBuilder().parse(lintPolicy)

        val issues = document.getElementsByTagName("issue")
        assertEquals(1, issues.length)
        val issue = issues.item(0)
        assertEquals("IconMissingDensityFolder", issue.attributes.getNamedItem("id").nodeValue)

        val ignores = document.getElementsByTagName("ignore")
        assertEquals(1, ignores.length)
        val ignore = ignores.item(0)
        assertEquals("src/main/res", ignore.attributes.getNamedItem("path").nodeValue)
        assertFalse(ignore.attributes.getNamedItem("regexp") != null)

        assertFalse(moduleRoot.resolve("lint-baseline.xml").exists())
        BRAND_ASSETS.forEach { asset ->
            assertTrue(moduleRoot.resolve("src/main/res/drawable-mdpi/$asset").isFile)
            assertFalse(moduleRoot.resolve("src/main/res/drawable/$asset").exists())
            assertFalse(moduleRoot.resolve("src/main/res/drawable-nodpi/$asset").exists())
        }
    }

    private fun projectFile(rootRelativePath: String, moduleRelativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        val repositoryRoot =
            generateSequence(workingDirectory) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").isFile }
                ?: error("Could not locate repository root from $workingDirectory")
        return repositoryRoot.resolve(rootRelativePath).resolve(moduleRelativePath)
    }

    private companion object {
        val BRAND_ASSETS =
            listOf(
                "ic_e1g.png",
                "ic_etc.png",
                "ic_gsc.png",
                "ic_hdo.png",
                "ic_rtx.png",
                "ic_ske.png",
                "ic_skg.png",
                "ic_sol.png",
            )
    }
}
