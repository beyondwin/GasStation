package com.gasstation.core.location

import android.annotation.SuppressLint
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationRepository
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides
    @Singleton
    @SuppressLint("MissingPermission")
    fun provideCurrentLocationClient(): CurrentLocationClient = CurrentLocationClient {
            context,
            priority,
            cancellationTokenSource,
            onSuccess,
            onFailure,
        ->
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(priority, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                onSuccess(location?.let { Coordinates(it.latitude, it.longitude) })
            }
            .addOnFailureListener(onFailure)
    }

    @Provides
    @Singleton
    fun provideForegroundLocationProvider(
        provider: AndroidForegroundLocationProvider,
    ): ForegroundLocationProvider = provider

    @Provides
    @Singleton
    internal fun provideLocationRepository(
        repository: DefaultLocationRepository,
    ): LocationRepository = repository
}
