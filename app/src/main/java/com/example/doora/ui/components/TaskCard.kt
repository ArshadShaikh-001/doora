package com.example.doora.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
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
    isRecurring: Boolean = false,
    isOverdue: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var justCompletedLocally by remember { mutableStateOf(false) }

    val isCompleted = occurrence.status == OccurrenceStatus.COMPLETED || justCompletedLocally
    val isPending = occurrence.status == OccurrenceStatus.PENDING && !justCompletedLocally
    val isSkipped = occurrence.status == OccurrenceStatus.SKIPPED
    val isEffectivelyOverdue = isOverdue && isPending

    val taskColor = Color(occurrence.color)

    // Animated completion feedback
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCompleted || isSkipped) 0.55f else 1.0f,
        animationSpec = tween(durationMillis = 250),
        label = "contentAlpha"
    )

    val surfaceBg = if (isCompleted) com.example.doora.ui.theme.LuxuryCompletedCardSurface else com.example.doora.ui.theme.LuxuryCardSurface
    val borderStroke = when {
        isCompleted -> BorderStroke(1.dp, com.example.doora.ui.theme.GoldBorderSubdued)
        else -> BorderStroke(1.dp, com.example.doora.ui.theme.GoldBorder)
    }

    Surface(
        onClick = onEdit,
        modifier = modifier
            .fillMaxWidth()
            .travelingBorderHighlight(
                enabled = isEffectivelyOverdue,
                color = taskColor,
                cornerRadius = 22.dp,
                strokeWidth = 1.3.dp,
                highlightLength = 80.dp,
                durationMillis = 2400
            ),
        shape = RoundedCornerShape(22.dp),
        color = surfaceBg,
        border = borderStroke,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Task Unique Color Accent Pill
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isCompleted || isSkipped) taskColor.copy(alpha = 0.45f)
                        else taskColor
                    )
            )

            Spacer(Modifier.width(14.dp))

            // Main Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                // Task Title
                Text(
                    text = occurrence.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCompleted) com.example.doora.ui.theme.TextCompletedDark else Color.White,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                // Time + Priority + Status / Overdue tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(occurrence.scheduledTimeMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isCompleted) com.example.doora.ui.theme.TextCompletedDark.copy(alpha = 0.8f) else Color(0xFF9E988F)
                    )

                    PriorityPill(priority = occurrence.priority, isDimmed = isCompleted)

                    if (isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = Color(0x280D5A36)
                        ) {
                            Text(
                                text = "Done",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF26D07C),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isSkipped) {
                        StatusPill(status = occurrence.status)
                    } else if (occurrence.status == OccurrenceStatus.MISSED) {
                        StatusPill(status = occurrence.status)
                    } else if (isEffectivelyOverdue) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = taskColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Overdue",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = taskColor.copy(alpha = 0.9f),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Completion Circle Control (on the right)
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
                // Completed Checkmark Badge in subtle tone
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = com.example.doora.ui.theme.TextCompletedDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Three-dot Options Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Task options",
                        tint = if (isCompleted) com.example.doora.ui.theme.GoldSecondary.copy(alpha = 0.5f) else com.example.doora.ui.theme.GoldSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(14.dp),
                    containerColor = com.example.doora.ui.theme.LuxuryCardSurface,
                    border = BorderStroke(1.dp, com.example.doora.ui.theme.GoldBorder)
                ) {
                    if (isPending) {
                        val skipLabel = if (isRecurring) "Skip for today" else "Skip"
                        DropdownMenuItem(
                            text = { Text(skipLabel, color = com.example.doora.ui.theme.TextPrimaryDark) },
                            onClick = {
                                menuExpanded = false
                                onSkip()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit", color = com.example.doora.ui.theme.TextPrimaryDark) },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = com.example.doora.ui.theme.GoldPrimary, modifier = Modifier.size(18.dp))
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
        targetValue = if (pressed) 0.86f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "btnScale"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1.0f else 0.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
        label = "checkScale"
    )

    val fillColor by animateColorAsState(
        targetValue = if (isCompleted) color.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "fillColor"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
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
                .size(28.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checkScale > 0.05f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed",
                    tint = color,
                    modifier = Modifier
                        .size(16.dp)
                        .scale(checkScale)
                )
            }
        }
    }
}

@Composable
fun PriorityPill(priority: Priority, isDimmed: Boolean = false) {
    val (label, textCol, bgCol) = when (priority) {
        Priority.HIGH -> Triple("High", PriorityHighColor, PriorityHighBg)
        Priority.MEDIUM -> Triple("Med", PriorityMediumColor, PriorityMediumBg)
        Priority.LOW -> Triple("Low", PriorityLowColor, PriorityLowBg)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDimmed) bgCol.copy(alpha = bgCol.alpha * 0.45f) else bgCol
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDimmed) textCol.copy(alpha = 0.5f) else textCol,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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

/**
 * Premium traveling light highlight around the card's rounded outer border.
 * Continuously loops: top-left -> top -> right -> bottom -> left -> back around.
 * Automatically active only when enabled = true (overdue and pending), and terminates
 * immediately upon completion, skip, delete, or rollover to MISSED status.
 */
@Composable
fun Modifier.travelingBorderHighlight(
    enabled: Boolean,
    color: Color,
    cornerRadius: Dp = 22.dp,
    strokeWidth: Dp = 1.3.dp,
    highlightLength: Dp = 80.dp,
    durationMillis: Int = 2400
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "travelingBorderHighlight")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "highlightProgress"
    )

    return this.drawWithCache {
        val strokeWidthPx = strokeWidth.toPx()
        val halfStroke = strokeWidthPx / 2f
        val cornerRadiusPx = (cornerRadius.toPx() - halfStroke).coerceAtLeast(0f)

        val borderPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        offset = Offset(halfStroke, halfStroke),
                        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                    ),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            )
        }

        val pathMeasure = PathMeasure().apply {
            setPath(borderPath, forceClosed = true)
        }
        val perimeter = pathMeasure.length

        val beamLengthPx = minOf(highlightLength.toPx(), perimeter * 0.25f)
        val numSegments = 12
        val segLen = beamLengthPx / numSegments
        val scratchPath = Path()

        onDrawWithContent {
            // 1. Draw card content and existing normal border first
            drawContent()

            // 2. Draw subtle traveling light highlight along the border
            if (perimeter > 0f) {
                val centerDist = progress * perimeter
                val beamStart = centerDist - beamLengthPx / 2f

                for (i in 0 until numSegments) {
                    val s1 = beamStart + i * segLen
                    val s2 = beamStart + (i + 1) * segLen

                    val t = (i + 0.5f) / numSegments
                    val sinVal = sin(t * PI.toFloat())
                    val alpha = (sinVal * sinVal) * 0.95f

                    drawBeamSegment(
                        drawScope = this,
                        pathMeasure = pathMeasure,
                        scratchPath = scratchPath,
                        startDist = s1,
                        stopDist = s2,
                        perimeter = perimeter,
                        color = color.copy(alpha = alpha),
                        strokeWidth = strokeWidthPx
                    )

                    // Core bright sheen at the center of the highlight
                    if (sinVal > 0.75f) {
                        val coreAlpha = ((sinVal - 0.75f) / 0.25f) * 0.45f
                        drawBeamSegment(
                            drawScope = this,
                            pathMeasure = pathMeasure,
                            scratchPath = scratchPath,
                            startDist = s1,
                            stopDist = s2,
                            perimeter = perimeter,
                            color = Color.White.copy(alpha = coreAlpha),
                            strokeWidth = strokeWidthPx * 0.75f
                        )
                    }
                }
            }
        }
    }
}

private fun drawBeamSegment(
    drawScope: ContentDrawScope,
    pathMeasure: PathMeasure,
    scratchPath: Path,
    startDist: Float,
    stopDist: Float,
    perimeter: Float,
    color: Color,
    strokeWidth: Float
) {
    if (perimeter <= 0f) return
    val len = stopDist - startDist
    if (len <= 0f) return

    val s1 = (startDist % perimeter + perimeter) % perimeter
    val s2 = s1 + len

    if (s2 <= perimeter) {
        scratchPath.reset()
        pathMeasure.getSegment(s1, s2, scratchPath, true)
        drawScope.drawPath(
            path = scratchPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
    } else {
        scratchPath.reset()
        pathMeasure.getSegment(s1, perimeter, scratchPath, true)
        pathMeasure.getSegment(0f, s2 - perimeter, scratchPath, true)
        drawScope.drawPath(
            path = scratchPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt, join = StrokeJoin.Round)
        )
    }
}


