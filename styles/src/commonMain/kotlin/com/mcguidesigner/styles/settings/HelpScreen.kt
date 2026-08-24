package com.mcguidesigner.styles.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.HelpBook
import com.mcguidesigner.core.HelpEntry
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Everything the app does, and how to ask for it.
 *
 * One page rather than a searchable index: there are about sixty entries, which
 * is short enough to scroll and read but long enough that a search box would be
 * the only thing anybody used - and a search box over sixty items is furniture.
 *
 * Actions with no keyboard shortcut are listed anyway, with the column left
 * blank. A help page that only lists the things with shortcuts is a shortcut
 * reference pretending to be help, and on a phone it would be nearly empty.
 */
@Composable
fun HelpScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: AdaptiveMetrics = LocalAdaptive.current,
) {
    val palette = LocalSkinPalette.current
    val scroll = rememberScrollState()

    Surface(modifier.fillMaxSize(), color = palette.chromeBackground) {
        Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.chromePanel)
                    .padding(horizontal = metrics.gutter * 0.5f, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(metrics.minTarget.coerceAtMost(48.dp))
                        .clip(RoundedCornerShape(metrics.corner * 0.5f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { BackIcon(size = 20.dp, ink = palette.chromeText) }

                Spacer(Modifier.width(4.dp))
                Text("Help", style = MaterialTheme.typography.titleMedium, color = palette.chromeText)
                Spacer(Modifier.weight(1f))
                Text(
                    "${Branding.NAME} ${Branding.VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = metrics.readingWidth * 1.15f)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.gutter)
                        .padding(top = metrics.sectionGap, bottom = metrics.sectionGap * 2),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    HelpBook.sections.forEach { section ->
                        Column {
                            Text(
                                section.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chromeTextMuted,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(palette.chromeBorder),
                            )
                            section.entries.forEach { entry ->
                                HelpRow(entry, metrics)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpRow(entry: HelpEntry, metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current

    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.action,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.chromeText,
            )
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.chromeTextMuted,
                )
            }
        }

        Spacer(Modifier.width(metrics.gap))

        // The key column keeps its width whether or not there is a key in it,
        // so the actions stay in one straight edge instead of jittering left
        // and right down the page.
        Box(Modifier.width(if (metrics.isCompact) 104.dp else 148.dp), contentAlignment = Alignment.TopEnd) {
            if (entry.keys.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.chromePanelAlt)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        entry.keys,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.chromeText,
                    )
                }
            }
        }
    }
}
