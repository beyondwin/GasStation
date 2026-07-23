package com.gasstation.feature.settings

sealed interface SettingsEffect {
    data class SelectionSaved(val section: SettingsSection) : SettingsEffect

    data object SaveFailed : SettingsEffect
}
