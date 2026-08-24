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
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.AppScreen
import com.mcguidesigner.android.io.AndroidFileIO
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.styles.home.HomeScreen
import com.mcguidesigner.styles.layout.AdaptiveMetrics
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
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Saving the donation QR goes through the same picker as everything else,
    // which is why the app still needs no storage permission to do it.
    val qrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.PNG_MIME),
    ) { uri ->
        if (uri != null) app.saveQrCode(context, uri) else app.pendingQrBytes = null
    }

    // Home is the first screen, so back from it leaves the app. The editor
    // owns its own handler; this one is only registered while home is up, so
    // the two can never both claim a press.
    BackHandler(enabled = app.screen == AppScreen.HOME) {
        app.guardUnsaved("leave the app") { activity?.finish() }
    }

    Crossfade(targetState = app.screen, label = "screen") { screen ->
        when (screen) {
            AppScreen.HOME -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val metrics = AdaptiveMetrics.of(maxWidth, maxHeight, touchMode = true)
                CompositionLocalProvider(LocalAdaptive provides metrics) {
                    HomeScreen(
                        lastUsed = state.edition,
                        dark = dark,
                        eyebrow = app.homeEyebrow,
                        onOpen = app::openEditor,
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

            AppScreen.EDITOR -> AndroidEditor(app, controller, state)
        }
    }

}
