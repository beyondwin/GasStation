package com.gasstation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal fun Rect.toScreenBounds(positionInWindow: Offset, positionOnScreen: Offset): Rect {
    val windowToScreenX = positionOnScreen.x - positionInWindow.x
    val windowToScreenY = positionOnScreen.y - positionInWindow.y
    return Rect(
        left + windowToScreenX,
        top + windowToScreenY,
        right + windowToScreenX,
        bottom + windowToScreenY,
    )
}
