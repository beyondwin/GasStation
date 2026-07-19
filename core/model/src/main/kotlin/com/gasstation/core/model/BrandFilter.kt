package com.gasstation.core.model

enum class BrandFilter(private val matchedBrands: Set<Brand>) {
    ALL(emptySet()),
    SKE(setOf(Brand.SKE)),
    GSC(setOf(Brand.GSC)),
    HDO(setOf(Brand.HDO)),
    SOL(setOf(Brand.SOL)),
    ALTEUL(setOf(Brand.RTO, Brand.RTX, Brand.NHO)),
    E1G(setOf(Brand.E1G)),
    SKG(setOf(Brand.SKG)),
    ETC(setOf(Brand.ETC)),
    ;

    fun matches(stationBrand: Brand): Boolean = this == ALL || stationBrand in matchedBrands
}
