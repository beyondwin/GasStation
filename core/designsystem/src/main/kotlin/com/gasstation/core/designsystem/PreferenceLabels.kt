package com.gasstation.core.designsystem

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.FuelType
import com.gasstation.core.model.SearchRadius

fun SearchRadius.gasStationSearchRadiusLabel(): StringResource = when (this) {
    SearchRadius.KM_3 -> StringResource.fromId(R.string.gas_station_radius_km3)
    SearchRadius.KM_4 -> StringResource.fromId(R.string.gas_station_radius_km4)
    SearchRadius.KM_5 -> StringResource.fromId(R.string.gas_station_radius_km5)
}

fun FuelType.gasStationFuelTypeLabel(): StringResource = when (this) {
    FuelType.GASOLINE -> StringResource.fromId(R.string.gas_station_fuel_gasoline)
    FuelType.DIESEL -> StringResource.fromId(R.string.gas_station_fuel_diesel)
    FuelType.PREMIUM_GASOLINE -> StringResource.fromId(R.string.gas_station_fuel_premium)
    FuelType.KEROSENE -> StringResource.fromId(R.string.gas_station_fuel_kerosene)
    FuelType.LPG -> StringResource.fromId(R.string.gas_station_fuel_lpg)
}
