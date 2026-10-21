package com.onvexa.recorder.core.design.phone

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.onvexa.recorder.core.design.OnvexaPalette as P
import com.onvexa.recorder.core.design.OnvexaType

private val PhoneColors = darkColorScheme(
    primary = P.Primary,
    onPrimary = P.OnPrimary,
    primaryContainer = P.SurfaceHigh,
    onPrimaryContainer = P.PrimaryLight,
    inversePrimary = P.PrimaryLight,
    secondary = P.Secondary,
    onSecondary = P.OnSecondary,
    secondaryContainer = P.SurfaceHigh,
    onSecondaryContainer = P.Text,
    tertiary = P.Recording,
    onTertiary = P.OnRecording,
    tertiaryContainer = P.SurfaceHigh,
    onTertiaryContainer = P.Accent,
    background = P.Background,
    onBackground = P.Text,
    surface = P.Surface,
    onSurface = P.Text,
    surfaceVariant = P.SurfaceHigh,
    onSurfaceVariant = P.TextMuted,
    surfaceTint = P.Primary,
    inverseSurface = P.Text,
    inverseOnSurface = P.Background,
    error = P.Error,
    onError = P.OnError,
    errorContainer = P.SurfaceHigh,
    onErrorContainer = P.Error,
    outline = P.Outline,
    outlineVariant = P.SurfaceHigh,
    surfaceDim = P.Background,
    surfaceBright = P.SurfaceHigh,
    surfaceContainerLowest = P.Background,
    surfaceContainerLow = P.Background,
    surfaceContainer = P.Surface,
    surfaceContainerHigh = P.SurfaceHigh,
    surfaceContainerHighest = P.SurfaceHigh,
)

private val PhoneTypography = Typography(
    displayLarge = OnvexaType.Display,
    headlineMedium = OnvexaType.Title,
    titleMedium = OnvexaType.Subtitle,
    bodyLarge = OnvexaType.Body,
    bodyMedium = OnvexaType.Body,
    bodySmall = OnvexaType.Caption,
    labelLarge = OnvexaType.Label,
    labelMedium = OnvexaType.Label,
    labelSmall = OnvexaType.Caption,
)

/** Tema del celular. Solo modo oscuro (estilo HUD). */
@Composable
fun OnvexaPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhoneColors,
        typography = PhoneTypography,
        content = content,
    )
}
