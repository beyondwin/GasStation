package com.gasstation.domain.station.model

sealed interface StationPriceDelta {
    enum class PriceDirection { RISE, FALL, NEUTRAL }

    val direction: PriceDirection
    val amountWonOrNull: Int?

    data object Unavailable : StationPriceDelta {
        override val direction = PriceDirection.NEUTRAL
        override val amountWonOrNull: Int? = null
    }

    data object Unchanged : StationPriceDelta {
        override val direction = PriceDirection.NEUTRAL
        override val amountWonOrNull: Int? = null
    }

    data class Increased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Increased price delta amount must be positive." }
        }

        override val direction = PriceDirection.RISE
        override val amountWonOrNull get() = amountWon
    }

    data class Decreased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Decreased price delta amount must be positive." }
        }

        override val direction = PriceDirection.FALL
        override val amountWonOrNull get() = amountWon
    }

    companion object {
        fun from(previousPriceWon: Int?, currentPriceWon: Int): StationPriceDelta {
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
