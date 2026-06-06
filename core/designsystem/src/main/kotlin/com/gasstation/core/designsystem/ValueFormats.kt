package com.gasstation.core.designsystem

import com.gasstation.core.model.DistanceMeters
import com.gasstation.core.model.MoneyWon
import java.text.DecimalFormat

// Korean Won unit (U+C6D0 = 원); single source so price labels never drift per screen.
const val GAS_STATION_WON_UNIT = "원"
const val GAS_STATION_DISTANCE_UNIT = "km"

fun MoneyWon.gasStationPriceDigits(): String = DecimalFormat("#,###").format(value)

fun MoneyWon.gasStationPriceLabel(): String = "${gasStationPriceDigits()}$GAS_STATION_WON_UNIT"

fun DistanceMeters.gasStationDistanceDigits(): String = DecimalFormat("#,##0.0").format(value / 1000.0)

fun DistanceMeters.gasStationDistanceLabel(): String = "${gasStationDistanceDigits()}$GAS_STATION_DISTANCE_UNIT"
