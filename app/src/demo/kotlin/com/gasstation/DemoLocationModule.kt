package com.gasstation

import com.gasstation.core.location.DemoLocationOverride
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.demo.seed.DemoSeedOrigin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DemoLocationModule {
    @Provides
    fun provideDemoLocationOverride(): DemoLocationOverride = DemoLocationOverride { DemoSeedOrigin.coordinates }
}
