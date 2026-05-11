package com.gasstation.feature.settings

import com.gasstation.core.designsystem.string.StringResource
import com.gasstation.core.model.Brand

data class SettingOptionUiModel(
    val label: StringResource,
    val subtitle: StringResource? = null,
    val meta: StringResource? = null,
    val action: SettingsAction,
    val isSelected: Boolean,
    val brandIconBrand: Brand? = null,
)
