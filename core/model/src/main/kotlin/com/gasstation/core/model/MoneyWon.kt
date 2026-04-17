package com.gasstation.core.model

@JvmInline
value class MoneyWon(val value: Int) {
    init {
        require(value >= 0) { "money won must be non-negative" }
    }
}
