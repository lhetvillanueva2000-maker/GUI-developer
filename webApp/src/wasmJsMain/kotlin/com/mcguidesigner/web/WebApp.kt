package com.mcguidesigner.web

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.styles.home.HomeScreen
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.DeviceClass
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.paint.PaintScreen
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.support.DonationQr
import com.mcguidesigner.styles.support.LocalDonationQr
import com.mcguidesigner.styles.theme.DesignerTheme
import com.mcguidesigner.styles.theme.LocalBackdropMotion
import com.mcguidesigner.web.io.BrowserFiles

/**
 * The browser shell: home, the editor, or the paint canvas.
 *
 * Peers rather than a stack, exactly as on Android - home is not a sheet the
 * editor opens, it is the screen the editor was launched from, and the
 * document lives in the controller either way so moving between them costs
 * nothing and loses nothing.
 */
@Composable
fun WebApp(app: WebAppState) {

    val state by app.controller.state.collectAsState()

    // No artwork is supplied, so `DesignerBackdrop` draws its own scene. The
    // four wallpaper PNGs are about a megabyte between them, which is a
    // megabyte added to the first paint of a page for something the procedural
    // backdrop already does convincingly.
    val donationQr = remember { DonationQr.from(null) }

    // A tab is closed far more casually than an app is, and there is no file
    // on disk to come back to. Written on every change to the document rather
    // than on the way out, because a page has no reliable "on the way out".
    LaunchedEffect(state.project) { app.persistSession() }

    DesignerTheme(
        edition = state.edition,
        touchMode = false,
        dark = app.themeMode.isDark(),
        chromeTheme = app.appearance.chromeTheme,
        motion = app.appearance.motion,
    ) {
        CompositionLocalProvider(
            LocalBackdropMotion provides app.appearance.backdropMoves,
            LocalDonationQr provides donationQr,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val metrics = AdaptiveMetrics.of(
                    widthDp = maxWidth,
                    heightDp = maxHeight,
                    // A browser window is resized freely, so unlike a phone
                    // there is nothing else to go on: the window really is the
                    // device here.
                    touchMode = false,
                    device = DeviceClass.ofSmallestWidth(minOf(maxWidth, maxHeight).value.toInt()),
                )

                CompositionLocalProvider(LocalAdaptive provides metrics) {
                    Crossfade(targetState = app.screen, label = "screen") { screen ->
                        when (screen) {
                            WebScreen.HOME -> HomeScreen(
                                lastUsed = state.edition,
                                dark = app.themeMode.isDark(),
                                settings = app.appearance,
                                onSettingsChange = app::applyAppearance,
                                overlay = app.homeOverlay,
                                onOverlayChange = { app.homeOverlay = it },
                                onOpen = app::openEditor,
                                onOpenLink = BrowserFiles::openLink,
                                onSaveQr = { bytes ->
                                    BrowserFiles.save(Donation.QR_FILE_NAME, bytes, "image/png")
                                },
                                onToggleTheme = app::cycleTheme,
                                systemIsDark = true,
                                onCopied = { app.status = it },
                                modifier = Modifier.fillMaxSize(),
                            )

                            WebScreen.EDITOR -> WebEditor(
                                app = app,
                                controller = app.controller,
                                state = state,
                                textures = remember(app.activeTab) { TextureCache() },
                                modifier = Modifier.fillMaxSize(),
                            )

                            WebScreen.PAINT -> {
                                val paint = app.paint
                                if (paint == null) {
                                    app.goHome()
                                } else {
                                    PaintScreen(
                                        state = paint,
                                        onBack = app::goHome,
                                        onExport = app::savePaintPng,
                                        onImportImage = app::importPaintImage,
                                        motion = app.appearance.motion,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
