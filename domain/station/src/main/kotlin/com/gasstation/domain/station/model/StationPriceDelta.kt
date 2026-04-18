package com.gasstation.domain.station.model

sealed interface StationPriceDelta {
    data object Unavailable : StationPriceDelta
    data object Unchanged : StationPriceDelta
    data class Increased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Increased price delta amount must be positive." }
        }
    }

    data class Decreased(val amountWon: Int) : StationPriceDelta {
        init {
            require(amountWon > 0) { "Decreased price delta amount must be positive." }
        }
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
