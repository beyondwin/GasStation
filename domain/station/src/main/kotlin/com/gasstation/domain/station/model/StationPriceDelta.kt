package com.gasstation.domain.station.model

public sealed interface StationPriceDelta {
    public enum class PriceDirection { RISE, FALL, NEUTRAL }

    public val direction: PriceDirection
    public val amountWonOrNull: Int?

    public data object Unavailable : StationPriceDelta {
        public override val direction: PriceDirection = PriceDirection.NEUTRAL
        public override val amountWonOrNull: Int? = null
    }

    public data object Unchanged : StationPriceDelta {
        public override val direction: PriceDirection = PriceDirection.NEUTRAL
        public override val amountWonOrNull: Int? = null
    }

    public data class Increased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Increased price delta amount must be positive." }
        }

        public override val direction: PriceDirection = PriceDirection.RISE
        public override val amountWonOrNull: Int get() = amountWon
    }

    public data class Decreased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Decreased price delta amount must be positive." }
        }

        public override val direction: PriceDirection = PriceDirection.FALL
        public override val amountWonOrNull: Int get() = amountWon
    }

    public companion object {
        public fun from(previousPriceWon: Int?, currentPriceWon: Int): StationPriceDelta {
            require(currentPriceWon >= 0) { "Current price must be non-negative." }
            require(previousPriceWon == null || previousPriceWon >= 0) {
                "Previous price must be non-negative when present."
            }

            return when {
                previousPriceWon == null -> Unavailable
                previousPriceWon == currentPriceWon -> Unchanged
                previousPriceWon < currentPriceWon -> Increased(currentPriceWon - previousPriceWon)
                else -> Decreased(previousPriceWon - currentPriceWon)
            }
        }
    }
}
