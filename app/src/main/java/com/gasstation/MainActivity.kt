package com.gasstation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gasstation.core.designsystem.GasStationTheme
import com.gasstation.map.ExternalMapLauncher
import com.gasstation.navigation.GasStationNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var externalMapLauncher: ExternalMapLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GasStationTheme {
                GasStationNavHost(
                    externalMapLauncher = externalMapLauncher,
                )
            }
        }
    }
}
