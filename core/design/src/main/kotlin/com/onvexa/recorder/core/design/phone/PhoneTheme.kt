package com.onvexa.recorder.core.design.phone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.onvexa.recorder.core.design.OnvexaPalette as P
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.OnvexaType

private val PhoneColors = darkColorScheme(
    primary = P.Primary,
    onPrimary = P.OnPrimary,
    primaryContainer = P.PrimaryContainer,
    onPrimaryContainer = P.PrimaryLight,
    inversePrimary = P.PrimaryLight,
    secondary = P.PrimaryLight,
    onSecondary = P.Background,
    secondaryContainer = P.PrimaryContainer,
    onSecondaryContainer = P.PrimaryLight,
    tertiary = P.Recording,
    onTertiary = P.OnRecording,
    tertiaryContainer = P.SurfaceHigh,
    onTertiaryContainer = P.RecordingText,
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
    outline = P.OutlineStrong,
    outlineVariant = P.Outline,
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
    titleLarge = OnvexaType.TitleLarge,
    titleMedium = OnvexaType.Subtitle,
    bodyLarge = OnvexaType.Subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
    bodyMedium = OnvexaType.Body,
    bodySmall = OnvexaType.Caption,
    labelLarge = OnvexaType.Label,
    labelMedium = OnvexaType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
    labelSmall = OnvexaType.LabelSmall,
)

private val PhoneShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Tema del celular. Solo modo oscuro. */
@Composable
fun OnvexaPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhoneColors,
        typography = PhoneTypography,
        shapes = PhoneShapes,
        content = content,
    )
}
