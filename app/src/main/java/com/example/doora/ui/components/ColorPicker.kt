package com.example.doora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    0xFFBDBDBD.toInt(), // Grey
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val rows = TASK_COLOR_PALETTE.chunked(7)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (colorInt in row) {
                    val isSelected = colorInt == selectedColor
                    val isUnavailable = colorInt in usedColors && !isSelected
                    ColorSwatch(
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
private fun ColorSwatch(
    colorInt: Int,
    isSelected: Boolean,
    isUnavailable: Boolean,
    onClick: () -> Unit
) {
    val color = Color(colorInt)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (isUnavailable) color.copy(alpha = 0.25f) else color)
            .then(
                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable(enabled = !isUnavailable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
