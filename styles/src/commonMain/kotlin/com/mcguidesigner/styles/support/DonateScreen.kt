@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.mcguidesigner.styles.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.core.support.DonationDetail
import com.mcguidesigner.core.support.SupportedApps
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.layout.WindowSizeClass
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The support page.
 *
 * One screen, three shapes.  On a phone it is a single scrolling column with
 * the code big enough to point another phone at; on a tablet and on the desktop
 * the reading matter moves into a column beside the code so neither has to be
 * scrolled past to reach the other.  Nothing here is edition-specific, but it
 * takes the active edition's chrome colours like every other panel, because a
 * page that ignored the theme would look bolted on - which it is not.
 *
 * [onSaveQr] is given the code's original bytes.  Long-pressing the code calls
 * it, and so does the button underneath: the gesture is the one people expect
 * from a QR, and the button is the one they can find.
 */
@Composable
fun DonateScreen(
    onClose: () -> Unit,
    onSaveQr: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    metrics: AdaptiveMetrics = LocalAdaptive.current,
    onCopied: (String) -> Unit = {},
) {
    val palette = LocalSkinPalette.current
    val qr = LocalDonationQr.current

    Surface(modifier.fillMaxSize(), color = palette.chromeBackground) {
        Column(Modifier.fillMaxSize()) {
            DonateTopBar(onClose = onClose, metrics = metrics)

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val twoColumn = metrics.sizeClass.atLeastMedium && maxWidth >= 720.dp
                val scroll = rememberScrollState()

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = metrics.gutter)
                        .padding(top = metrics.gutter, bottom = metrics.gutter * 2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Hero(metrics)
                    Spacer(Modifier.height(metrics.sectionGap))

                    if (twoColumn) {
                        Row(
                            Modifier.widthIn(max = 1180.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.gap * 2),
                        ) {
                            Column(Modifier.weight(1.15f)) {
                                MessageCard(metrics)
                                Spacer(Modifier.height(metrics.gap * 2))
                                DetailsCard(metrics, onCopied)
                            }
                            Column(Modifier.weight(1f)) {
                                QrCard(qr, metrics, onSaveQr)
                            }
                        }
                        Spacer(Modifier.height(metrics.sectionGap))
                        Box(Modifier.widthIn(max = 1180.dp).fillMaxWidth()) {
                            SupportedAppsPanel(metrics)
                        }
                    } else {
                        Column(Modifier.widthIn(max = metrics.readingWidth).fillMaxWidth()) {
                            MessageCard(metrics)
                            Spacer(Modifier.height(metrics.gap * 2))
                            QrCard(qr, metrics, onSaveQr)
                            Spacer(Modifier.height(metrics.gap * 2))
                            DetailsCard(metrics, onCopied)
                            Spacer(Modifier.height(metrics.gap * 2))
                            SupportedAppsPanel(metrics)
                        }
                    }

                    Spacer(Modifier.height(metrics.sectionGap))
                    Text(
                        "No ads. No tracking. No account. This page is the only thing " +
                            "the app ever asks you for.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = metrics.readingWidth),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

@Composable
private fun DonateTopBar(onClose: () -> Unit, metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    Surface(color = palette.chromePanel, contentColor = palette.chromeText) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (metrics.sizeClass.isCompact) 56.dp else 64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .combinedClickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text("←", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Support the designer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            DonateIcon(
                size = 24.dp,
                ink = palette.chromeText,
                slot = palette.chromePanel,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
    }
}

@Composable
private fun Hero(metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    val big = metrics.sizeClass != WindowSizeClass.COMPACT

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DonateIcon(
            size = if (big) 72.dp else 56.dp,
            ink = palette.chromeText,
            slot = palette.chromeBackground,
        )
        Spacer(Modifier.height(metrics.gap))
        Text(
            Donation.HEADLINE,
            style = MaterialTheme.typography.titleMedium,
            fontSize = if (big) 30.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = palette.chromeText,
            textAlign = TextAlign.Center,
            lineHeight = if (big) 36.sp else 30.sp,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Free forever either way.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.chromeTextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun CardSurface(
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.chromePanel,
        shape = RoundedCornerShape(metrics.corner),
        border = BorderStroke(1.dp, palette.chromeBorder),
    ) {
        Box(Modifier.padding(metrics.gutter)) { content() }
    }
}

@Composable
private fun MessageCard(metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    CardSurface(metrics) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            Donation.MESSAGE.forEachIndexed { index, paragraph ->
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == 0) palette.chromeText else palette.chromeTextMuted,
                    fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(metrics: AdaptiveMetrics, onCopied: (String) -> Unit) {
    val palette = LocalSkinPalette.current
    val clipboard = LocalClipboardManager.current

    CardSurface(metrics) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
            Text(
                "Or send it manually",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.chromeText,
            )
            Text(
                "Tap any line to copy it.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
            Spacer(Modifier.height(2.dp))
            Donation.details.forEach { detail ->
                DetailRow(detail, metrics) {
                    clipboard.setText(AnnotatedString(detail.value))
                    onCopied("${detail.label} copied.")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(detail: DonationDetail, metrics: AdaptiveMetrics, onCopy: () -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.corner / 2))
            .background(palette.chromePanelAlt)
            .combinedClickable(onClick = onCopy)
            .heightIn(min = metrics.minTarget)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    detail.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
                detail.note?.let {
                    Text(
                        "  ·  $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                }
            }
            Text(
                detail.value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = palette.chromeText,
            )
        }
        Text("⧉", style = MaterialTheme.typography.titleSmall, color = palette.chromeTextMuted)
    }
}

/**
 * The code itself.
 *
 * Always on a white plate with a real margin around it, whatever the theme is
 * doing: a QR inverted for dark mode, or bled to the edge of its card, is a QR
 * that half the scanners in the world will refuse - and this one is a payment
 * code, so "usually works" is not good enough.
 */
@Composable
private fun QrCard(
    qr: DonationQr?,
    metrics: AdaptiveMetrics,
    onSaveQr: (ByteArray) -> Unit,
) {
    val palette = LocalSkinPalette.current
    var saved by remember { mutableStateOf(false) }

    CardSurface(metrics) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.gap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                Donation.QR_CAPTION,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.chromeText,
            )

            if (qr == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .clip(RoundedCornerShape(metrics.corner))
                        .background(palette.chromePanelAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "The QR image could not be loaded.\nThe numbers below work just as well.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.chromeTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Box(
                    Modifier
                        .widthIn(max = if (metrics.sizeClass.isCompact) 280.dp else 320.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(metrics.corner))
                        .background(Color.White)
                        .border(1.dp, palette.chromeBorder, RoundedCornerShape(metrics.corner))
                        .combinedClickable(
                            onClick = { onSaveQr(qr.bytes); saved = true },
                            onLongClick = { onSaveQr(qr.bytes); saved = true },
                        )
                        .padding(12.dp),
                ) {
                    Image(
                        bitmap = qr.image,
                        contentDescription = "InstaPay QR code for ${Donation.NAME}",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }

                Text(
                    Donation.QR_HINT,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = { onSaveQr(qr.bytes); saved = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = metrics.minTarget),
                    shape = RoundedCornerShape(metrics.corner / 2),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.textOnAccent,
                    ),
                ) {
                    Text(if (saved) "Save the QR code again" else "Save the QR code")
                }
            }

            Text(
                "InstaPay / QR Ph  ·  ${SupportedApps.count} apps can pay it",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The long list
// ---------------------------------------------------------------------------

@Composable
private fun SupportedAppsPanel(metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "chevron",
    )

    CardSurface(metrics) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.corner / 2))
                    .combinedClickable(onClick = { expanded = !expanded })
                    .heightIn(min = metrics.minTarget)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Which apps can pay this QR code?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.chromeText,
                    )
                    Text(
                        "${SupportedApps.count} apps across " +
                            "${SupportedApps.groups.size} categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                    )
                }
                Text(
                    "⌄",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.rotate(chevron).padding(end = 4.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = metrics.gap)) {
                    SupportedApps.groups.forEach { group ->
                        Text(
                            group.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent,
                            modifier = Modifier.padding(top = metrics.gap, bottom = 6.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            group.apps.forEach { app -> AppChip(app) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppChip(name: String) {
    val palette = LocalSkinPalette.current
    Surface(
        color = palette.chromePanelAlt,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, palette.chromeBorder),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
