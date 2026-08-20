package com.gasstation.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PermissionUiContractTest {
    @Test
    fun sdkIndexedPermissionResourcesAreClosedAtReviewedBoundaries() {
        for (sdk in listOf(24, 28)) {
            assertEquals(
                listOf(
                    PermissionUiResource("com.android.packageinstaller", "permission_allow_button"),
                    PermissionUiResource("com.google.android.packageinstaller", "permission_allow_button"),
                ),
                PermissionUiContract.candidates(sdk, PermissionButton.ALLOW),
            )
        }
        for (sdk in listOf(29, 36, 37)) {
            assertEquals(
                listOf(
                    PermissionUiResource(
                        "com.android.permissioncontroller",
                        "permission_allow_foreground_only_button",
                    ),
                    PermissionUiResource(
                        "com.google.android.permissioncontroller",
                        "permission_allow_foreground_only_button",
                    ),
                ),
                PermissionUiContract.candidates(sdk, PermissionButton.ALLOW),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PermissionUiContract.candidates(23, PermissionButton.DENY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PermissionUiContract.candidates(38, PermissionButton.DENY)
        }
    }

    @Test
    fun denyResourcesUseTheSameClosedSdkPackageFamilies() {
        assertEquals(
            listOf(
                PermissionUiResource("com.android.packageinstaller", "permission_deny_button"),
                PermissionUiResource("com.google.android.packageinstaller", "permission_deny_button"),
            ),
            PermissionUiContract.candidates(28, PermissionButton.DENY),
        )
        assertEquals(
            listOf(
                PermissionUiResource("com.android.permissioncontroller", "permission_deny_button"),
                PermissionUiResource("com.google.android.permissioncontroller", "permission_deny_button"),
            ),
            PermissionUiContract.candidates(36, PermissionButton.DENY),
        )
    }

    @Test
    fun failureArtifactNameIsAttemptTestAndApiBound() {
        assertEquals(
            "failure-run_12-3-com_gasstation_DemoPermissionFlowTest-" +
                "c_grantingExplicitPermissionRequest_revealsFixedDemoNearbyContent-api28",
            failureArtifactBaseName(
                attemptId = "run_12-3",
                className = "com.gasstation.DemoPermissionFlowTest",
                methodName = "c_grantingExplicitPermissionRequest_revealsFixedDemoNearbyContent",
                apiLevel = 28,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            failureArtifactBaseName("../escape", "Fixture", "method", 28)
        }
    }
}
