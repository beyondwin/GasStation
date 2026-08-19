package com.gasstation.domain.location

import javax.inject.Inject

public class ObserveLocationAvailabilityUseCase @Inject public constructor(private val repository: LocationRepository) {
    public operator fun invoke(): kotlinx.coroutines.flow.Flow<Boolean> = repository.observeAvailability()
}
