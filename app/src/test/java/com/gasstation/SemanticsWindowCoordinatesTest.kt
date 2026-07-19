package com.gasstation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticsWindowCoordinatesTest {
    @Test
    fun toScreenBounds_usesTheOwningWindowsScreenDelta() {
        val screenBounds = Rect(10f, 20f, 30f, 50f).toScreenBounds(
            positionInWindow = Offset(2f, 3f),
            positionOnScreen = Offset(102f, 203f),
        )

        assertEquals(Rect(110f, 220f, 130f, 250f), screenBounds)
    }
}
