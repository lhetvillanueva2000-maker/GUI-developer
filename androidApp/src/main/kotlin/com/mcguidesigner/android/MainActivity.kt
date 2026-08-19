package com.mcguidesigner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mcguidesigner.android.ui.AndroidEditor
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.styles.theme.DesignerTheme

/**
 * Single-activity host for the Android editor.
 *
 * The activity itself does almost nothing: it enables edge-to-edge drawing and
 * hands off to Compose.  Configuration changes are handled in the manifest so
 * rotating the device keeps the in-memory document instead of recreating the
 * activity - losing an unsaved design to a rotation would be unforgivable.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appState = remember { AndroidAppState(BuiltInTemplates.demo.instantiate()) }
            val editorState by appState.controller.state.collectAsState()

            DesignerTheme(edition = editorState.edition, touchMode = true) {
                AndroidEditor(
                    app = appState,
                    controller = appState.controller,
                    state = editorState,
                )
            }
        }
    }
}
