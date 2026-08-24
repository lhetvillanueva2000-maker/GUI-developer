@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mcguidesigner.styles.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.settings.AppearanceSettings
import com.mcguidesigner.styles.settings.SettingsIcon
import com.mcguidesigner.styles.settings.SettingsScreen
import com.mcguidesigner.styles.support.DonateIcon
import com.mcguidesigner.styles.support.DonateScreen
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.spec

/**
 * Which full-screen page is open over home, if any.
 *
 * Hoisted to the shell rather than kept inside [HomeScreen] because Android's
 * back gesture has to be able to close whichever one is showing, and only the
 * shell can register a `BackHandler`. Keeping the state here and the handler
 * there would mean back either dismissed nothing or left the app entirely.
 */
enum class HomeOverlay { NONE, SUPPORT, SETTINGS }

/**
 * The home screen: pick an edition, read what this is, or open settings.
 *
 * Home has no edition of its own - it comes before that choice - so instead of
 * inventing a third neutral chrome it wears the chrome of whichever edition the
 * pointer or focus is currently over, falling back to [lastUsed]. The whole
 * shell repaints as you move between the two cards, so the app changes identity
 * before you commit to anything.
 *
 * Below the cards is the explainer: three short answers to the three questions
 * somebody who has just installed this actually has. It is at the bottom rather
 * than the top because the people who need it read down to it once, and the
 * people who do not need it should not have to read past it every launch.
 *
 * @param lastUsed  edition to rest on, normally read from EditorSettings.
 * @param dark      the resolved light/dark state; [settings] holds the choice
 *                  that produced it, which is not the same thing when it is
 *                  set to follow the system.
 * @param eyebrow   the small line above the heading. The default is right for
 *                  a fresh launch; a host that is holding a document someone
 *                  has already worked on should say so instead.
 * @param onOpen    open the editor for the chosen edition.
 * @param onSaveQr  write the QR's original bytes wherever the platform puts
 *                  downloads. Do not re-encode them - handing someone a lossier
 *                  copy of a payment code helps nobody.
 */
@Composable
fun HomeScreen(
    lastUsed: Edition,
    dark: Boolean,
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    overlay: HomeOverlay,
    onOverlayChange: (HomeOverlay) -> Unit,
    onOpen: (Edition) -> Unit,
    onOpenLink: (String) -> Unit,
    onSaveQr: (ByteArray) -> Unit,
    onToggleTheme: () -> Unit,
    systemIsDark: Boolean,
    onCopied: (String) -> Unit = {},
    eyebrow: String = "NEW SCREEN",
    version: String = HomeMetrics.VERSION,
    modifier: Modifier = Modifier,
    metrics: AdaptiveMetrics = LocalAdaptive.current,
) {
    var previewing by remember { mutableStateOf<Edition?>(null) }
    val active = previewing ?: lastUsed
    val skin = SkinRegistry.forEdition(active)
    val chrome = settings.chromeTheme.apply(if (dark) skin.darkChrome else skin.lightChrome, dark)

    val spec = settings.motion.spec<Color>(280)
    val background by animateColorAsState(chrome.background, spec, label = "homeBackground")
    val panel by animateColorAsState(chrome.panel, spec, label = "homePanel")
    val text by animateColorAsState(chrome.text, spec, label = "homeText")
    val muted by animateColorAsState(chrome.textMuted, spec, label = "homeMuted")
    // From the edition's palette rather than the chrome: the accent is the one
    // colour a theme is not allowed to move, so it stays the edition's own.
    val accent = skin.paletteFor(dark, settings.chromeTheme).accent

    // Key events only reach a focused node, so the screen takes focus on
    // arrival. Without this the shortcut hints along the bottom would be
    // advertising keys that never fire.
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

                // A page is over the top of home; its own controls own the
                // keyboard now. Without this, pressing J while reading the
                // settings page would open the Java editor underneath it.
                if (overlay != HomeOverlay.NONE) {
                    return@onKeyEvent if (event.key == Key.Escape) {
                        onOverlayChange(HomeOverlay.NONE); true
                    } else {
                        false
                    }
                }

                when (event.key) {
                    Key.J -> { onOpen(Edition.JAVA); true }
                    Key.B -> { onOpen(Edition.BEDROCK); true }
                    Key.T -> { onToggleTheme(); true }
                    Key.S -> { onOverlayChange(HomeOverlay.SETTINGS); true }
                    else -> false
                }
            },
    ) {
        // The pixel night scene, drawn in code so it can take the active
        // edition's hue instead of loading a bitmap per edition per theme.
        if (settings.backdropEnabled) {
            HomeBackdrop(
                skin = skin,
                dark = dark,
                scrim = chrome.backdropScrim,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(Modifier.fillMaxSize()) {

            HomeTopBar(
                panel = panel,
                text = text,
                muted = muted,
                slotFill = skin.paletteFor(dark, settings.chromeTheme).slot,
                version = version,
                metrics = metrics,
                onDonate = { onOverlayChange(HomeOverlay.SUPPORT) },
                onSettings = { onOverlayChange(HomeOverlay.SETTINGS) },
            )

            // Scrolling, because the explainer at the bottom has to be
            // reachable on a phone in landscape - about 340dp of usable height
            // - where the cards alone already fill the screen.
            //
            // The cards *and* the explainer are centred together inside at
            // least one screenful, rather than the cards being given the whole
            // screenful and the explainer starting after it. The second shape
            // was the first thing tried and it is wrong on a desktop: a 1000px
            // window showed the cards floating in the middle with the
            // explainer entirely below the fold, so the text existed but
            // nobody would ever scroll a half-empty screen to find it.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val viewportHeight = maxHeight
                val scroll = rememberScrollState()

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .heightIn(min = viewportHeight)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = metrics.readingWidth)
                                .fillMaxWidth()
                                .padding(horizontal = metrics.gutter, vertical = metrics.sectionGap),
                        ) {
                            // The mark, then what you are here to do. One
                            // line rather than two stacked mono labels: the
                            // top bar already carries the wordmark, so a
                            // second full-height one directly under it would
                            // read as a repeat rather than a heading.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Branding.NAME.uppercase(),
                                    style = label(muted).copy(
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text("  ·  ", style = label(muted))
                                Text(
                                    settings.greeting?.let { "WELCOME BACK, ${it.uppercase()}" } ?: eyebrow,
                                    style = label(muted),
                                )
                            }
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
                                            // Order-independent: a card only
                                            // ever clears the preview it set
                                            // itself. Letting either card clear
                                            // unconditionally means the one
                                            // that recomposes last wipes the
                                            // other's hover and the chrome
                                            // never moves.
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

                            Spacer(Modifier.height(metrics.sectionGap))
                            PublicLinkRow(
                                links = Branding.publicLinks,
                                panel = panel,
                                border = chrome.border,
                                text = text,
                                muted = muted,
                                accent = accent,
                                metrics = metrics,
                                onOpenLink = onOpenLink,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Keyboard hints belong where there is a keyboard.
                            if (!metrics.touchMode) {
                                Spacer(Modifier.height(metrics.sectionGap))
                                Text(
                                    "J  JAVA      B  BEDROCK      T  THEME      S  SETTINGS",
                                    style = label(muted),
                                )
                            }
                        }

                        Explainer(
                            text = text,
                            muted = muted,
                            border = chrome.border,
                            metrics = metrics,
                        )
                    }
                }
            }
        }

        // Over the top of everything, including the chrome bar. Both pages are
        // opaque and own their own dismissal.
        when (overlay) {
            HomeOverlay.SUPPORT -> DonateScreen(
                onClose = { onOverlayChange(HomeOverlay.NONE) },
                onSaveQr = onSaveQr,
                onCopied = onCopied,
                metrics = metrics,
                modifier = Modifier.fillMaxSize(),
            )

            HomeOverlay.SETTINGS -> SettingsScreen(
                settings = settings,
                onChange = onSettingsChange,
                onClose = { onOverlayChange(HomeOverlay.NONE) },
                systemIsDark = systemIsDark,
                version = version,
                metrics = metrics,
                modifier = Modifier.fillMaxSize(),
            )

            HomeOverlay.NONE -> Unit
        }
    }
}

/**
 * What this is, who it is for, and how to use it.
 *
 * The three answers come from [Branding.explainer] rather than being written
 * here, because the store listing and the README need the same three and three
 * copies of a paragraph is three chances to update two of them.
 *
 * Laid out as a flow rather than a fixed row so the columns wrap on their own
 * at whatever width they stop fitting, instead of at a breakpoint chosen by
 * guessing how long the text would end up being.
 */
@Composable
private fun Explainer(
    text: Color,
    muted: Color,
    border: Color,
    metrics: AdaptiveMetrics,
) {
    Column(
        Modifier
            .widthIn(max = metrics.readingWidth * 1.9f)
            .fillMaxWidth()
            .padding(horizontal = metrics.gutter)
            .padding(bottom = metrics.sectionGap * 1.5f),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
        Spacer(Modifier.height(metrics.sectionGap))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            Branding.explainer.forEach { point ->
                Column(Modifier.widthIn(min = 240.dp, max = 340.dp)) {
                    Text(point.heading.uppercase(), style = label(muted))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        point.body,
                        style = TextStyle(
                            color = text,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        ),
                    )
                }
            }
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
    // No chrome theme here on purpose: the card is a *widget*, and no theme is
    // allowed to touch widget colours. That is the same rule that keeps the
    // canvas honest, and the card exists precisely to preview the canvas.
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

/** Top chrome: the wordmark, then support and settings, hard right. */
@Composable
private fun HomeTopBar(
    panel: Color,
    text: Color,
    muted: Color,
    slotFill: Color,
    version: String,
    metrics: AdaptiveMetrics,
    onDonate: () -> Unit,
    onSettings: () -> Unit,
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
        Text("${Branding.WORDMARK} ", style = label(text))
        Box(
            Modifier
                .size(width = 44.dp, height = 18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(slotFill),
            contentAlignment = Alignment.Center,
        ) { Text(Branding.WORDMARK_SLOT, style = label(text).copy(fontSize = 9.sp)) }
        Spacer(Modifier.width(8.dp))
        Text(version, style = label(muted))

        Spacer(Modifier.weight(1f))

        IconSlot(metrics, onDonate) { DonateIcon(size = 22.dp, ink = muted, slot = panel) }
        IconSlot(metrics, onSettings) { SettingsIcon(size = 22.dp, ink = muted) }
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
