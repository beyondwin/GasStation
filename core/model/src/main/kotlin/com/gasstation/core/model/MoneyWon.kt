package com.gasstation.core.model

@JvmInline
value class MoneyWon(val value: Int) {
    init {
        require(value >= 0) { "money won must be non-negative" }
    }

    companion object {
        /** 신뢰할 수 없는 외부/영속 입력용. 음수면 예외 대신 null. */
        fun ofOrNull(value: Int): MoneyWon? = if (value >= 0) MoneyWon(value) else null
    }
}
