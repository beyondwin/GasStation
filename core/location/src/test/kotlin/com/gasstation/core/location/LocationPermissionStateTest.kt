package com.gasstation.core.location

import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocationPermissionStateTest {
    @Test
    fun `approximate granted is not equivalent to precise granted`() {
        assertNotEquals(
            LocationPermissionState.ApproximateGranted,
            LocationPermissionState.PreciseGranted,
        )
    }
}
