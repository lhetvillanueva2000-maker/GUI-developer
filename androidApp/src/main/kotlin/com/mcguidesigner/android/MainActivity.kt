package com.mcguidesigner.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import com.mcguidesigner.android.io.AndroidPreferences
import com.mcguidesigner.android.io.SessionStore
import com.mcguidesigner.android.ui.AndroidEditor
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.styles.render.decodeImageBitmap
import com.mcguidesigner.styles.theme.BackdropArtwork
import com.mcguidesigner.styles.theme.DesignerBackdrop
import com.mcguidesigner.styles.theme.DesignerTheme
import com.mcguidesigner.styles.theme.LocalBackdropArtwork
import com.mcguidesigner.styles.theme.LocalBackdropMotion

/**
 * Single-activity host for the Android editor.
 *
 * The activity itself does almost nothing: it enables edge-to-edge drawing,
 * restores the previous session and hands off to Compose.  Configuration
 * changes are handled in the manifest so rotating the device keeps the
 * in-memory document instead of recreating the activity - losing an unsaved
 * design to a rotation would be unforgivable.
 */
class MainActivity : ComponentActivity() {

    private lateinit var appState: AndroidAppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A session written by a previous run wins over the demo document.
        // Android kills backgrounded processes freely, and coming back to an
        // empty canvas is the single most damning thing a mobile editor can do.
        val restored = SessionStore.restore(this)
        appState = AndroidAppState(restored?.project ?: BuiltInTemplates.demo.instantiate())
        restored?.let(appState::restoreSession)
        appState.applySettings(AndroidPreferences.load(this))
        appState.loadLibraries(this)

        val artwork = AssetBackdrops(this)

        setContent {
            val state by appState.controller.state.collectAsState()

            // The system's own light/dark setting, which is what
            // ThemeMode.SYSTEM defers to.
            val configuration = LocalConfiguration.current
            val systemIsDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            DesignerTheme(
                edition = state.edition,
                touchMode = true,
                dark = appState.themeMode.isDark(systemIsDark),
            ) {
                CompositionLocalProvider(
                    LocalBackdropArtwork provides artwork,
                    LocalBackdropMotion provides appState.backdropMotion,
                ) {
                    AndroidEditor(
                        app = appState,
                        controller = appState.controller,
                        state = state,
                    )
                }
            }
        }
    }

    /**
     * `onStop` rather than `onPause`: it still runs before the process can be
     * killed, but it does not fire for a transient overlay such as a
     * permission dialog, so the snapshot is not rewritten on every tap.
     */
    override fun onStop() {
        super.onStop()
        if (::appState.isInitialized) appState.persistSession(this)
    }
}

/**
 * The wallpaper artwork, read out of the APK's assets on first use.
 *
 * Decoding is deferred and cached because it costs a few megabytes of bitmap
 * per image and most sessions only ever see one of the four.  A missing or
 * unreadable asset returns null, and [DesignerBackdrop] draws its own scene
 * instead - so a build that somehow shipped without the art still looks
 * deliberate.
 */
private class AssetBackdrops(context: android.content.Context) : BackdropArtwork {

    private val assets = context.applicationContext.assets
    private val cache = mutableMapOf<String, ImageBitmap?>()

    override fun imageFor(edition: Edition, dark: Boolean): ImageBitmap? {
        val name = "backdrop-${edition.slug}-${if (dark) "dark" else "light"}.png"
        return cache.getOrPut(name) {
            runCatching { assets.open(name).use { decodeImageBitmap(it.readBytes()) } }.getOrNull()
        }
    }
}
