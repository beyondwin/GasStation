package com.gasstation.test

internal enum class PermissionButton {
    ALLOW,
    DENY,
}

internal data class PermissionUiResource(val packageName: String, val resourceName: String)

internal object PermissionUiContract {
    private val packageInstallerPackages =
        listOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )
    private val permissionControllerPackages =
        listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )

    fun candidates(sdk: Int, button: PermissionButton): List<PermissionUiResource> {
        val packages =
            when (sdk) {
                in 24..28 -> packageInstallerPackages
                in 29..37 -> permissionControllerPackages
                else -> throw IllegalArgumentException("Unsupported permission UI SDK: $sdk")
            }
        val resource =
            when (button) {
                PermissionButton.ALLOW ->
                    if (sdk <= 28) {
                        "permission_allow_button"
                    } else {
                        "permission_allow_foreground_only_button"
                    }

                PermissionButton.DENY -> "permission_deny_button"
            }
        return packages.map { packageName -> PermissionUiResource(packageName, resource) }
    }
}

private val attemptIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")

internal fun failureArtifactBaseName(attemptId: String, className: String, methodName: String, apiLevel: Int): String {
    require(attemptIdPattern.matches(attemptId)) { "Invalid device evidence attempt ID" }
    require(apiLevel in 24..37) { "Unsupported device evidence API: $apiLevel" }
    fun sanitize(value: String): String {
        require(value.isNotBlank()) { "Failure artifact identity must not be blank" }
        return value.map { character ->
            if (character.isLetterOrDigit() || character == '_' || character == '-') character else '_'
        }.joinToString("")
    }
    return "failure-$attemptId-${sanitize(className)}-${sanitize(methodName)}-api$apiLevel"
}
