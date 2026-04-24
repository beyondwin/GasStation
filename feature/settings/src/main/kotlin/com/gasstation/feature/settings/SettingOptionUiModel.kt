package com.gasstation.feature.settings

import com.gasstation.core.model.Brand

data class SettingOptionUiModel(
    val label: String,
    val subtitle: String? = null,
    val meta: String? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
    val brandIconBrand: Brand? = null,
)
