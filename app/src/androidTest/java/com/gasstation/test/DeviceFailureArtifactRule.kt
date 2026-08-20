package com.gasstation.test

import android.os.Build
import androidx.test.core.app.takeScreenshot
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

private const val ATTEMPT_ARGUMENT = "deviceEvidenceAttemptId"

internal object DeviceFailureContext {
    private var permissionSelection: PermissionUiResource? = null

    fun reset() {
        permissionSelection = null
    }

    fun recordPermissionSelection(selection: PermissionUiResource) {
        permissionSelection = selection
    }

    fun permissionSelection(): PermissionUiResource? = permissionSelection
}

class DeviceFailureArtifactRule : TestWatcher() {
    override fun starting(description: Description) {
        DeviceFailureContext.reset()
        writeDeviceEvidenceReceipt()
    }

    override fun failed(throwable: Throwable, description: Description) {
        val attemptId =
            requireNotNull(
                InstrumentationRegistry.getArguments().getString(ATTEMPT_ARGUMENT),
            ) { "Missing $ATTEMPT_ARGUMENT instrumentation argument" }
        val className = requireNotNull(description.className)
        val methodName = requireNotNull(description.methodName)
        val baseName =
            failureArtifactBaseName(
                attemptId = attemptId,
                className = className,
                methodName = methodName,
                apiLevel = Build.VERSION.SDK_INT,
            )

        val screenshotFailure =
            runCatching {
                takeScreenshot().writeToTestStorage(baseName)
            }.exceptionOrNull()
        val diagnosticFailure =
            runCatching {
                val selection = DeviceFailureContext.permissionSelection()
                val diagnostic =
                    JSONObject()
                        .put("apiLevel", Build.VERSION.SDK_INT)
                        .put("attemptId", attemptId)
                        .put("className", className)
                        .put("methodName", methodName)
                        .put(
                            "permissionSelection",
                            selection?.let {
                                JSONObject()
                                    .put("packageName", it.packageName)
                                    .put("resourceName", it.resourceName)
                            } ?: JSONObject.NULL,
                        )
                PlatformTestStorageRegistry.getInstance()
                    .openOutputFile("$baseName.txt")
                    .bufferedWriter(Charsets.UTF_8)
                    .use { writer -> writer.write(diagnostic.toString()) }
            }.exceptionOrNull()

        screenshotFailure?.let { throwable.addSuppressed(it) }
        diagnosticFailure?.let { throwable.addSuppressed(it) }
    }
}

private fun writeDeviceEvidenceReceipt() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val packageManager = context.packageManager
    val permissionPackage =
        if (Build.VERSION.SDK_INT <= 28) {
            listOf("com.google.android.packageinstaller", "com.android.packageinstaller")
        } else {
            listOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
        }.firstOrNull { candidate ->
            runCatching { packageManager.getPackageInfo(candidate, 0) }.isSuccess
        } ?: error("Permission controller package is not installed")
    val permissionInfo = packageManager.getPackageInfo(permissionPackage, 0)
    val permissionRevision =
        if (Build.VERSION.SDK_INT >= 28) permissionInfo.longVersionCode.toString()
        else @Suppress("DEPRECATION") permissionInfo.versionCode.toString()
    val hasGoogleServices =
        runCatching { packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess
    val imageSource = if (hasGoogleServices) "google" else "aosp"
    val serial = readSystemProperty("ro.boot.qemu.avd_name")
    require(serial.isNotBlank()) { "GMD AVD name property is missing" }
    val abi = requireNotNull(Build.SUPPORTED_ABIS.firstOrNull())
    val receipt =
        JSONObject()
            .put("abi", abi)
            .put("apiLevel", Build.VERSION.SDK_INT)
            .put("fingerprint", Build.FINGERPRINT)
            .put("imagePackage", "system-images;android-${Build.VERSION.SDK_INT};$imageSource;$abi")
            .put("imageSource", imageSource)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("permissionControllerPackage", permissionPackage)
            .put("permissionControllerRevision", permissionRevision)
            .put("profile", profileFromAvdName(serial))
            .put("serial", serial)
    PlatformTestStorageRegistry.getInstance()
        .openOutputFile("device-evidence-device.json")
        .bufferedWriter(Charsets.UTF_8)
        .use { writer -> writer.write(receipt.toString()) }
}

private fun readSystemProperty(name: String): String =
    ProcessBuilder("/system/bin/getprop", name)
        .start()
        .inputStream
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText().trim() }

private fun profileFromAvdName(avdName: String): String =
    when {
        avdName.contains("Pixel2", ignoreCase = true) -> "Pixel 2"
        else -> error("Unreviewed GMD AVD name: $avdName")
    }
