package com.gasstation.core.location

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
