package com.gasstation.core.model

@JvmInline
value class DistanceMeters(val value: Int) {
    init {
        require(value >= 0) { "distance meters must be non-negative" }
    }
}
