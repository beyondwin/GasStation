package com.gasstation.core.model

@JvmInline
public value class MoneyWon(public val value: Int) {
    init {
        require(value >= 0) { "money won must be non-negative" }
    }

    public companion object {
        /** 신뢰할 수 없는 외부/영속 입력용. 음수면 예외 대신 null. */
        public fun ofOrNull(value: Int): MoneyWon? = if (value >= 0) MoneyWon(value) else null
    }
}
