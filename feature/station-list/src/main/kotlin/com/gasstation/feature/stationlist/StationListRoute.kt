package com.gasstation.feature.stationlist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.gasstation.core.location.LocationPermissionState
import com.gasstation.core.model.Coordinates
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StationListRoute(
    onSettingsClick: () -> Unit,
    onWatchlistClick: (Coordinates) -> Unit,
    onOpenExternalMap: (StationListEffect.OpenExternalMap) -> Unit,
    viewModel: StationListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionState = rememberLocationPermissionsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var isGpsEnabled by remember { mutableStateOf(context.isGpsEnabled()) }

    LaunchedEffect(permissionState.allPermissionsGranted, permissionState.permissions) {
        viewModel.onAction(
            StationListAction.PermissionChanged(permissionState.toPermissionState()),
        )
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            isGpsEnabled = context.isGpsEnabled()
            viewModel.onAction(StationListAction.GpsAvailabilityChanged(isGpsEnabled))
        }
    }

    LaunchedEffect(permissionState.toPermissionState(), uiState.isGpsEnabled) {
        if (permissionState.toPermissionState() != LocationPermissionState.Denied && uiState.isGpsEnabled) {
            viewModel.onAction(StationListAction.RefreshRequested)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StationListEffect.OpenExternalMap -> onOpenExternalMap(effect)
                StationListEffect.OpenLocationSettings -> {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                is StationListEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    StationListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onRequestPermissions = { permissionState.launchMultiplePermissionRequest() },
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        onSettingsClick = onSettingsClick,
        onWatchlistClick = uiState.currentCoordinates
            ?.takeIf {
                uiState.permissionState != LocationPermissionState.Denied && uiState.isGpsEnabled
            }
            ?.let { coordinates ->
                { onWatchlistClick(coordinates) }
            },
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberLocationPermissionsState(): MultiplePermissionsState =
    rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    )

@OptIn(ExperimentalPermissionsApi::class)
private fun MultiplePermissionsState.toPermissionState(): LocationPermissionState {
    val fineGranted = permissions.any {
        it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status.isGranted
    }
    val coarseGranted = permissions.any {
        it.permission == Manifest.permission.ACCESS_COARSE_LOCATION && it.status.isGranted
    }
    return when {
        fineGranted -> LocationPermissionState.PreciseGranted
        coarseGranted -> LocationPermissionState.ApproximateGranted
        else -> LocationPermissionState.Denied
    }
}

private fun Context.isGpsEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
