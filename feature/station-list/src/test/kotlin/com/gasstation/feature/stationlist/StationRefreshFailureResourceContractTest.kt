package com.gasstation.feature.stationlist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StationRefreshFailureResourceContractTest {
    @Test
    fun `refresh failure copy mapping remains compiler exhaustive`() {
        val source = stationListViewModelSource()
        val signature = "private fun StationRefreshFailureReason?.refreshFailureResource(): StringResource"
        val mappingStart = source.indexOf(signature)

        assertTrue("refresh-failure copy mapping is missing", mappingStart >= 0)

        val mapping = source.substring(mappingStart)
        assertTrue(
            "mapping must use a nullable sealed when expression without a catch-all",
            mapping.startsWith("$signature = when (this) {"),
        )
        assertFalse("an else branch defeats sealed-subtype compile protection", mapping.contains("else"))
        assertFalse("an equality if defeats sealed-subtype compile protection", mapping.contains("if ("))

        listOf(
            "StationRefreshFailureReason.Timeout",
            "StationRefreshFailureReason.Network",
            "StationRefreshFailureReason.InvalidPayload",
            "is StationRefreshFailureReason.Http",
            "StationRefreshFailureReason.Unknown",
            "null",
        ).forEach { branch ->
            assertTrue("missing explicit exhaustive branch: $branch", mapping.contains(branch))
        }
    }

    private fun stationListViewModelSource(): String {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val sourcePath = "feature/station-list/src/main/kotlin/com/gasstation/feature/stationlist/StationListViewModel.kt"
        val sourceFile = generateSequence(File(workingDirectory)) { it.parentFile }
            .map { root -> File(root, sourcePath) }
            .firstOrNull(File::isFile)

        return requireNotNull(sourceFile) { "could not locate $sourcePath from $workingDirectory" }
            .readText()
    }
}
