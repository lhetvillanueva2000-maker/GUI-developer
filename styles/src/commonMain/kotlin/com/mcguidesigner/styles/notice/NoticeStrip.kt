package com.mcguidesigner.styles.notice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.theme.LocalMotion
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.spec
import kotlinx.coroutines.delay

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

/** How long a transient notice stays before it clears itself. */
const val TRANSIENT_NOTICE_MILLIS = 4_000L

/**
 * The notification panel, directly under the editor's top bar.
 *
 * **It is not there when there is nothing to say.** An empty panel is a bar of
 * chrome charging rent for a message it does not have, and on a phone that is
 * a row of pixels taken off the canvas permanently. So [notices] empty means
 * no strip at all, not a strip with nothing in it.
 *
 * When something does arrive it grows in as it comes down - scaling up from
 * just under full size with its origin at the top edge, so it reads as
 * unfolding out of the bar above rather than being pasted over the canvas. The
 * spring is what keeps that from feeling mechanical; a linear slide of the same
 * distance reads as a jump. Both halves are one gesture, which is why they
 * share a spring rather than being tuned separately.
 *
 * Collapsed it is one line. Pull it down - a swipe anywhere on a phone, the
 * chevron on a desktop - and it lists the detail. Both work everywhere: a
 * gesture nobody can see is not an affordance, and a small target is not a
 * good one for a thumb. Swiping up on a collapsed panel dismisses it.
 *
 * [expanded] is hoisted so the shell owns it and the panel does not forget its
 * state every time the editor recomposes around it.
 */
@Composable
fun NoticeStrip(
    notices: List<Notice>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: (Notice) -> Unit,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
    motion: MotionLevel = LocalMotion.current,
) {
    val notice = notices.firstOrNull()

    // `notices` is already empty while the panel animates *out*, so the last
    // real one is kept to draw during the exit. A plain holder rather than
    // snapshot state on purpose: this is a cache of what was just composed,
    // and making it observable would invalidate the composition that wrote it.
    val lastShown = remember { LastNotice() }
    notice?.let { lastShown.value = it }

    // Transient notices clear themselves. Keyed on the id so a replacement
    // arriving mid-countdown restarts it rather than inheriting the remainder
    // of the previous one's.
    LaunchedEffect(notice?.id) {
        val current = notice ?: return@LaunchedEffect
        if (!current.transient) return@LaunchedEffect
        delay(TRANSIENT_NOTICE_MILLIS)
        onDismiss(current)
    }

    AnimatedVisibility(
        visible = notice != null,
        enter = enterTransition(motion),
        exit = fadeOut(motion.spec(140)) +
            shrinkVertically(motion.spec(180), shrinkTowards = Alignment.Top),
        modifier = modifier,
    ) {
        val shown = lastShown.value ?: return@AnimatedVisibility
        NoticeBody(
            notice = shown,
            queued = notices.size,
            expanded = expanded && shown.expandable,
            onExpandedChange = onExpandedChange,
            onDismiss = { onDismiss(shown) },
            metrics = metrics,
            motion = motion,
        )
    }
}

/**
 * Down, and larger, at once.
 *
 * `TransformOrigin(0.5f, 0f)` is what makes it unfold from the bar above
 * instead of ballooning from its own middle, and 0.94 rather than something
 * smaller keeps it a settle rather than a pop - the panel is telling you
 * something, not demanding attention.
 */
private fun enterTransition(motion: MotionLevel) = if (!motion.animates) {
    fadeIn(motion.spec(0))
} else {
    val bounce = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        ),
        initialOffsetY = { height -> -height },
    ) + scaleIn(
        animationSpec = bounce,
        initialScale = 0.94f,
        transformOrigin = TransformOrigin(0.5f, 0f),
    ) + fadeIn(motion.spec(200))
}

@Composable
private fun NoticeBody(
    notice: Notice,
    queued: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    metrics: AdaptiveMetrics,
    motion: MotionLevel,
) {
    val palette = LocalSkinPalette.current
    var drag by remember { mutableStateOf(0f) }

    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "noticeChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                        val wantsOpen = dragOutcome(drag, velocity, expanded)
                        when {
                            // Up, from closed, with nothing to open: the only
                            // thing that gesture can mean is "go away".
                            !wantsOpen && !expanded -> onDismiss()
                            notice.expandable -> onExpandedChange(wantsOpen)
                            else -> Unit
                        }
                    },
                )
                // Tapping does the same thing. On a phone the swipe is the
                // natural gesture and on a desktop it is undiscoverable, so
                // neither one is allowed to be the only way in.
                .clickable(enabled = notice.expandable) { onExpandedChange(!expanded) },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (metrics.isCompact) 40.dp else 34.dp)
                    .padding(horizontal = metrics.gutter * 0.6f, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    notice.headline,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Only when there is genuinely a queue: "1" beside a single
                // message is a label for something the reader can already see.
                if (queued > 1) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.accentMuted)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "+${queued - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textOnAccent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

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

                if (notice.expandable) {
                    Text(
                        "⌄",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.chromeTextMuted,
                        modifier = Modifier.rotate(chevron),
                    )
                } else {
                    // Nothing to expand, so the affordance on offer is the
                    // only one left: getting rid of it.
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "✕",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spec(260)) + fadeIn(motion.spec(200, delayMillis = 60)),
                exit = shrinkVertically(motion.spec(200)) + fadeOut(motion.spec(120)),
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

                    Spacer(Modifier.height(2.dp))
                    // Discoverable where the swipe is not: a pointer has no
                    // way to guess that dragging the bar upwards dismisses it.
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}


/** Cache of the notice last composed, so the exit animation has something to draw. */
private class LastNotice { var value: Notice? = null }
