package com.mcguidesigner.core.editor

import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.validation.ValidationReport

/** Active canvas tool. */
enum class EditorTool(val displayName: String, val shortcut: String) {
    SELECT("Select", "V"),
    PAN("Pan", "H"),
    MARQUEE("Marquee", "M"),
    PLACE("Place", "B"),
    MEASURE("Measure", "R"),
}

/** Whether the canvas is being edited or shown as the running screen. */
enum class ViewMode(val displayName: String) {
    DESIGN("Design"),
    PREVIEW("Preview"),
    CODE("Code"),
}

/** A user-placed alignment guide. */
data class Guide(
    val id: String,
    val vertical: Boolean,
    /** Canvas-space GUI-pixel position. */
    val position: Int,
    val locked: Boolean = false,
)

/**
 * In-flight canvas gesture.  Kept in the state (rather than in UI-local
 * variables) so desktop and Android can drive exactly the same interaction
 * logic from completely different gesture recognisers.
 */
sealed interface Interaction {
    data object None : Interaction

    data class Dragging(
        val elementIds: Set<String>,
        val startBounds: Map<String, IntRect>,
        val grabOffsetX: Int,
        val grabOffsetY: Int,
    ) : Interaction

    data class Resizing(
        val elementId: String,
        val handle: com.mcguidesigner.core.model.ResizeHandle,
        val startBounds: IntRect,
    ) : Interaction

    data class Marquee(val startX: Int, val startY: Int, val currentX: Int, val currentY: Int) : Interaction {
        val rect: IntRect
            get() = IntRect.fromCorners(
                com.mcguidesigner.core.model.IntPoint(startX, startY),
                com.mcguidesigner.core.model.IntPoint(currentX, currentY),
            )
    }
}

/**
 * The complete observable state of one open editor.
 *
 * Immutable: every mutation produces a new instance, which lets Compose skip
 * recomposition precisely and makes the undo system trivial.  Both the desktop
 * and Android front-ends render from this same type.
 */
data class EditorState(
    val project: GuiProject,
    val selection: Set<String> = emptySet(),
    /** Anchor of the selection - the element the inspector focuses on. */
    val primarySelection: String? = null,
    val hoveredId: String? = null,
    val expandedInTree: Set<String> = emptySet(),

    val tool: EditorTool = EditorTool.SELECT,
    val pendingPlacementType: String? = null,
    val interaction: Interaction = Interaction.None,

    val zoom: Float = 3f,
    val panX: Float = 0f,
    val panY: Float = 0f,

    val showGrid: Boolean = true,
    val snapToGrid: Boolean = true,
    val snapToElements: Boolean = true,
    val showRulers: Boolean = true,
    val showGuides: Boolean = true,
    val showSafeArea: Boolean = false,
    val guides: List<Guide> = emptyList(),

    val viewMode: ViewMode = ViewMode.DESIGN,
    val previewState: InteractionState = InteractionState.NORMAL,
    val previewForm: TargetForm = TargetForm.DESKTOP,

    val filePath: String? = null,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String? = null,
    val redoLabel: String? = null,

    val validation: ValidationReport = ValidationReport.empty(),
    val statusMessage: String? = null,

    /**
     * Editor behaviour the user has tuned.
     *
     * Part of the state rather than a platform preference object so the
     * canvas, the controller and both front-ends all read one value - see
     * [EditorSettings].
     */
    val settings: EditorSettings = EditorSettings(),
) {
    val edition get() = project.edition

    val selectedElements
        get() = selection.mapNotNull { project.element(it) }

    val primaryElement
        get() = primarySelection?.let { project.element(it) }

    val hasSelection: Boolean get() = selection.isNotEmpty()

    val documentTitle: String
        get() = buildString {
            append(project.name.ifBlank { "Untitled" })
            if (dirty) append(" *")
        }

    /** Absolute canvas-space bounds for every element, recomputed lazily. */
    val absoluteBounds: Map<String, IntRect> by lazy(LazyThreadSafetyMode.NONE) {
        project.absoluteBounds()
    }

    /** Bounding box of the current selection in canvas space. */
    val selectionBounds: IntRect?
        get() = selection.mapNotNull { absoluteBounds[it] }
            .takeIf { it.isNotEmpty() }
            ?.let { IntRect.bounds(it) }

    fun snapshot(label: String) = HistorySnapshot(project, selection, label)

    companion object {
        fun of(project: GuiProject) = EditorState(
            project = project,
            previewForm = project.canvas.targetForm,
            showSafeArea = project.canvas.targetForm == TargetForm.MOBILE,
        )
    }
}
