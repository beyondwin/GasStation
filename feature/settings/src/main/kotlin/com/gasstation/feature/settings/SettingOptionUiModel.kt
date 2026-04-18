package com.gasstation.feature.settings

data class SettingOptionUiModel(
    val label: String,
    val action: SettingsAction,
    val isSelected: Boolean,
)
