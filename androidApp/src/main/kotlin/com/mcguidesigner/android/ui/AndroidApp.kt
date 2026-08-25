package com.mcguidesigner.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.AppScreen
import com.mcguidesigner.android.io.AndroidFileIO
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.styles.home.HomeOverlay
import com.mcguidesigner.styles.home.HomeScreen
import com.mcguidesigner.styles.paint.PaintPopover
import com.mcguidesigner.styles.paint.PaintScreen
import com.mcguidesigner.styles.paint.PaintSheet
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.DeviceClass
import com.mcguidesigner.styles.layout.LocalAdaptive

/**
 * The Android navigation host: home, or the editor.
 *
 * It sits above [AndroidEditor] rather than inside it because the two screens
 * are peers - home is not a sheet the editor opens, it is the screen the
 * editor was launched from. The document lives in the controller either way,
 * so moving between them costs nothing and loses nothing.
 *
 * The QR save launcher lives here for the same reason the screen switch does:
 * home is what shows the support page now, and an activity-result launcher has
 * to be registered by a composable that is on screen when the result comes
 * back.
 */
@Composable
fun AndroidApp(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    /** Resolved by the activity, which is the only thing that can ask the OS. */
    dark: Boolean,
    /** What the OS reports for its own light/dark setting. */
    systemIsDark: Boolean,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // The shorter edge of the *screen*, which does not change when the device
    // is rotated - so a phone is still a phone on its side. Reading the window
    // width instead is what used to hand a landscape phone the tablet layout:
    // around 900dp wide, a navigation rail and a docked inspector, on a screen
    // four inches tall.
    val device = DeviceClass.ofSmallestWidth(LocalConfiguration.current.smallestScreenWidthDp)

    // Saving the donation QR goes through the same picker as everything else,
    // which is why the app still needs no storage permission to do it.
    val qrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.PNG_MIME),
    ) { uri ->
        if (uri != null) app.saveQrCode(context, uri) else app.pendingQrBytes = null
    }

    val paintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.PNG_MIME),
    ) { uri ->
        if (uri != null) app.savePaintPng(context, uri) else app.pendingPaintPng = null
    }

    // Reading one image needs no storage permission either: the picker hands
    // back a URI the app may read, and nothing more.
    val paintImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) app.importPaintImage(context, uri)
    }

    // Innermost first. A page open over home is what back should close, and
    // only once there is nothing left over home does back leave the app. Each
    // handler is enabled for exactly one state, so no two can claim a press.
    BackHandler(enabled = app.screen == AppScreen.HOME && app.homeOverlay != HomeOverlay.NONE) {
        app.homeOverlay = HomeOverlay.NONE
    }
    BackHandler(enabled = app.screen == AppScreen.HOME && app.homeOverlay == HomeOverlay.NONE) {
        app.guardUnsaved("leave the app") { activity?.finish() }
    }
    BackHandler(enabled = app.screen == AppScreen.PAINT) {
        val paint = app.paint
        when {
            paint == null -> app.goHome()
            paint.sheet != PaintSheet.NONE -> paint.sheet = PaintSheet.NONE
            paint.popover != PaintPopover.NONE -> paint.popover = PaintPopover.NONE
            else -> app.goHome()
        }
    }

    Crossfade(targetState = app.screen, label = "screen") { screen ->
        when (screen) {
            AppScreen.HOME -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val metrics = AdaptiveMetrics.of(
                    widthDp = maxWidth,
                    heightDp = maxHeight,
                    touchMode = true,
                    device = device,
                )
                CompositionLocalProvider(LocalAdaptive provides metrics) {
                    HomeScreen(
                        lastUsed = state.edition,
                        dark = dark,
                        settings = app.appearance,
                        onSettingsChange = { app.applyAppearance(context, it) },
                        overlay = app.homeOverlay,
                        onOverlayChange = { app.homeOverlay = it },
                        systemIsDark = systemIsDark,
                        eyebrow = app.homeEyebrow,
                        onOpen = app::openEditor,
                        onOpenLink = { app.openLink(context, it) },
                        onSaveQr = { bytes ->
                            app.pendingQrBytes = bytes
                            qrLauncher.launch(Donation.QR_FILE_NAME)
                        },
                        onToggleTheme = { app.cycleTheme(context) },
                        onCopied = { app.status = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }

            AppScreen.EDITOR -> AndroidEditor(app, controller, state, device)

            AppScreen.PAINT -> {
                val paint = app.paint
                if (paint == null) {
                    app.goHome()
                } else {
                    PaintScreen(
                        state = paint,
                        onBack = { app.goHome() },
                        onExport = { bytes, name ->
                            // Destination first, bytes second - the same order
                            // the rest of the app now uses, because the picker
                            // creates the file the moment a location is chosen
                            // and anything held across that can be reclaimed.
                            app.pendingPaintPng = bytes
                            paintLauncher.launch(name)
                        },
                        onImportImage = { paintImportLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }

}
