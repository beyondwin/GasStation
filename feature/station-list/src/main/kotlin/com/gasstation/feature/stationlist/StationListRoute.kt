package com.gasstation.feature.stationlist

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionState = rememberLocationPermissionsState()
    val domainPermissionState = permissionState.toPermissionState()

    LaunchedEffect(domainPermissionState) {
        viewModel.onAction(
            StationListAction.PermissionChanged(domainPermissionState),
        )
    }

    LaunchedEffect(context, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.collectLocationAvailability()
        }
    }

    LaunchedEffect(
        uiState.permissionState,
        uiState.isGpsEnabled,
        uiState.isAvailabilityKnown,
        uiState.needsRecoveryRefresh,
    ) {
        if (
            uiState.isAvailabilityKnown &&
            uiState.isGpsEnabled &&
            (
                uiState.currentCoordinates == null ||
                    uiState.hasDeniedLocationAccess ||
                    uiState.needsRecoveryRefresh
                )
        ) {
            viewModel.onAction(StationListAction.AutoRefreshRequested)
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
                uiState.isGpsEnabled &&
                    (
                        uiState.permissionState != LocationPermissionState.Denied ||
                            uiState.hasDeniedLocationAccess
                        )
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
