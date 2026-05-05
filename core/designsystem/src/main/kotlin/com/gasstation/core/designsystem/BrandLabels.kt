package com.gasstation.core.designsystem

import com.gasstation.core.model.Brand
import com.gasstation.core.model.BrandFilter

fun Brand.gasStationBrandLabel(): String = when (this) {
    Brand.SKE -> "SK에너지"
    Brand.GSC -> "GS칼텍스"
    Brand.HDO -> "현대오일뱅크"
    Brand.SOL -> "S-OIL"
    Brand.RTO -> "자영알뜰"
    Brand.RTX -> "고속도로알뜰"
    Brand.NHO -> "농협알뜰"
    Brand.ETC -> "자가상표"
    Brand.E1G -> "E1"
    Brand.SKG -> "SK가스"
}

fun BrandFilter.gasStationBrandFilterLabel(): String = when (this) {
    BrandFilter.ALL -> "전체"
    BrandFilter.SKE,
    BrandFilter.GSC,
    BrandFilter.HDO,
    BrandFilter.SOL,
    BrandFilter.RTO,
    BrandFilter.RTX,
    BrandFilter.NHO,
    BrandFilter.ETC,
    BrandFilter.E1G,
    BrandFilter.SKG,
    -> requireNotNull(brand).gasStationBrandLabel()
}
