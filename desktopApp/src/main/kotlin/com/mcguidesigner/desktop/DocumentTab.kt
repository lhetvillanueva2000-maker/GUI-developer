package com.mcguidesigner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.GuiProject
import java.io.File

/**
 * One open document, and the file it came from.
 *
 * A class rather than a data class on purpose: two tabs holding equal projects
 * are still two different tabs, and `equals` by content would let a list
 * operation quietly collapse them into one.
 */
class DocumentTab(initial: GuiProject) {

    var controller by mutableStateOf(EditorController(initial))

    /** Where this document was opened from, or null if it has never been saved. */
    var file by mutableStateOf<File?>(null)

    /** What the tab strip shows. Follows a rename without any bookkeeping. */
    val title: String get() = controller.current.documentTitle

    val edition get() = controller.current.edition

    val dirty: Boolean get() = controller.current.dirty
}
