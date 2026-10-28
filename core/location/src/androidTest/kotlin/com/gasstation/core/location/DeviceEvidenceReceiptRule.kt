package com.gasstation.core.location

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

class DeviceEvidenceReceiptRule : TestWatcher() {
    override fun starting(description: Description) {
        if (!requireGmdReceiptForSelectedLane()) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val candidates =
            if (Build.VERSION.SDK_INT <= 28) {
                listOf("com.google.android.packageinstaller", "com.android.packageinstaller")
            } else {
                listOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
            }
        val permissionPackage = candidates.firstOrNull { runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess }
            ?: error("Permission controller package is not installed")
        val permissionInfo = packageManager.getPackageInfo(permissionPackage, 0)
        val permissionRevision =
            if (Build.VERSION.SDK_INT >= 28) {
                permissionInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                permissionInfo.versionCode.toString()
            }
        val googleServices = runCatching { packageManager.getPackageInfo("com.google.android.gms", 0) }.getOrNull()
        val googleServicesRevision = googleServices?.let {
            if (Build.VERSION.SDK_INT >= 28) {
                it.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toString()
            }
        }
        val abi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull())
        val serial = readSystemProperty("ro.boot.qemu.avd_name")
        require(serial.isNotBlank()) { "GMD AVD name property is missing" }
        val imageSource = if (googleServicesRevision == null) "aosp" else "google"
        val task = ":core:location:${serial}DebugAndroidTest"
        val shards = InstrumentationRegistry.getArguments().getString("numShards")?.toInt() ?: 1
        require(shards == 1) { "GMD test sharding is not allowed" }
        val receipt = JSONObject()
            .put("schemaVersion", 1)
            .put("producer", "androidx-test-storage")
            .put("abi", abi)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("avdName", serial)
            .put("fingerprint", Build.FINGERPRINT)
            .put("googleServicesRevision", googleServicesRevision ?: JSONObject.NULL)
            .put("imagePackage", "system-images;android-${Build.VERSION.SDK_INT};$imageSource;$abi")
            .put("imageSource", imageSource)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("permissionControllerPackage", permissionPackage)
            .put("permissionControllerRevision", permissionRevision)
            .put("profile", profileFromAvdName(serial))
            .put("shards", shards)
            .put("task", task)
        PlatformTestStorageRegistry.getInstance().openOutputFile("device-evidence-device.json")
            .bufferedWriter(Charsets.UTF_8).use { it.write(receipt.toString()) }
    }
}

private fun requireGmdReceiptForSelectedLane(): Boolean {
    val lane = requireNotNull(InstrumentationRegistry.getArguments().getString("deviceEvidenceLane")) {
        "Missing deviceEvidenceLane instrumentation argument"
    }
    if (lane == "local-connected") {
        val attemptId = InstrumentationRegistry.getArguments().getString("deviceEvidenceAttemptId")
        require(attemptId != null && Regex("local-[A-Za-z0-9][A-Za-z0-9._-]{0,73}").matches(attemptId)) {
            "Local connected tests require a local-prefixed deviceEvidenceAttemptId of at most 80 characters"
        }
        // Developer execution only: never manufacture a managed-device evidence receipt.
        return false
    }
    require(lane == "api36-scheduled") { "Unreviewed location device evidence lane: $lane" }
    require(Build.VERSION.SDK_INT == 36) { "Device API does not match selected lane: $lane" }
    require(readSystemProperty("ro.boot.qemu.avd_name") == "gasstationPixel2Api36") {
        "AVD name does not match selected lane: $lane"
    }
    return true
}

private fun readSystemProperty(name: String): String = ProcessBuilder("/system/bin/getprop", name).start().inputStream
    .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }

private fun profileFromAvdName(avdName: String): String = when {
    avdName.matches(Regex("gasstationPixel2Api(?:28|36)")) -> "Pixel 2"
    else -> error("Unreviewed GMD AVD name: $avdName")
}
