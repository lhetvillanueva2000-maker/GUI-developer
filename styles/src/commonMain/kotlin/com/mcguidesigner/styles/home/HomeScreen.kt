package com.mcguidesigner.styles.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.support.DonateIcon
import com.mcguidesigner.styles.support.DonateScreen
import com.mcguidesigner.styles.support.ThemeIcon
import com.mcguidesigner.styles.theme.SkinRegistry

/**
 * The home screen: pick an edition, or open the support page.
 *
 * Home has no edition of its own - it comes before that choice - so instead of
 * inventing a third neutral chrome it wears the chrome of whichever edition the
 * pointer or focus is currently over, falling back to [lastUsed]. The whole
 * shell repaints as you move between the two cards, so the app changes identity
 * before you commit to anything.
 *
 * The support mark in the top bar is the feature's only entry point in the app.
 * The screen is hosted here rather than handed out through a callback, because
 * `DonateScreen` already owns its own dismissal and there is nothing for a
 * caller to decide; all the host still has to supply is somewhere to put a
 * saved file.
 *
 * There is no donation URL to point at. The payment is an InstaPay / QR Ph
 * code plus four copyable lines, all of which live in `Donation` and are read
 * straight from there by `DonateScreen` - so nothing about the payee is
 * restated in this file, and changing the details never means editing it.
 *
 * @param lastUsed  edition to rest on, normally read from EditorSettings.
 * @param dark      current resolved theme; [onToggleTheme] cycles it upstream.
 * @param eyebrow   the small line above the heading. The default is right for
 *                  a fresh launch; a host that is holding a document someone
 *                  has already worked on should say so instead.
 * @param onOpen    open the editor for the chosen edition.
 * @param onSaveQr  write the code's original bytes wherever the platform puts
 *                  downloads. Do not re-encode them - handing someone a lossier
 *                  copy of a payment code helps nobody.
 * @param onCopied  optional: confirm a copied detail line, e.g. via a snackbar.
 */
@Composable
fun HomeScreen(
    lastUsed: Edition,
    dark: Boolean,
    onOpen: (Edition) -> Unit,
    onSaveQr: (ByteArray) -> Unit,
    onToggleTheme: () -> Unit,
    onCopied: (String) -> Unit = {},
    eyebrow: String = "NEW SCREEN",
    modifier: Modifier = Modifier,
    metrics: AdaptiveMetrics = LocalAdaptive.current,
) {
    var previewing by remember { mutableStateOf<Edition?>(null) }
    var showDonate by remember { mutableStateOf(false) }
    val active = previewing ?: lastUsed
    val skin = SkinRegistry.forEdition(active)
    val chrome = if (dark) skin.darkChrome else skin.lightChrome

    val spring = tween<Color>(durationMillis = 280)
    val background by animateColorAsState(chrome.background, spring, label = "homeBackground")
    val panel by animateColorAsState(chrome.panel, spring, label = "homePanel")
    val border by animateColorAsState(chrome.border, spring, label = "homeBorder")
    val text by animateColorAsState(chrome.text, spring, label = "homeText")
    val muted by animateColorAsState(chrome.textMuted, spring, label = "homeMuted")

    // Key events only reach a focused node, so the screen takes focus on
    // arrival. Without this the J / B / T hints along the bottom would be
    // advertising shortcuts that never fire.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                // Down only: an unfiltered handler fires twice per press, and
                // opening the editor twice is a race with the navigation.
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.J -> { onOpen(Edition.JAVA); true }
                    Key.B -> { onOpen(Edition.BEDROCK); true }
                    Key.T -> { onToggleTheme(); true }
                    else -> false
                }
            },
    ) {
        // The pixel night scene the app ships as backdrop-*.png, drawn in code
        // so it can take the active edition's hue instead of loading a bitmap
        // per edition per theme.
        HomeBackdrop(
            skin = skin,
            dark = dark,
            scrim = chrome.backdropScrim,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {

            HomeTopBar(
                panel = panel,
                text = text,
                muted = muted,
                slotFill = skin.paletteFor(dark).slot,
                metrics = metrics,
                onDonate = { showDonate = true },
                onToggleTheme = onToggleTheme,
            )

            // Weighted, not fillMaxSize: a second full-height child under a
            // fixed-height bar asks for more room than the column has and gets
            // its bottom clipped.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .widthIn(max = metrics.readingWidth)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.gutter, vertical = metrics.sectionGap),
                ) {
                    Text(eyebrow, style = label(muted))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Design a screen for",
                        style = TextStyle(
                            color = text,
                            fontSize = if (metrics.isCompact) 26.sp else 34.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(Modifier.height(metrics.sectionGap))

                    val cards = @Composable { mod: Modifier ->
                        Edition.entries.forEach { edition ->
                            EditionColumn(
                                edition = edition,
                                dark = dark,
                                muted = muted,
                                metrics = metrics,
                                onOpen = onOpen,
                                onHoverChanged = { hoveredEdition, isHovered ->
                                    // Order-independent: a card only ever
                                    // clears the preview it set itself. Letting
                                    // either card clear unconditionally means
                                    // the one that recomposes last wipes the
                                    // other's hover and the chrome never moves.
                                    previewing = when {
                                        isHovered -> hoveredEdition
                                        previewing == hoveredEdition -> null
                                        else -> previewing
                                    }
                                },
                                modifier = mod,
                            )
                        }
                    }

                    if (HomeMetrics.sideBySide(metrics)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap)) {
                            cards(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(metrics.sectionGap)) {
                            cards(Modifier.fillMaxWidth())
                        }
                    }

                    // Keyboard hints belong where there is a keyboard.
                    if (!metrics.touchMode) {
                        Spacer(Modifier.height(metrics.sectionGap))
                        Text("J  JAVA      B  BEDROCK      T  THEME", style = label(muted))
                    }
                }
            }
        }

        // Over the top of everything, including the chrome bar. DonateScreen
        // draws its own scrim and handles Esc and outside-clicks itself.
        if (showDonate) {
            DonateScreen(
                onClose = { showDonate = false },
                onSaveQr = onSaveQr,
                onCopied = onCopied,
                metrics = metrics,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A card plus the edition's own tagline, in the shell's voice not the widget's. */
@Composable
private fun EditionColumn(
    edition: Edition,
    dark: Boolean,
    muted: Color,
    metrics: AdaptiveMetrics,
    onOpen: (Edition) -> Unit,
    onHoverChanged: (Edition, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = SkinRegistry.forEdition(edition)

    // One source shared with the card: the card tracks hover and press from
    // it, and this column reads the same signal. Two sources would mean the
    // card lights up while the shell behind it never notices.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // In an effect rather than inline: writing state during composition is
    // what made the original repaint fight itself.
    LaunchedEffect(hovered) { onHoverChanged(edition, hovered) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
        EditionCard(
            skin = skin,
            dark = dark,
            metrics = metrics,
            onClick = { onOpen(edition) },
            interaction = interaction,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(skin.tagline, style = label(muted))
    }
}

/** Top chrome: the wordmark, then support and theme, hard right. */
@Composable
private fun HomeTopBar(
    panel: Color,
    text: Color,
    muted: Color,
    slotFill: Color,
    metrics: AdaptiveMetrics,
    onDonate: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HomeMetrics.chromeHeight(metrics))
            .background(panel)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The mark carries a real slot in it - the logo is a widget.
        Text("MC ", style = label(text))
        Box(
            Modifier
                .size(width = 26.dp, height = 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(slotFill),
            contentAlignment = Alignment.Center,
        ) { Text("GUI", style = label(text).copy(fontSize = 9.sp)) }
        Text(" DESIGNER", style = label(text))
        Spacer(Modifier.width(8.dp))
        Text(HomeMetrics.VERSION, style = label(muted))

        Spacer(Modifier.weight(1f))

        IconSlot(metrics, onDonate) { DonateIcon(size = 22.dp, ink = muted, slot = panel) }
        IconSlot(metrics, onToggleTheme) { ThemeIcon(size = 22.dp, ink = muted) }
    }
}

@Composable
private fun IconSlot(
    metrics: AdaptiveMetrics,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(HomeMetrics.iconTarget(metrics))
            .clip(RoundedCornerShape(metrics.corner * 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun label(color: Color) = TextStyle(
    color = color,
    fontSize = 11.sp,
    fontFamily = FontFamily.Monospace,
    letterSpacing = 1.5.sp,
)
