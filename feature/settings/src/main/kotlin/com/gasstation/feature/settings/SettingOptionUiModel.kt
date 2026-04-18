package com.gasstation.feature.settings

data class SettingOptionUiModel(
    val label: String,
    val subtitle: String? = null,
    val meta: String? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
)
