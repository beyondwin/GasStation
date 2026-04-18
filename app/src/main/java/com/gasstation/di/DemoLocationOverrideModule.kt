package com.gasstation.di

import com.gasstation.core.location.DemoLocationOverride
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoLocationOverrideModule {
    @BindsOptionalOf
    abstract fun bindDemoLocationOverride(): DemoLocationOverride
}
