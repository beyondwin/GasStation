package com.gasstation.feature.stationlist

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onCoordinatesAvailable: (Coordinates?) -> Unit,
    onOpenExternalMap: (StationListEffect.OpenExternalMap) -> Unit,
    onFirstContentDrawn: () -> Unit = {},
    viewModel: StationListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deniedRequestCount by rememberSaveable { mutableIntStateOf(0) }
    val permissionState = rememberLocationPermissionsState { results ->
        if (results.values.none { granted -> granted }) {
            deniedRequestCount += 1
        }
    }
    val domainPermissionState = permissionState.toPermissionState()
    val permissionAction = permissionAction(
        deniedRequestCount = deniedRequestCount,
        shouldShowRationale = permissionState.shouldShowRationale,
    )

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
        uiState.preferences,
    ) {
        if (uiState.shouldAutoRefreshOnRoute()) {
            viewModel.onAction(StationListAction.AutoRefreshRequested)
        }
    }

    StationListRouteCoordinatesEffect(
        uiState = uiState,
        onCoordinatesAvailable = onCoordinatesAvailable,
    )

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is StationListEffect.OpenExternalMap -> onOpenExternalMap(effect)

                StationListEffect.OpenLocationSettings -> {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

                is StationListEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message.resolve(context))
            }
        }
    }

    StationListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        permissionAction = permissionAction,
        onAction = viewModel::onAction,
        onPermissionAction = {
            when (permissionAction) {
                PermissionAction.Request -> permissionState.launchMultiplePermissionRequest()

                PermissionAction.OpenAppSettings -> context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }
        },
        onOpenLocationSettings = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        onFirstContentDrawn = onFirstContentDrawn,
    )
}

@Composable
internal fun StationListRouteCoordinatesEffect(uiState: StationListUiState, onCoordinatesAvailable: (Coordinates?) -> Unit) {
    val currentOnCoordinatesAvailable by rememberUpdatedState(onCoordinatesAvailable)
    val availableCoordinates = uiState.watchlistCoordinatesOrNull()
    LaunchedEffect(availableCoordinates) {
        currentOnCoordinatesAvailable(availableCoordinates)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun rememberLocationPermissionsState(onPermissionsResult: (Map<String, Boolean>) -> Unit): MultiplePermissionsState =
    rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
        onPermissionsResult = onPermissionsResult,
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
