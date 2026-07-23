package com.gasstation.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.ColorNeutralLine
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.core.designsystem.component.GasStationBackground
import com.gasstation.core.designsystem.component.GasStationGuidanceCard
import com.gasstation.core.designsystem.component.GasStationRowDivider
import com.gasstation.core.designsystem.component.GasStationTopBar

@Composable
fun SettingsLoadingScreen() {
    val loadingLabel = stringResource(R.string.settings_loading)

    SettingsStateScaffold {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = loadingLabel },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        ) {
            item {
                Text(
                    text = loadingLabel,
                    style = GasStationTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(3) { index ->
                SettingsLoadingRow()
                if (index < 2) {
                    GasStationRowDivider()
                }
            }
        }
    }
}

@Composable
fun SettingsLoadFailureScreen(onRetry: () -> Unit) {
    SettingsStateScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            GasStationGuidanceCard(
                title = stringResource(R.string.settings_load_failed_title),
                body = stringResource(R.string.settings_load_failed_body),
                actionLabel = stringResource(R.string.settings_retry),
                onAction = onRetry,
            )
        }
    }
}

@Composable
private fun SettingsStateScaffold(content: @Composable () -> Unit) {
    GasStationBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                GasStationTopBar(
                    title = { Text(text = stringResource(R.string.settings_title)) },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsLoadingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LoadingPlaceholder(width = 112.dp, height = 20.dp)
        LoadingPlaceholder(width = 72.dp, height = 18.dp)
        LoadingPlaceholder(width = 236.dp, height = 14.dp)
    }
}

@Composable
private fun LoadingPlaceholder(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Spacer(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(ColorNeutralLine.copy(alpha = 0.72f)),
    )
}
