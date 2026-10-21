package com.onvexa.recorder.app.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.OnvexaPalette

/** Chip de filtro con la forma y los colores de la app: rectángulo de 8 dp, selección en violeta tenue. */
@Composable
fun OnvexaChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, modifier = Modifier.padding(0.dp)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = OnvexaPalette.Background,
            labelColor = OnvexaPalette.TextMuted,
            iconColor = OnvexaPalette.TextMuted,
            selectedContainerColor = OnvexaPalette.PrimaryContainer,
            selectedLabelColor = OnvexaPalette.PrimaryLight,
            selectedLeadingIconColor = OnvexaPalette.PrimaryLight,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = OnvexaPalette.Outline,
            selectedBorderColor = OnvexaPalette.PrimaryLight.copy(alpha = 0.4f),
        ),
    )
}
