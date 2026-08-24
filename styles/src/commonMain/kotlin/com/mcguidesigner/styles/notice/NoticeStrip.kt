package com.mcguidesigner.styles.notice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Whether a drag on the strip should open it, close it, or leave it alone.
 *
 * Pulled out as a pure function because gesture thresholds are exactly the
 * kind of thing that gets tuned by feel and then silently regresses: a sign
 * flip here turns "swipe down to open" into "swipe down to close" and no test
 * that looks at composition would notice.
 *
 * Down is positive, matching the pointer axis. A flick counts even when it
 * covered almost no distance, which is what makes a fast swipe feel like it
 * worked rather than like it was ignored.
 */
internal fun dragOutcome(
    totalDrag: Float,
    velocity: Float,
    expanded: Boolean,
    distanceThreshold: Float = DRAG_DISTANCE_THRESHOLD,
    velocityThreshold: Float = DRAG_VELOCITY_THRESHOLD,
): Boolean {
    val opening = totalDrag > distanceThreshold || velocity > velocityThreshold
    val closing = totalDrag < -distanceThreshold || velocity < -velocityThreshold
    return when {
        opening -> true
        closing -> false
        else -> expanded
    }
}

/** Far enough to be a swipe rather than a slipped tap. */
internal const val DRAG_DISTANCE_THRESHOLD = 24f

/** Fast enough to be a flick, in pixels per second. */
internal const val DRAG_VELOCITY_THRESHOLD = 320f

/**
 * The what's-new strip, directly under the editor's top bar.
 *
 * Collapsed it is one line: a version chip and a headline. Expanded it lists
 * the detail. It opens two ways on purpose - a downward swipe anywhere on the
 * strip, which is what a finger reaches for, and the chevron on the right,
 * which is the only one a pointer can discover. Both are always live; a
 * gesture nobody can see is not an affordance, and a button a thumb has to
 * aim at is not a good one.
 *
 * [expanded] is hoisted so the shell owns it and the strip does not forget its
 * state every time the editor recomposes around it.
 */
@Composable
fun NoticeStrip(
    notice: UpdateNotice,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    var drag by remember { mutableStateOf(0f) }

    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "noticeChevron",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.chromePanelAlt,
        contentColor = palette.chromeText,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .draggable(
                    state = rememberDraggableState { delta -> drag += delta },
                    orientation = Orientation.Vertical,
                    onDragStarted = { drag = 0f },
                    onDragStopped = { velocity ->
                        onExpandedChange(dragOutcome(drag, velocity, expanded))
                    },
                )
                // Tapping does the same thing. On a phone the swipe is the
                // natural gesture and on a desktop it is undiscoverable, so
                // neither one is allowed to be the only way in.
                .clickable { onExpandedChange(!expanded) },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (metrics.isCompact) 40.dp else 34.dp)
                    .padding(horizontal = metrics.gutter * 0.6f, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.accentMuted)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        notice.version,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textOnAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    notice.headline,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // The grab handle on a phone: a short bar is the standard
                // "this sheet pulls" cue, and it reads at a glance where a
                // chevron alone does not.
                if (metrics.isCompact) {
                    Box(
                        Modifier
                            .size(width = 22.dp, height = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.chromeTextMuted),
                    )
                    Spacer(Modifier.width(2.dp))
                }

                Text(
                    "⌄",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.rotate(chevron),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(200, delayMillis = 60)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(120)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = metrics.gutter * 0.6f,
                            end = metrics.gutter * 0.6f,
                            bottom = metrics.gap,
                        ),
                    verticalArrangement = Arrangement.spacedBy(metrics.gap * 0.7f),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.chromeBorder),
                    )
                    Spacer(Modifier.height(2.dp))
                    notice.points.forEach { point ->
                        Row(
                            // Capped rather than full-bleed: the strip spans a
                            // 1600px window and a line of prose that long is
                            // one the eye loses its place in.
                            Modifier.widthIn(max = metrics.readingWidth * 1.15f),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text(
                                "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent,
                            )
                            Text(
                                point,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.chromeTextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}
