package com.gasstation.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.gasstation.core.model.MapProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

interface ExternalMapLauncher {
    fun open(
        provider: MapProvider,
        stationName: String,
        originLatitude: Double?,
        originLongitude: Double?,
        latitude: Double,
        longitude: Double,
    ): ExternalMapLaunchResult
}

sealed interface ExternalMapLaunchResult {
    data object Opened : ExternalMapLaunchResult

    data object StoreOpened : ExternalMapLaunchResult

    data object Failed : ExternalMapLaunchResult
}

@Singleton
class IntentExternalMapLauncher @Inject constructor(@param:ApplicationContext private val context: Context) : ExternalMapLauncher {
    override fun open(
        provider: MapProvider,
        stationName: String,
        originLatitude: Double?,
        originLongitude: Double?,
        latitude: Double,
        longitude: Double,
    ): ExternalMapLaunchResult {
        val target = provider.externalMapTarget(
            applicationId = context.packageName,
            stationName = stationName,
            originLatitude = originLatitude,
            originLongitude = originLongitude,
            latitude = latitude,
            longitude = longitude,
        )
        val routeIntent = Intent(
            Intent.ACTION_VIEW,
            target.routeUri.toUri(),
        )
            .setPackage(target.packageName)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isInstalled(target.packageName) && startActivity(routeIntent)) {
            return ExternalMapLaunchResult.Opened
        }

        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=${target.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startActivity(marketIntent)) {
            return ExternalMapLaunchResult.StoreOpened
        }

        val httpsStoreIntent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=${target.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (startActivity(httpsStoreIntent)) {
            ExternalMapLaunchResult.StoreOpened
        } else {
            ExternalMapLaunchResult.Failed
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun startActivity(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

internal data class ExternalMapTarget(val packageName: String, val routeUri: String)

internal fun MapProvider.externalMapTarget(
    applicationId: String,
    stationName: String,
    originLatitude: Double?,
    originLongitude: Double?,
    latitude: Double,
    longitude: Double,
): ExternalMapTarget {
    val encodedName = URLEncoder.encode(stationName, Charsets.UTF_8.name())
    val packageName = when (this) {
        MapProvider.TMAP -> "com.skt.tmap.ku"
        MapProvider.KAKAO_MAP -> "net.daum.android.map"
        MapProvider.NAVER_MAP -> "com.nhn.android.nmap"
    }
    val routeUri = when (this) {
        MapProvider.TMAP ->
            "tmap://route?goalx=$longitude&goaly=$latitude&goalname=$encodedName&reqCoordType=KTM&resCoordType=WGS84"

        MapProvider.KAKAO_MAP -> buildList {
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

        MapProvider.NAVER_MAP ->
            "nmap://route/car?dlat=$latitude&dlng=$longitude&dname=$encodedName&appname=$applicationId"
    }
    return ExternalMapTarget(packageName = packageName, routeUri = routeUri)
}
