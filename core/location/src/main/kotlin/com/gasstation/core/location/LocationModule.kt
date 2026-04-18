package com.gasstation.core.location

import com.gasstation.core.model.Coordinates
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
}
