package com.onvexa.recorder.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Estilos de texto compartidos. Usan la sans-serif del sistema; para igualar la tipografía
 * geométrica de la marca basta con cambiar [OnvexaFont] por una FontFamily empaquetada en res/font.
 */
object OnvexaFont {
    val Family: FontFamily = FontFamily.SansSerif
}

object OnvexaType {
    val Display = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
    val Title = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val Subtitle = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val Caption = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Label = TextStyle(fontFamily = OnvexaFont.Family, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)

    /** Cronómetro: cifras de ancho fijo para que no "baile" al correr. */
    val Timer = TextStyle(
        fontFamily = OnvexaFont.Family,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontFeatureSettings = "tnum",
    )
}
