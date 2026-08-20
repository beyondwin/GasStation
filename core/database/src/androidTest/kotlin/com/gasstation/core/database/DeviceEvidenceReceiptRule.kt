package com.gasstation.core.database

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

class DeviceEvidenceReceiptRule : TestWatcher() {
    override fun starting(description: Description) {
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
            if (Build.VERSION.SDK_INT >= 28) permissionInfo.longVersionCode.toString()
            else @Suppress("DEPRECATION") permissionInfo.versionCode.toString()
        val googleServices = runCatching { packageManager.getPackageInfo("com.google.android.gms", 0) }.getOrNull()
        val googleServicesRevision = googleServices?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toString()
            else @Suppress("DEPRECATION") it.versionCode.toString()
        }
        val abi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull())
        val serial = readSystemProperty("ro.boot.qemu.avd_name")
        require(serial.isNotBlank()) { "GMD AVD name property is missing" }
        val receipt = JSONObject()
            .put("schemaVersion", 1)
            .put("producer", "androidx-test-storage")
            .put("abi", abi)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("avdName", serial)
            .put("fingerprint", Build.FINGERPRINT)
            .put("googleServicesRevision", googleServicesRevision ?: JSONObject.NULL)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("permissionControllerPackage", permissionPackage)
            .put("permissionControllerRevision", permissionRevision)
        PlatformTestStorageRegistry.getInstance().openOutputFile("device-evidence-device.json")
            .bufferedWriter(Charsets.UTF_8).use { it.write(receipt.toString()) }
    }
}

private fun readSystemProperty(name: String): String =
    ProcessBuilder("/system/bin/getprop", name).start().inputStream
        .bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
