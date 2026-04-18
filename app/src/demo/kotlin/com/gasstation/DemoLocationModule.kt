package com.gasstation

import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DemoLocationModule {
    private val reviewerCoordinates = Coordinates(
        latitude = 37.498095,
        longitude = 127.02761,
    )

    @Provides
    fun provideDemoLocationOverride(): DemoLocationOverride = DemoLocationOverride { permissionState ->
        if (permissionState == LocationPermissionState.Denied) {
            null
        } else {
            reviewerCoordinates
        }
    }
}
