package com.gasstation.di

import com.gasstation.analytics.LogcatCrashReporter
import com.gasstation.core.observability.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProdCrashReporterModule {
    @Binds
    abstract fun bindCrashReporter(impl: LogcatCrashReporter): CrashReporter
}
