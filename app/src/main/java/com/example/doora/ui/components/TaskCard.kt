package com.example.doora.ui.components

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import java.util.Locale

@Composable
fun TaskCard(
    occurrence: TaskOccurrence,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var justCompleted by remember { mutableStateOf(false) }

    val cardScale by animateFloatAsState(
        targetValue = if (justCompleted) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        finishedListener = { justCompleted = false },
        label = "cardScale"
    )

    val taskColor = Color(occurrence.color)
    val isCompleted = occurrence.status == OccurrenceStatus.COMPLETED
    val isPending = occurrence.status == OccurrenceStatus.PENDING

    val cardBackground by animateColorAsState(
        targetValue = if (isCompleted)
            taskColor.copy(alpha = 0.06f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(400),
        label = "cardBg"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Coloured left-edge strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(
                        color = when (occurrence.status) {
                            OccurrenceStatus.COMPLETED -> taskColor.copy(alpha = 0.45f)
                            OccurrenceStatus.SKIPPED   -> taskColor.copy(alpha = 0.30f)
                            OccurrenceStatus.MISSED    -> taskColor.copy(alpha = 0.30f)
                            OccurrenceStatus.PENDING   -> taskColor
                        }
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title + meta
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = occurrence.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(occurrence.scheduledTimeMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PriorityBadge(priority = occurrence.priority)
                        if (!isPending) {
                            StatusBadge(status = occurrence.status)
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Action: complete (only for PENDING)
                if (isPending) {
                    IconButton(
                        onClick = {
                            justCompleted = true
                            vibrate(context)
                            onComplete()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Complete task",
                            tint = taskColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (isPending) {
                            DropdownMenuItem(
                                text = { Text("Skip") },
                                onClick = { menuExpanded = false; onSkip() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Priority) {
    val (bgColor, fgColor, label) = when (priority) {
        Priority.HIGH   -> Triple(Color(0xFFEF5350).copy(alpha = 0.15f), Color(0xFFEF5350), "High")
        Priority.MEDIUM -> Triple(Color(0xFFFFA726).copy(alpha = 0.15f), Color(0xFFFFA726), "Medium")
        Priority.LOW    -> Triple(Color(0xFF66BB6A).copy(alpha = 0.15f), Color(0xFF66BB6A), "Low")
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bgColor) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatusBadge(status: OccurrenceStatus) {
    val (bg, fg, label) = when (status) {
        OccurrenceStatus.COMPLETED -> Triple(Color(0xFF66BB6A).copy(alpha = 0.15f), Color(0xFF66BB6A), "Done")
        OccurrenceStatus.SKIPPED   -> Triple(Color(0xFF78909C).copy(alpha = 0.15f), Color(0xFF78909C), "Skipped")
        OccurrenceStatus.MISSED    -> Triple(Color(0xFFEF5350).copy(alpha = 0.15f), Color(0xFFEF5350), "Missed")
        OccurrenceStatus.PENDING   -> return
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Converts minutes-since-midnight to a 12-hour "h:mm AM/PM" string. */
fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val amPm = if (h < 12) "AM" else "PM"
    val hour12 = when {
        h == 0  -> 12
        h > 12  -> h - 12
        else    -> h
    }
    return String.format(Locale.getDefault(), "%d:%02d %s", hour12, m, amPm)
}

private fun vibrate(context: Context) {
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(1000L)
    }
}
