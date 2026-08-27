package com.mcguidesigner.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.web.io.WebPreferences
import kotlinx.browser.document

/**
 * The browser entry point.
 *
 * Everything below this is the same code the desktop and Android builds run;
 * this function is the whole of what is different about starting up in a page.
 * It restores the last session if there is one, hands the app a canvas, and
 * takes the loading screen down.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // A session written by a previous visit wins over the demo document. A tab
    // is refreshed and reopened constantly, and coming back to an empty canvas
    // after losing an afternoon's work is the single most damning thing this
    // could do - and unlike an app, there is no file on disk to fall back to.
    val restored = WebPreferences.restoreSession()
        ?.let { ProjectSerializer.decode(it) }
        ?.let { (it as? LoadResult.Success)?.project }

    val app = WebAppState(restored ?: BuiltInTemplates.demo.instantiate())

    ComposeViewport(document.body!!) {
        // Taken down after the first frame has actually been produced, rather
        // than as soon as this function returns - `ComposeViewport` sets the
        // composition up and comes straight back, so removing it here would
        // leave a blank page for however long the first layout takes. The
        // wasm module is several megabytes; the gap the loading screen covers
        // is seconds on a slow connection, not milliseconds, and the last of
        // those seconds is this one.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            document.getElementById("boot")?.remove()
        }
        WebApp(app)
    }
}
