package com.onvexa.recorder.core.design.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography
import com.onvexa.recorder.core.design.OnvexaPalette as P
import com.onvexa.recorder.core.design.OnvexaType

private val WearColors = Colors(
    primary = P.Primary,
    primaryVariant = P.PrimaryLight,
    secondary = P.RecordingText,
    secondaryVariant = P.Recording,
    background = P.Background,
    surface = P.Surface,
    error = P.Error,
    onPrimary = P.OnPrimary,
    onSecondary = P.Background,
    onBackground = P.Text,
    onSurface = P.Text,
    onSurfaceVariant = P.TextMuted,
    onError = P.OnError,
)

private val WearTypography = Typography(
    display1 = OnvexaType.Timer,
    title1 = OnvexaType.TitleLarge,
    title2 = OnvexaType.Subtitle,
    body1 = OnvexaType.Body,
    body2 = OnvexaType.Caption,
    caption1 = OnvexaType.Caption,
    caption2 = OnvexaType.LabelSmall,
    button = OnvexaType.Label,
)

/** Tema del reloj (pantalla circular, fondo oscuro para ahorrar batería en OLED). */
@Composable
fun OnvexaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColors,
        typography = WearTypography,
        content = content,
    )
}
