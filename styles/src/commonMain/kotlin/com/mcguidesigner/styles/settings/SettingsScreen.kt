@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mcguidesigner.styles.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.Branding
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.theme.ChromeTheme
import com.mcguidesigner.styles.theme.LocalDarkChrome
import com.mcguidesigner.styles.theme.LocalEditionSkin
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.ThemeMode

/**
 * Settings.
 *
 * One scrolling column at every width rather than a two-pane preferences
 * window, because there are four sections and a sidebar to navigate four
 * things is furniture, not navigation. What does change with width is the
 * measure: the column is capped at reading width and centred, so a 1600px
 * desktop window gets a readable page rather than labels at one edge of the
 * screen and controls at the other.
 *
 * Every change is applied and persisted immediately - there is no OK button.
 * These are all reversible display preferences whose effect is visible the
 * instant they are made, and a dialog that made you confirm a theme you can
 * already see would be asking a question it had already answered.
 */
@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onChange: (AppearanceSettings) -> Unit,
    onClose: () -> Unit,
    systemIsDark: Boolean,
    version: String,
    modifier: Modifier = Modifier,
    metrics: AdaptiveMetrics = LocalAdaptive.current,
) {
    val palette = LocalSkinPalette.current
    val skin = LocalEditionSkin.current
    val scroll = rememberScrollState()

    Surface(modifier.fillMaxSize(), color = palette.chromeBackground) {
        Column(Modifier.fillMaxSize()) {

            // -- Top bar ---------------------------------------------------
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
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.chromeText,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = metrics.readingWidth)
                        .fillMaxWidth()
                        .padding(horizontal = metrics.gutter)
                        .padding(top = metrics.sectionGap, bottom = metrics.sectionGap * 2),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {

                    // -- Theme -------------------------------------------------
                    Section(
                        title = "Theme",
                        note = "Light or dark, and which colours the app is built out of. " +
                            "The canvas is never retinted - it has to keep showing what the " +
                            "game will actually draw.",
                        palette = palette,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(metrics.gap * 0.7f),
                            verticalArrangement = Arrangement.spacedBy(metrics.gap * 0.7f),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                Chip(
                                    label = mode.displayName,
                                    selected = settings.theme == mode,
                                    palette = palette,
                                    metrics = metrics,
                                    onClick = { onChange(settings.copy(theme = mode)) },
                                )
                            }
                        }

                        Spacer(Modifier.height(metrics.gap))

                        val dark = settings.theme.isDark(systemIsDark)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(metrics.gap * 0.7f),
                            verticalArrangement = Arrangement.spacedBy(metrics.gap * 0.7f),
                        ) {
                            ChromeTheme.entries.forEach { theme ->
                                ThemeSwatch(
                                    theme = theme,
                                    // Previewed against the edition you are
                                    // actually in and the light/dark setting
                                    // you have actually chosen, so the swatch
                                    // is the result rather than an impression
                                    // of it.
                                    preview = theme.apply(
                                        if (dark) skin.darkChrome else skin.lightChrome,
                                        dark,
                                    ),
                                    selected = settings.chromeTheme == theme,
                                    accent = palette.accent,
                                    textColor = palette.chromeText,
                                    mutedColor = palette.chromeTextMuted,
                                    onClick = { onChange(settings.copy(chromeTheme = theme)) },
                                )
                            }
                        }

                        Spacer(Modifier.height(metrics.gap * 0.6f))
                        Text(
                            settings.chromeTheme.blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.chromeTextMuted,
                        )
                    }

                    // -- Motion ------------------------------------------------
                    Section(
                        title = "Motion",
                        note = "Turning motion down is the single biggest thing you can do for " +
                            "battery and for an older device: the backdrop and the star field " +
                            "redraw every frame while they are moving, and stopping them stops " +
                            "that work entirely.",
                        palette = palette,
                    ) {
                        MotionLevel.entries.forEach { level ->
                            OptionRow(
                                title = level.displayName,
                                body = level.blurb,
                                selected = settings.motion == level,
                                palette = palette,
                                metrics = metrics,
                                onClick = { onChange(settings.copy(motion = level)) },
                            )
                            Spacer(Modifier.height(metrics.gap * 0.5f))
                        }

                        Spacer(Modifier.height(metrics.gap * 0.4f))
                        ToggleRow(
                            title = "Backdrop artwork",
                            body = if (settings.backdropEnabled && !settings.motion.allowsLoops) {
                                "Drawn, but held still by the motion setting above."
                            } else {
                                "The pixel scene behind the editor and the home screen."
                            },
                            checked = settings.backdropEnabled,
                            palette = palette,
                            metrics = metrics,
                            onCheckedChange = { onChange(settings.copy(backdropEnabled = it)) },
                        )
                    }

                    // -- Profile -----------------------------------------------
                    Section(
                        title = "You",
                        note = "Stored on this device, in the same preferences file as everything " +
                            "else on this screen. It is not sent anywhere, because there is " +
                            "nowhere to send it.",
                        palette = palette,
                    ) {
                        OutlinedTextField(
                            value = settings.profileName,
                            onValueChange = { onChange(settings.copy(profileName = it.take(40))) },
                            label = { Text("Name") },
                            placeholder = { Text("What home should call you") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(metrics.gap))
                        Text(
                            "Signing in with Google, an email address or a phone number is not " +
                                "built yet, and this screen does not pretend otherwise. Doing it " +
                                "honestly needs two things ${Branding.NAME} does not have: an " +
                                "OAuth client registered with each provider, and a server to " +
                                "hold the session and whatever the account is meant to sync. " +
                                "Until both exist, everything here stays on your device - which " +
                                "also means no account, no tracking and nothing to leak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.chromeTextMuted,
                        )
                    }

                    // -- About -------------------------------------------------
                    Section(title = "About", note = null, palette = palette) {
                        Text(
                            "${Branding.NAME} $version",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.chromeText,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Branding.TAGLINE,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.chromeTextMuted,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun Section(
    title: String,
    note: String?,
    palette: com.mcguidesigner.styles.theme.SkinPalette,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )
        Spacer(Modifier.height(10.dp))
        content()
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = palette.chromeTextMuted)
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    palette: com.mcguidesigner.styles.theme.SkinPalette,
    metrics: AdaptiveMetrics,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(metrics.corner * 0.5f))
            .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent else palette.chromeBorder,
                shape = RoundedCornerShape(metrics.corner * 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.textOnAccent else palette.chromeText,
        )
    }
}

/**
 * One chrome theme, shown as the colours it actually produces.
 *
 * The three bands are the three surfaces that carry the whole look - the page,
 * a panel on it, and the accent - so the swatch answers "what will this look
 * like" without the reader having to apply it and undo it.
 */
@Composable
private fun ThemeSwatch(
    theme: ChromeTheme,
    preview: com.mcguidesigner.styles.theme.ChromeColors,
    selected: Boolean,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(preview.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else preview.border,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.86f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview.panel),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview.panelAlt),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) textColor else mutedColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    body: String,
    selected: Boolean,
    palette: com.mcguidesigner.styles.theme.SkinPalette,
    metrics: AdaptiveMetrics,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.corner * 0.5f))
            .background(if (selected) palette.chromePanelAlt else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent else palette.chromeBorder,
                shape = RoundedCornerShape(metrics.corner * 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A filled dot rather than a Material RadioButton so the row is one
        // hit target instead of two that disagree about what was pressed.
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) palette.accent else Color.Transparent)
                .border(1.5.dp, if (selected) palette.accent else palette.chromeBorder, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)
            Text(body, style = MaterialTheme.typography.bodySmall, color = palette.chromeTextMuted)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    palette: com.mcguidesigner.styles.theme.SkinPalette,
    metrics: AdaptiveMetrics,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.corner * 0.5f))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)
            Text(body, style = MaterialTheme.typography.bodySmall, color = palette.chromeTextMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.textOnAccent,
                checkedTrackColor = palette.accent,
                uncheckedTrackColor = palette.chromePanelAlt,
                uncheckedBorderColor = palette.chromeBorder,
            ),
        )
    }
}
