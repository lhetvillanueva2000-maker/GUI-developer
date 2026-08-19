package com.mcguidesigner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mcguidesigner.android.io.SessionStore
import com.mcguidesigner.android.ui.AndroidEditor
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.styles.theme.DesignerTheme

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

        setContent {
            val state by appState.controller.state.collectAsState()

            DesignerTheme(edition = state.edition, touchMode = true) {
                AndroidEditor(
                    app = appState,
                    controller = appState.controller,
                    state = state,
                )
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
