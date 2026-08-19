package com.gasstation.core.model

@JvmInline
public value class DistanceMeters(public val value: Int) {
    init {
        require(value >= 0) { "distance meters must be non-negative" }
    }
}
