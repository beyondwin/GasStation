package com.gasstation.tools.demoseed

import java.io.File
import kotlin.test.Test

class CommittedDemoSeedAssetTest {
    @Test
    fun `committed demo asset satisfies generator invariants`() {
        val assetPath = requireNotNull(System.getProperty("demo.seed.asset.path")) {
            "demo.seed.asset.path must point to the committed asset"
        }
        val document = DemoSeedJsonWriter.gson.fromJson(
            File(assetPath).readText(Charsets.UTF_8),
            DemoSeedDocument::class.java,
        )

        DemoSeedAssetVerifier.verify(document)
    }
}
