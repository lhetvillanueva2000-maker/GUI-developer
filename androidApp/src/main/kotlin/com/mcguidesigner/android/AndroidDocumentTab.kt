package com.mcguidesigner.android

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.GuiProject

/**
 * One open document on Android, and the tree URI it came from.
 *
 * The same shape as the desktop's tab, deliberately: the two shells hold the
 * document differently only where the platform forces them to - a `Uri` from
 * the Storage Access Framework here, a `File` there.
 */
class AndroidDocumentTab(initial: GuiProject) {

    var controller by mutableStateOf(EditorController(initial))

    var uri by mutableStateOf<Uri?>(null)

    var name by mutableStateOf<String?>(null)

    val title: String get() = controller.current.documentTitle

    val edition get() = controller.current.edition

    val dirty: Boolean get() = controller.current.dirty
}
