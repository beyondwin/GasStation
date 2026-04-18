package com.gasstation.di

import com.gasstation.startup.AppStartupHook
import com.gasstation.startup.ProdSecretsStartupHook
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ProdStartupModule {
    @Binds
    @IntoSet
    abstract fun bindProdStartupHook(
        hook: ProdSecretsStartupHook,
    ): AppStartupHook
}
