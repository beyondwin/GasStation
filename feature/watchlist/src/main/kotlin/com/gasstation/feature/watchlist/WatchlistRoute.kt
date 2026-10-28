package com.gasstation.feature.watchlist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
fun WatchlistRoute(onNavigateNearby: () -> Unit, viewModel: WatchlistViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failureMessage = stringResource(R.string.watchlist_remove_failed)
    val failureId = uiState.pendingRemovalFailureId

    LaunchedEffect(lifecycle, viewModel, failureId, failureMessage) {
        if (failureId == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snackbarHostState.showSnackbar(failureMessage)
            currentCoroutineContext().ensureActive()
            viewModel.onAction(WatchlistAction.RemovalFailureHandled(failureId))
        }
    }

    WatchlistScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateNearby = onNavigateNearby,
        snackbarHostState = snackbarHostState,
    )
}
