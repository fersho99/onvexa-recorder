package com.onvexa.recorder.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Inter (licencia OFL, ver docs/licenses/inter-OFL.txt). */
object OnvexaFont {
    val Family: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )
}

object OnvexaType {
    private fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = OnvexaFont.Family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

    val Display = style(36, 42, FontWeight.SemiBold)
    val Title = style(24, 30, FontWeight.SemiBold)
    val TitleLarge = style(18, 24, FontWeight.SemiBold)
    val Subtitle = style(15, 21, FontWeight.Medium)
    val Body = style(14, 20, FontWeight.Normal)
    val Caption = style(12, 16, FontWeight.Normal)
    val Label = style(13, 18, FontWeight.Medium)
    val LabelSmall = style(11, 14, FontWeight.Medium)

    /** Cronómetro: cifras de ancho fijo para que no "baile" al correr. */
    val Timer = TextStyle(
        fontFamily = OnvexaFont.Family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontFeatureSettings = "tnum",
    )
}
