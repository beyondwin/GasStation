package com.gasstation.core.network.station

import com.gasstation.core.model.Coordinates
import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

object LocalKoreanCoordinateTransform {
    private const val WGS84_PARAMS = "+proj=longlat +datum=WGS84 +no_defs"
    // Opinet aroundAll.do documents its x/y inputs and station GIS coordinates as KATEC.
    private const val KATEC_PARAMS =
        "+proj=tmerc +lat_0=38 +lon_0=128 +k=0.9999 +x_0=400000 +y_0=600000 " +
            "+ellps=bessel +units=m +no_defs " +
            "+towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43"

    private val crsFactory = CRSFactory()
    private val transformFactory = CoordinateTransformFactory()
    private val wgs84 = crsFactory.createFromParameters("WGS84", WGS84_PARAMS)
    private val katec = crsFactory.createFromParameters("KATEC", KATEC_PARAMS)
    private val wgs84ToKatecTransform = transformFactory.createTransform(wgs84, katec)
    private val katecToWgs84Transform = transformFactory.createTransform(katec, wgs84)

    fun wgs84ToKtm(
        latitude: Double,
        longitude: Double,
    ): KtmCoordinates {
        val source = ProjCoordinate(longitude, latitude)
        val target = ProjCoordinate()
        wgs84ToKatecTransform.transform(source, target)
        return KtmCoordinates(x = target.x, y = target.y)
    }

    fun ktmToWgs84(
        x: Double,
        y: Double,
    ): Coordinates {
        val source = ProjCoordinate(x, y)
        val target = ProjCoordinate()
        katecToWgs84Transform.transform(source, target)
        return Coordinates(
            latitude = target.y,
            longitude = target.x,
        )
    }
}

data class KtmCoordinates(
    val x: Double,
    val y: Double,
)
