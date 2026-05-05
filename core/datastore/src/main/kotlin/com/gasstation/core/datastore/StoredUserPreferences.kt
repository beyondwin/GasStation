package com.gasstation.core.datastore

data class StoredUserPreferences(
    val searchRadiusName: String,
    val fuelTypeName: String,
    val brandFilterName: String,
    val sortOrderName: String,
    val mapProviderName: String,
) {
    companion object {
        val Default = StoredUserPreferences(
            searchRadiusName = "KM_3",
            fuelTypeName = "GASOLINE",
            brandFilterName = "ALL",
            sortOrderName = "DISTANCE",
            mapProviderName = "TMAP",
        )
    }
}
