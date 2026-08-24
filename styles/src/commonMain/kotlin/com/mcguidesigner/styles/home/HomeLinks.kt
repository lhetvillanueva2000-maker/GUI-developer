package com.mcguidesigner.styles.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.PublicLink
import com.mcguidesigner.core.PublicLinkKind
import com.mcguidesigner.styles.layout.AdaptiveMetrics

/**
 * The two community destinations, under the edition cards.
 *
 * Deliberately *not* drawn as edition widgets. The cards above are real Java
 * and Bedrock containers because they are previewing the thing you are about
 * to design; these two leave the app entirely, so dressing them up as Minecraft
 * buttons would promise something they do not do. They are chrome - panel
 * colour, chrome border, the same monospaced label the rest of the shell uses -
 * sitting in the same two-column grid as the cards so the composition still
 * reads as one block rather than two unrelated rows.
 *
 * A link with no destination yet renders visibly unfinished and does not
 * respond. That is the honest shape: a button that looks live and opens
 * nothing is a bug report waiting to happen.
 */
@Composable
fun PublicLinkRow(
    links: List<PublicLink>,
    panel: Color,
    border: Color,
    text: Color,
    muted: Color,
    accent: Color,
    metrics: AdaptiveMetrics,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = @Composable { mod: Modifier ->
        links.forEach { link ->
            PublicLinkCard(
                link = link,
                panel = panel,
                border = border,
                text = text,
                muted = muted,
                accent = accent,
                metrics = metrics,
                onOpenLink = onOpenLink,
                modifier = mod,
            )
        }
    }

    // The same breakpoint the cards above use, so the two rows line up instead
    // of one wrapping while the other does not.
    if (HomeMetrics.sideBySide(metrics)) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap)) {
            cards(Modifier.weight(1f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            cards(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PublicLinkCard(
    link: PublicLink,
    panel: Color,
    border: Color,
    text: Color,
    muted: Color,
    accent: Color,
    metrics: AdaptiveMetrics,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(metrics.corner * 0.6f)
    val ink = if (link.isLive) text else muted

    Row(
        modifier
            .clip(shape)
            .background(panel.copy(alpha = if (link.isLive) 0.92f else 0.55f))
            .edge(border, shape = metrics.corner * 0.6f)
            .then(
                if (link.isLive) Modifier.clickable { onOpenLink(link.url) } else Modifier,
            )
            .padding(horizontal = metrics.gutter * 0.7f, vertical = metrics.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinkMark(kind = link.kind, ink = if (link.isLive) accent else muted, size = 22.dp)
        Spacer(Modifier.width(metrics.gap))

        Column(Modifier.weight(1f)) {
            Text(
                link.title,
                style = TextStyle(
                    color = ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                link.blurb,
                style = TextStyle(color = muted, fontSize = 11.5.sp, lineHeight = 15.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(metrics.gap * 0.6f))

        if (link.isLive) {
            // The arrow that means "this leaves the app", which is exactly what
            // it does - both of these open a browser.
            Text("↗", style = TextStyle(color = muted, fontSize = 15.sp))
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(muted.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "SOON",
                    style = TextStyle(
                        color = muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                    ),
                )
            }
        }
    }
}

/**
 * A one-pixel hairline around the card.
 *
 * Drawn rather than `Modifier.border` so it lands on the same half-pixel grid
 * as the bevels the edition cards use; a Material border on a rounded shape
 * antialiases to a slightly different weight and the row above stops matching
 * the row below.
 */
private fun Modifier.edge(color: Color, shape: Dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(color, Offset.Zero, Size(size.width, w))
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
    drawRect(color, Offset.Zero, Size(w, size.height))
    drawRect(color, Offset(size.width - w, 0f), Size(w, size.height))
}

/**
 * The two marks, drawn on the same 24-unit grid every other icon uses and
 * snapped to whole units so they stay pixel-crisp - which is the one piece of
 * the edition language that does belong here, because the whole app is drawn
 * that way.
 */
@Composable
private fun LinkMark(kind: PublicLinkKind, ink: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val extent = minOf(this.size.width, this.size.height)
        if (extent <= 0f) return@Canvas
        val u = extent / 24f
        val left = (this.size.width - extent) / 2f
        val top = (this.size.height - extent) / 2f
        fun px(x: Float, y: Float, w: Float, h: Float) =
            drawRect(ink, Offset(left + x * u, top + y * u), Size(w * u, h * u))

        when (kind) {
            // A block with a tab on one side and a socket on the other: the
            // shape of a thing that plugs into something else.
            PublicLinkKind.PLUGINS -> {
                px(4f, 6f, 12f, 12f)
                px(16f, 9f, 4f, 2f)
                px(16f, 13f, 4f, 2f)
                px(7f, 9f, 6f, 2f)
                px(7f, 13f, 3f, 2f)
            }
            // Four tiles with one lifted clear of the grid: a gallery, and the
            // one you are about to take.
            PublicLinkKind.CREATIONS -> {
                px(4f, 10f, 7f, 7f)
                px(13f, 10f, 7f, 7f)
                px(4f, 19f, 7f, 2f)
                px(13f, 4f, 7f, 4f)
            }
        }
    }
}
