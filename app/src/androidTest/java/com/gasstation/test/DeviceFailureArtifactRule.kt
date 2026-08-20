package com.gasstation.test

import android.os.Build
import androidx.test.core.app.takeScreenshot
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import org.json.JSONObject
import org.junit.rules.TestWatcher
import org.junit.runner.Description

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
