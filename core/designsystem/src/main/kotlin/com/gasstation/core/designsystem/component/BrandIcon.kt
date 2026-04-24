package com.gasstation.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gasstation.core.designsystem.R
import com.gasstation.core.model.Brand

@DrawableRes
fun Brand.gasStationBrandIconResource(): Int = when (this) {
    Brand.SKE -> R.drawable.ic_ske
    Brand.GSC -> R.drawable.ic_gsc
    Brand.HDO -> R.drawable.ic_hdo
    Brand.SOL -> R.drawable.ic_sol
    Brand.RTO -> R.drawable.ic_rtx
    Brand.RTX -> R.drawable.ic_rtx
    Brand.NHO -> R.drawable.ic_rtx
    Brand.ETC -> R.drawable.ic_etc
    Brand.E1G -> R.drawable.ic_e1g
    Brand.SKG -> R.drawable.ic_skg
}

@Composable
fun GasStationBrandIcon(
    brand: Brand,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    Image(
        painter = painterResource(id = brand.gasStationBrandIconResource()),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
