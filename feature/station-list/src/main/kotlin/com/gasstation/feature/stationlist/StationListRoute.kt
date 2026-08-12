package com.gasstation.feature.stationlist

import android.Manifest
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gasstation.core.model.Coordinates
import com.gasstation.domain.location.LocationPermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StationListRoute(
    onCoordinatesAvailable: (Coordinates?) -> Unit,
    onOpenExternalMap: (StationListCommandPayload.OpenExternalMap) -> Boolean,
    onFirstContentDrawn: () -> Unit = {},
    viewModel: StationListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deniedRequestCount by rememberSaveable { mutableIntStateOf(0) }
    val permissionState = rememberLocationPermissionsState { results ->
        if (isTerminalDeniedPermissionResult(results)) {
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

    StationListCommandEffect(
        command = uiState.pendingCommands.firstOrNull(),
        handle = { payload ->
            when (payload) {
                is StationListCommandPayload.OpenExternalMap -> openExternalMapOrShowFailure(
                    command = payload,
                    onOpenExternalMap = onOpenExternalMap,
                    snackbarHostState = snackbarHostState,
                    resources = resources,
                )

                StationListCommandPayload.OpenLocationSettings -> {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

                is StationListCommandPayload.ShowSnackbar -> snackbarHostState.showSnackbar(payload.message.resolve(context))
            }
        },
        acknowledge = { commandId ->
            viewModel.onAction(StationListAction.CommandHandled(commandId))
        },
    )

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

internal suspend fun openExternalMapOrShowFailure(
    command: StationListCommandPayload.OpenExternalMap,
    onOpenExternalMap: (StationListCommandPayload.OpenExternalMap) -> Boolean,
    snackbarHostState: SnackbarHostState,
    resources: Resources,
) {
    if (!onOpenExternalMap(command)) {
        snackbarHostState.showSnackbar(
            resources.getString(R.string.station_list_external_map_failed),
        )
    }
}

internal suspend fun handleAndAcknowledgeStationListCommand(
    command: StationListUiCommand,
    handle: suspend (StationListCommandPayload) -> Unit,
    acknowledge: (Long) -> Unit,
) {
    handle(command.payload)
    acknowledge(command.id)
}

@Composable
internal fun StationListCommandEffect(
    command: StationListUiCommand?,
    handle: suspend (StationListCommandPayload) -> Unit,
    acknowledge: (Long) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHandle by rememberUpdatedState(handle)
    val currentAcknowledge by rememberUpdatedState(acknowledge)
    var started by remember(lifecycle) { mutableStateOf(false) }
    var startGeneration by remember(lifecycle) { mutableLongStateOf(0L) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!started) {
                        startGeneration += 1L
                        started = true
                    }
                }

                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY,
                -> started = false

                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val startedForAttempt = started
    val generationForAttempt = startGeneration
    LaunchedEffect(command?.id, startedForAttempt, generationForAttempt) {
        if (!startedForAttempt || command == null) return@LaunchedEffect
        try {
            handleAndAcknowledgeStationListCommand(
                command = command,
                handle = currentHandle,
                acknowledge = currentAcknowledge,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Retain the exact queue head. The next START or route attachment may retry it.
        }
    }
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

internal fun isTerminalDeniedPermissionResult(results: Map<String, Boolean>): Boolean =
    results.isNotEmpty() && results.values.none { granted -> granted }

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
