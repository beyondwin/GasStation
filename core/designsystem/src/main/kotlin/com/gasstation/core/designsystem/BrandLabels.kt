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
    BrandFilter.SKE -> Brand.SKE.gasStationBrandLabel()
    BrandFilter.GSC -> Brand.GSC.gasStationBrandLabel()
    BrandFilter.HDO -> Brand.HDO.gasStationBrandLabel()
    BrandFilter.SOL -> Brand.SOL.gasStationBrandLabel()
    BrandFilter.ALTEUL -> "알뜰"
    BrandFilter.E1G -> Brand.E1G.gasStationBrandLabel()
    BrandFilter.SKG -> Brand.SKG.gasStationBrandLabel()
    BrandFilter.ETC -> Brand.ETC.gasStationBrandLabel()
}

fun BrandFilter.gasStationBrandFilterIconBrand(): Brand? = when (this) {
    BrandFilter.ALL -> null
    BrandFilter.SKE -> Brand.SKE
    BrandFilter.GSC -> Brand.GSC
    BrandFilter.HDO -> Brand.HDO
    BrandFilter.SOL -> Brand.SOL
    BrandFilter.ALTEUL -> Brand.RTO
    BrandFilter.E1G -> Brand.E1G
    BrandFilter.SKG -> Brand.SKG
    BrandFilter.ETC -> Brand.ETC
}
