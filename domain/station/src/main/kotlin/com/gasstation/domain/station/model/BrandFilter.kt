package com.gasstation.domain.station.model

enum class BrandFilter(val brand: Brand?) {
    ALL(brand = null),
    SKE(brand = Brand.SKE),
    GSC(brand = Brand.GSC),
    HDO(brand = Brand.HDO),
    SOL(brand = Brand.SOL),
    RTO(brand = Brand.RTO),
    RTX(brand = Brand.RTX),
    NHO(brand = Brand.NHO),
    ETC(brand = Brand.ETC),
    E1G(brand = Brand.E1G),
    SKG(brand = Brand.SKG),
    ;

    fun matches(stationBrand: Brand): Boolean = brand == null || brand == stationBrand
}
