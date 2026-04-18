package com.gasstation.core.location

import android.content.Context
import com.gasstation.core.model.Coordinates
import com.google.android.gms.tasks.CancellationTokenSource

fun interface CurrentLocationClient {
    fun getCurrentLocation(
        context: Context,
        priority: Int,
        cancellationTokenSource: CancellationTokenSource,
        onSuccess: (Coordinates?) -> Unit,
        onFailure: (Throwable) -> Unit,
    )
}
