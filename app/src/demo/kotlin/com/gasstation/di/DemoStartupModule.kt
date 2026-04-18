package com.gasstation.di

import com.gasstation.startup.AppStartupHook
import com.gasstation.startup.DemoSeedStartupHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoStartupModule {
    @Binds
    @IntoSet
    abstract fun bindDemoSeedStartupHook(
        hook: DemoSeedStartupHook,
    ): AppStartupHook
}
