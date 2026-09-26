package com.example.doora.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.example.doora.ui.util.UiFeedbackHelper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doora.data.db.entity.TaskOccurrence
import com.example.doora.data.model.OccurrenceStatus
import com.example.doora.data.model.Priority
import com.example.doora.ui.theme.PriorityHighBg
import com.example.doora.ui.theme.PriorityHighColor
import com.example.doora.ui.theme.PriorityLowBg
import com.example.doora.ui.theme.PriorityLowColor
import com.example.doora.ui.theme.PriorityMediumBg
import com.example.doora.ui.theme.PriorityMediumColor
import com.example.doora.ui.theme.StatusCompletedColor
import com.example.doora.ui.theme.StatusMissedColor
import com.example.doora.ui.theme.StatusSkippedColor

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
    var justCompletedLocally by remember { mutableStateOf(false) }

    val isCompleted = occurrence.status == OccurrenceStatus.COMPLETED || justCompletedLocally
    val isPending = occurrence.status == OccurrenceStatus.PENDING && !justCompletedLocally
    val isSkipped = occurrence.status == OccurrenceStatus.SKIPPED
    val isMissed = occurrence.status == OccurrenceStatus.MISSED
    val isHighPriority = occurrence.priority == Priority.HIGH

    val taskColor = Color(occurrence.color)

    // Animated completion feedback
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCompleted || isSkipped) 0.5f else 1.0f,
        animationSpec = tween(durationMillis = 250),
        label = "contentAlpha"
    )

    val surfaceBg by animateColorAsState(
        targetValue = when {
            isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            isSkipped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            isHighPriority -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "surfaceBg"
    )

    val borderStroke = when {
        isHighPriority && isPending -> BorderStroke(1.dp, PriorityHighColor.copy(alpha = 0.25f))
        isSkipped -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceBg,
        border = borderStroke,
        tonalElevation = if (isHighPriority && isPending) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task Unique Color Accent Pill
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isCompleted || isSkipped) taskColor.copy(alpha = 0.4f) else taskColor)
            )

            Spacer(Modifier.width(14.dp))

            // Main Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                // Task Title
                Text(
                    text = occurrence.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Time + Priority + Status tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(occurrence.scheduledTimeMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    )

                    PriorityPill(priority = occurrence.priority, alpha = contentAlpha)

                    if (!isPending) {
                        StatusPill(status = occurrence.status)
                    }
                }
            }

            // Completion Circle Control (Only active when PENDING)
            if (isPending) {
                ModernCompletionButton(
                    color = taskColor,
                    isCompleted = justCompletedLocally,
                    onClick = {
                        justCompletedLocally = true
                        UiFeedbackHelper.playCompletionFeedback(context)
                        onComplete()
                    }
                )
            } else if (isCompleted) {
                // Completed Checkmark Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(StatusCompletedColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = StatusCompletedColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // More Options Dropdown
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Task options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isPending) {
                        DropdownMenuItem(
                            text = { Text("Skip for today") },
                            onClick = {
                                menuExpanded = false
                                onSkip()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernCompletionButton(
    color: Color,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "btnScale"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1.0f else 0.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
        label = "checkScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isCompleted) StatusCompletedColor else color.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 180),
        label = "borderColor"
    )

    val fillColor by animateColorAsState(
        targetValue = if (isCompleted) StatusCompletedColor else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "fillColor"
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                enabled = !isCompleted,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = {
                    pressed = true
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checkScale > 0.05f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier
                        .size(15.dp)
                        .scale(checkScale)
                )
            }
        }
    }
}

@Composable
fun PriorityPill(priority: Priority, alpha: Float = 1.0f) {
    val (label, textCol, bgCol) = when (priority) {
        Priority.HIGH -> Triple("High", PriorityHighColor, PriorityHighBg)
        Priority.MEDIUM -> Triple("Med", PriorityMediumColor, PriorityMediumBg)
        Priority.LOW -> Triple("Low", PriorityLowColor, PriorityLowBg)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgCol.copy(alpha = bgCol.alpha * alpha)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textCol.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StatusPill(status: OccurrenceStatus) {
    val (label, col) = when (status) {
        OccurrenceStatus.COMPLETED -> "Done" to StatusCompletedColor
        OccurrenceStatus.MISSED -> "Missed" to StatusMissedColor
        OccurrenceStatus.SKIPPED -> "Skipped" to StatusSkippedColor
        OccurrenceStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = col.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = col,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** Formats minutes since midnight into a clean display time "h:mm a" */
fun formatTime(minutes: Int): String {
    val h24 = minutes / 60
    val m = minutes % 60
    val amPm = if (h24 >= 12) "PM" else "AM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "%d:%02d %s".format(h12, m, amPm)
}

