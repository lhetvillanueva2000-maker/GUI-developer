package com.mcguidesigner.styles.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.theme.LocalMotion
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.spec

/** One entry in the strip. The shell owns the documents; this only draws them. */
data class TabInfo(
    val title: String,
    val edition: Edition,
    val dirty: Boolean,
)

/**
 * The open-document strip.
 *
 * Each tab carries its edition's accent as a spine down its left edge, because
 * the single most important thing about a document here is which game it is
 * for - it decides the palette, the widgets and the export format - and reading
 * that off a colour is faster than reading it off a word.
 *
 * Scrolls horizontally rather than shrinking tabs towards illegibility. A tab
 * you cannot read is not a tab, and a row of eight two-character stubs is how
 * you close the wrong document.
 */
@Composable
fun DocumentTabs(
    tabs: List<TabInfo>,
    active: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onAdd: () -> Unit,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
    motion: MotionLevel = LocalMotion.current,
) {
    // One document is not a set of tabs, it is a document. Drawing a strip for
    // it would be a row of chrome that never changes and never gets used.
    if (tabs.size <= 1) return

    val palette = LocalSkinPalette.current
    val scroll = rememberScrollState()

    Row(
        modifier
            .fillMaxWidth()
            .background(palette.chromePanel)
            .heightIn(min = if (metrics.isCompact) 40.dp else 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                DocumentTabChip(
                    tab = tab,
                    selected = index == active,
                    metrics = metrics,
                    motion = motion,
                    onSelect = { onSelect(index) },
                    onClose = { onClose(index) },
                )
            }
        }

        Box(
            Modifier
                .size(if (metrics.isCompact) 40.dp else 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.titleSmall, color = palette.chromeTextMuted)
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun DocumentTabChip(
    tab: TabInfo,
    selected: Boolean,
    metrics: AdaptiveMetrics,
    motion: MotionLevel,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val accent = SkinRegistry.forEdition(tab.edition).palette.accent

    val fill by animateColorAsState(
        if (selected) palette.chromePanelAlt else Color.Transparent,
        motion.spec(180),
        label = "tabFill",
    )
    // The spine grows rather than appearing: a 3px bar snapping into existence
    // reads as a glitch, and the same 3px sliding out reads as a selection.
    val spine by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        motion.spec(220),
        label = "tabSpine",
    )

    Row(
        Modifier
            .heightIn(min = if (metrics.isCompact) 36.dp else 30.dp)
            .widthIn(min = 96.dp, max = 220.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(fill)
            .clickable(onClick = onSelect)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(spine)
                .height(if (metrics.isCompact) 36.dp else 30.dp)
                .background(accent),
        )
        Spacer(Modifier.width(if (selected) 7.dp else 10.dp))

        Text(
            tab.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.chromeText else palette.chromeTextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        // The unsaved dot sits where the close button would be and swaps for it
        // on approach, the way every editor does it - two separate affordances
        // in a 30px row is one too many.
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            if (tab.dirty) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.chromeTextMuted),
                )
            } else {
                Text("✕", fontSize = 10.sp, color = palette.chromeTextMuted)
            }
        }
    }
}
