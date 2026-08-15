package com.gasstation.core.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrandAssetLintPolicyTest {
    @Test
    fun `brand assets use nodpi without lint suppression`() {
        val moduleRoot = projectFile("core/designsystem", ".")
        assertFalse(moduleRoot.resolve("lint.xml").exists())
        assertFalse(moduleRoot.resolve("lint-baseline.xml").exists())
        BRAND_ASSETS.forEach { asset ->
            assertTrue(moduleRoot.resolve("src/main/res/drawable-nodpi/$asset").isFile)
            assertFalse(moduleRoot.resolve("src/main/res/drawable/$asset").exists())
            assertFalse(moduleRoot.resolve("src/main/res/drawable-mdpi/$asset").exists())
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
