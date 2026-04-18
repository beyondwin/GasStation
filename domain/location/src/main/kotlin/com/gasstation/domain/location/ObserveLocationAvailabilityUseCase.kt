package com.gasstation.domain.location

import javax.inject.Inject

class ObserveLocationAvailabilityUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    operator fun invoke() = repository.observeAvailability()
}
