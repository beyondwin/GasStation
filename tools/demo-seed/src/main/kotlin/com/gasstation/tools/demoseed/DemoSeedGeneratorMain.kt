package com.gasstation.tools.demoseed

import com.gasstation.core.model.Coordinates
import java.io.File
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Expected output file path argument." }
    require(System.getProperty("opinet.apikey").isNotBlank()) { "Missing opinet.apikey" }

    runBlocking {
        DemoSeedGenerator.fromSystemProperties().generate(
            outputFile = File(args[0]),
            origin = Coordinates(
                latitude = 37.497927,
                longitude = 127.027583,
            ),
            generatedAtEpochMillis = System.currentTimeMillis(),
        )
    }
}
