package com.gasstation.di

import com.gasstation.analytics.NoOpCrashReporter
import com.gasstation.core.observability.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DemoCrashReporterModule {
    @Binds
    abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter
}
