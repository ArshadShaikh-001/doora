package com.example.doora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Fixed palette of 14 ARGB task colours. */
val TASK_COLOR_PALETTE: List<Int> = listOf(
    0xFFEF5350.toInt(), // Red
    0xFFEC407A.toInt(), // Pink
    0xFFAB47BC.toInt(), // Purple
    0xFF7E57C2.toInt(), // Deep Purple
    0xFF42A5F5.toInt(), // Blue
    0xFF26C6DA.toInt(), // Cyan
    0xFF26A69A.toInt(), // Teal
    0xFF66BB6A.toInt(), // Green
    0xFFFFCA28.toInt(), // Amber
    0xFFFFA726.toInt(), // Orange
    0xFFFF7043.toInt(), // Deep Orange
    0xFF8D6E63.toInt(), // Brown
    0xFF78909C.toInt(), // Blue Grey
    0xFF607D8B.toInt(), // Slate
)

/**
 * Grid of colour swatches. Colours in [usedColors] are dimmed and non-clickable
 * (unless they equal [selectedColor], which is the current task's own colour).
 */
@Composable
fun ColorPicker(
    selectedColor: Int,
    usedColors: List<Int>,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = TASK_COLOR_PALETTE.chunked(7)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (colorInt in row) {
                    val isSelected = colorInt == selectedColor
                    val isUnavailable = colorInt in usedColors && !isSelected
                    ModernColorSwatch(
                        colorInt = colorInt,
                        isSelected = isSelected,
                        isUnavailable = isUnavailable,
                        onClick = { if (!isUnavailable) onColorSelected(colorInt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernColorSwatch(
    colorInt: Int,
    isSelected: Boolean,
    isUnavailable: Boolean,
    onClick: () -> Unit
) {
    val color = Color(colorInt)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else if (isUnavailable) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "swatchScale"
    )

    val ringBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "ringBorder"
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer selection ring
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .border(ringBorderWidth, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        // Color circle
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isUnavailable) color.copy(alpha = 0.2f) else color)
                .clickable(
                    enabled = !isUnavailable,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 20.dp),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
