package com.gasstation.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.gasstation.core.model.MapProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.net.URLEncoder

interface ExternalMapLauncher {
    fun open(
        provider: MapProvider,
        stationName: String,
        originLatitude: Double?,
        originLongitude: Double?,
        latitude: Double,
        longitude: Double,
    )
}

@Singleton
class IntentExternalMapLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExternalMapLauncher {
    override fun open(
        provider: MapProvider,
        stationName: String,
        originLatitude: Double?,
        originLongitude: Double?,
        latitude: Double,
        longitude: Double,
    ) {
        val packageName = provider.packageName()
        val mapIntent = Intent(
            Intent.ACTION_VIEW,
            provider.mapUri(
                stationName = stationName,
                originLatitude = originLatitude,
                originLongitude = originLongitude,
                latitude = latitude,
                longitude = longitude,
            ).toUri(),
        )
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val launchIntent = if (isInstalled(packageName)) {
            mapIntent
        } else {
            Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launchIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$packageName".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

private fun MapProvider.packageName(): String = when (this) {
    MapProvider.TMAP -> "com.skt.tmap.ku"
    MapProvider.KAKAO_NAVI -> "net.daum.android.map"
    MapProvider.NAVER_MAP -> "com.nhn.android.nmap"
}

private fun MapProvider.mapUri(
    stationName: String,
    originLatitude: Double?,
    originLongitude: Double?,
    latitude: Double,
    longitude: Double,
): String {
    val encodedName = URLEncoder.encode(stationName, Charsets.UTF_8.name())
    return when (this) {
        MapProvider.TMAP -> "tmap://route?goalx=$longitude&goaly=$latitude&goalname=$encodedName&reqCoordType=KTM&resCoordType=WGS84"
        MapProvider.KAKAO_NAVI -> buildList {
            originLatitude?.let { startLatitude ->
                originLongitude?.let { startLongitude ->
                    add("sp=$startLatitude,$startLongitude")
                }
            }
            add("ep=$latitude,$longitude")
            add("ename=$encodedName")
            add("by=car")
        }.joinToString(
            separator = "&",
            prefix = "kakaomap://route?",
        )
        MapProvider.NAVER_MAP -> "nmap://route/car?dlat=$latitude&dlng=$longitude&dname=$encodedName"
    }
}
