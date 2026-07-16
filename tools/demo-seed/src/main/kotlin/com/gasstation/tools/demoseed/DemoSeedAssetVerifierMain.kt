package com.gasstation.tools.demoseed

import java.io.File

fun main(args: Array<String>) {
    require(args.size == 1) { "Expected committed demo seed asset path argument." }

    val assetFile = File(args.single())
    require(assetFile.isFile) { "Demo seed asset does not exist: ${assetFile.absolutePath}" }
    val document = DemoSeedJsonWriter.gson.fromJson(
        assetFile.readText(Charsets.UTF_8),
        DemoSeedDocument::class.java,
    )

    DemoSeedAssetVerifier.verify(document)
    println("Verified demo seed asset: ${assetFile.canonicalPath}")
}
