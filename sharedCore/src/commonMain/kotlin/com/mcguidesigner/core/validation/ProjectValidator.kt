package com.mcguidesigner.core.validation

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.PropType
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.absoluteBoundsMap
import com.mcguidesigner.core.model.walkAll

enum class Severity { ERROR, WARNING, INFO }

/** Stable machine-readable identifiers, also used by the CI validation task. */
object IssueCode {
    const val DUPLICATE_ID = "duplicate-id"
    const val UNKNOWN_TYPE = "unknown-type"
    const val UNSUPPORTED_ELEMENT = "unsupported-element"
    const val UNSUPPORTED_PROPERTY = "unsupported-property"
    const val PROPERTY_TYPE_MISMATCH = "property-type-mismatch"
    const val PROPERTY_OUT_OF_RANGE = "property-out-of-range"
    const val UNKNOWN_PROPERTY = "unknown-property"
    const val MISSING_TEXTURE = "missing-texture"
    const val SIZE_TOO_SMALL = "size-too-small"
    const val SIZE_TOO_LARGE = "size-too-large"
    const val OUT_OF_CANVAS = "out-of-canvas"
    const val OUTSIDE_SAFE_AREA = "outside-safe-area"
    const val TOUCH_TARGET_TOO_SMALL = "touch-target-too-small"
    const val NESTED_IN_NON_CONTAINER = "nested-in-non-container"
    const val OVERLAPPING_SLOTS = "overlapping-slots"
    const val EMPTY_CANVAS = "empty-canvas"
    const val INVALID_NAMESPACE = "invalid-namespace"
    const val UNUSED_TEXTURE = "unused-texture"
    const val EDITION_PARITY = "edition-parity"
    const val JAVA_BUTTON_HEIGHT = "java-button-height"
    const val ANIMATION_NOT_ANIMATED = "animation-not-animated"
    const val ANIMATION_TIMING_FLATTENED = "animation-timing-flattened"
}

data class ValidationIssue(
    val severity: Severity,
    val code: String,
    val message: String,
    val elementId: String? = null,
    val elementName: String? = null,
    val propertyKey: String? = null,
    /** Short, actionable hint shown under the message in the issues panel. */
    val fixHint: String? = null,
)

data class ValidationReport(val issues: List<ValidationIssue>) {
    val errors get() = issues.filter { it.severity == Severity.ERROR }
    val warnings get() = issues.filter { it.severity == Severity.WARNING }
    val infos get() = issues.filter { it.severity == Severity.INFO }

    val errorCount get() = errors.size
    val warningCount get() = warnings.size
    val isClean get() = errors.isEmpty()

    fun forElement(id: String) = issues.filter { it.elementId == id }

    fun highestSeverityFor(id: String): Severity? =
        forElement(id).minByOrNull { it.severity.ordinal }?.severity

    companion object {
        fun empty() = ValidationReport(emptyList())
    }
}

/**
 * Static analysis of a project.
 *
 * Runs after every document edit (it is linear in element count and cheap for
 * screen-sized documents) and again, in strict mode, before every export.  The
 * same code backs the `validateProjects` Gradle task so a broken template can
 * never be committed.
 */
object ProjectValidator {

    private val namespacePattern = Regex("^[a-z0-9_.-]+$")

    fun validate(project: GuiProject, strict: Boolean = false): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        val absolute = project.elements.absoluteBoundsMap(project.canvas.size)
        val seenIds = mutableSetOf<String>()
        val referencedTextures = mutableSetOf<String>()

        if (project.elements.isEmpty()) {
            issues += ValidationIssue(
                Severity.INFO, IssueCode.EMPTY_CANVAS,
                "This screen has no elements yet.",
                fixHint = "Drag a component from the palette onto the canvas.",
            )
        }

        if (!namespacePattern.matches(project.meta.namespace)) {
            issues += ValidationIssue(
                Severity.ERROR, IssueCode.INVALID_NAMESPACE,
                "Namespace '${project.meta.namespace}' is not a valid resource namespace.",
                fixHint = "Use only lowercase letters, digits, '_', '-' and '.'.",
            )
        }

        validateTree(
            nodes = project.elements,
            parent = null,
            project = project,
            absolute = absolute,
            seenIds = seenIds,
            referencedTextures = referencedTextures,
            issues = issues,
            strict = strict,
            clipped = false,
        )

        // Textures that no element points at: harmless, but they bloat exports.
        project.textures.filter { it.id !in referencedTextures }.forEach { texture ->
            issues += ValidationIssue(
                Severity.INFO, IssueCode.UNUSED_TEXTURE,
                "Texture '${texture.name}' is imported but never used.",
                fixHint = "Assign it to an element or remove it to shrink the export.",
            )
        }

        issues += parityIssues(project)

        return ValidationReport(issues.sortedBy { it.severity.ordinal })
    }

    private fun validateTree(
        nodes: List<GuiElement>,
        parent: GuiElement?,
        project: GuiProject,
        absolute: Map<String, IntRect>,
        seenIds: MutableSet<String>,
        referencedTextures: MutableSet<String>,
        issues: MutableList<ValidationIssue>,
        strict: Boolean,
        clipped: Boolean,
    ) {
        val slotRects = mutableListOf<Pair<GuiElement, IntRect>>()

        for (element in nodes) {
            if (!seenIds.add(element.id)) {
                issues += ValidationIssue(
                    Severity.ERROR, IssueCode.DUPLICATE_ID,
                    "Duplicate element id '${element.id}'.",
                    element.id, element.name,
                    fixHint = "Re-create or re-paste the element to get a fresh id.",
                )
            }

            val definition = ElementCatalog[element.type]
            if (definition == null) {
                issues += ValidationIssue(
                    Severity.ERROR, IssueCode.UNKNOWN_TYPE,
                    "Unknown element type '${element.type}'.",
                    element.id, element.name,
                    fixHint = "This project may come from a newer build of the designer.",
                )
                continue
            }

            if (!definition.supports(project.edition)) {
                issues += ValidationIssue(
                    Severity.ERROR, IssueCode.UNSUPPORTED_ELEMENT,
                    "'${definition.displayName}' does not exist in ${project.edition.displayName}.",
                    element.id, element.name,
                    fixHint = project.edition.counterpart
                        ?.let { "Delete it, or switch the project to ${it.displayName}." }
                        ?: "Delete it - this element belongs to a Minecraft edition.",
                )
            }

            if (parent != null) {
                val parentDefinition = ElementCatalog[parent.type]
                if (parentDefinition?.acceptsChildren != true) {
                    issues += ValidationIssue(
                        Severity.ERROR, IssueCode.NESTED_IN_NON_CONTAINER,
                        "'${element.name}' is nested inside '${parent.name}', which is not a container.",
                        element.id, element.name,
                        fixHint = "Move it out, or use a Panel Frame / Scroll Container as the parent.",
                    )
                }
            }

            validateSize(element, definition, project.edition, issues)
            if (!clipped) validateBounds(element, parent, project, absolute, issues)
            validateProps(element, definition, project, referencedTextures, issues, strict)

            if (element.type == ElementCatalog.SLOT_INVENTORY) {
                absolute[element.id]?.let { slotRects += element to it }
            }

            if (element.children.isNotEmpty()) {
                // Anything inside a scroll container is meant to overflow and
                // is clipped at runtime, so canvas-bounds checks stop here.
                validateTree(
                    element.children, element, project, absolute,
                    seenIds, referencedTextures, issues, strict,
                    clipped = clipped || element.type == ElementCatalog.CONTAINER_SCROLL,
                )
            }
        }

        // Overlapping inventory slots are almost always a mistake and produce
        // unclickable regions in-game.
        for (i in slotRects.indices) {
            for (j in i + 1 until slotRects.size) {
                val (a, ra) = slotRects[i]
                val (b, rb) = slotRects[j]
                if (ra.intersects(rb)) {
                    issues += ValidationIssue(
                        Severity.WARNING, IssueCode.OVERLAPPING_SLOTS,
                        "Slot '${a.name}' overlaps '${b.name}'.",
                        a.id, a.name,
                        fixHint = "Vanilla slots are 18x18 on an 18px pitch; align them to the grid.",
                    )
                }
            }
        }
    }

    private fun validateSize(
        element: GuiElement,
        definition: com.mcguidesigner.core.catalog.ElementDefinition,
        edition: Edition,
        issues: MutableList<ValidationIssue>,
    ) {
        val size = element.bounds.size
        if (size.width < definition.minSize.width || size.height < definition.minSize.height) {
            issues += ValidationIssue(
                Severity.ERROR, IssueCode.SIZE_TOO_SMALL,
                "'${element.name}' is ${size.width}x${size.height}, below the minimum " +
                    "${definition.minSize.width}x${definition.minSize.height}.",
                element.id, element.name,
                fixHint = "Resize it, or reset the element to its default size.",
            )
        }
        if (size.width > definition.maxSize.width || size.height > definition.maxSize.height) {
            issues += ValidationIssue(
                Severity.ERROR, IssueCode.SIZE_TOO_LARGE,
                "'${element.name}' is ${size.width}x${size.height}, above the maximum " +
                    "${definition.maxSize.width}x${definition.maxSize.height}.",
                element.id, element.name,
            )
        }

        // Fixed-size widgets (inventory slots and the like) cannot be grown, so
        // warning about their touch target would be noise the user cannot act on.
        if (edition == Edition.BEDROCK && definition.interactive && definition.resizable) {
            val min = com.mcguidesigner.core.catalog.ElementDefinition.TOUCH_MIN_TARGET
            if (size.width < min || size.height < min) {
                issues += ValidationIssue(
                    Severity.WARNING, IssueCode.TOUCH_TARGET_TOO_SMALL,
                    "'${element.name}' is smaller than the ${min}x$min touch target Bedrock players expect.",
                    element.id, element.name,
                    fixHint = "Grow the element, or add padding around it.",
                )
            }
        }

        if (edition == Edition.JAVA &&
            element.type == ElementCatalog.JAVA_RECT_BUTTON &&
            size.height != 20
        ) {
            issues += ValidationIssue(
                Severity.WARNING, IssueCode.JAVA_BUTTON_HEIGHT,
                "'${element.name}' is ${size.height}px tall; the vanilla widgets.png button sprite is 20px.",
                element.id, element.name,
                fixHint = "Set the height to 20, or turn off 'Use widgets.png'.",
            )
        }
    }

    private fun validateBounds(
        element: GuiElement,
        parent: GuiElement?,
        project: GuiProject,
        absolute: Map<String, IntRect>,
        issues: MutableList<ValidationIssue>,
    ) {
        val rect = absolute[element.id] ?: return
        val canvasRect = IntRect(0, 0, project.canvas.width, project.canvas.height)
        if (!canvasRect.containsRect(rect)) {
            issues += ValidationIssue(
                if (canvasRect.intersects(rect)) Severity.WARNING else Severity.ERROR,
                IssueCode.OUT_OF_CANVAS,
                "'${element.name}' extends outside the ${project.canvas.width}x${project.canvas.height} canvas.",
                element.id, element.name,
                fixHint = "Move it back inside, or enlarge the canvas in Project settings.",
            )
        }

        val safe = project.canvas.safeArea
        if (project.canvas.hasSafeArea) {
            val safeRect = IntRect.fromEdges(
                safe.left, safe.top,
                project.canvas.width - safe.right,
                project.canvas.height - safe.bottom,
            )
            if (!safeRect.containsRect(rect)) {
                issues += ValidationIssue(
                    Severity.WARNING, IssueCode.OUTSIDE_SAFE_AREA,
                    "'${element.name}' reaches into the device safe-area margin.",
                    element.id, element.name,
                    fixHint = "Notches and gesture bars can cover this element on real devices.",
                )
            }
        }

        if (parent != null) {
            val parentRect = absolute[parent.id]
            if (parentRect != null && !parentRect.containsRect(rect) && parentRect.intersects(rect)) {
                issues += ValidationIssue(
                    Severity.INFO, IssueCode.OUT_OF_CANVAS,
                    "'${element.name}' is clipped by its parent '${parent.name}'.",
                    element.id, element.name,
                )
            }
        }
    }

    private fun validateProps(
        element: GuiElement,
        definition: com.mcguidesigner.core.catalog.ElementDefinition,
        project: GuiProject,
        referencedTextures: MutableSet<String>,
        issues: MutableList<ValidationIssue>,
        strict: Boolean,
    ) {
        val allProps = buildList {
            add(null to element.props)
            element.stateOverrides.forEach { (state, map) -> add(state to map) }
        }

        for ((state, props) in allProps) {
            val stateLabel = state?.let { " (${it.displayName} state)" } ?: ""
            for ((key, value) in props) {
                val spec = definition.property(key, project.edition)
                if (spec == null) {
                    // Only asked between the two Minecraft editions: a
                    // property that exists "in the other one" is a porting
                    // note, and Other UIs has nothing to port to.
                    val counterpart = project.edition.counterpart
                    val existsInOtherEdition =
                        counterpart != null && definition.property(key, counterpart) != null
                    if (existsInOtherEdition && counterpart != null) {
                        issues += ValidationIssue(
                            Severity.WARNING, IssueCode.UNSUPPORTED_PROPERTY,
                            "'$key'$stateLabel on '${element.name}' only applies to " +
                                "${counterpart.displayName} and will be dropped on export.",
                            element.id, element.name, key,
                            fixHint = "Safe to ignore if you also maintain a ${counterpart.displayName} variant.",
                        )
                    } else if (strict) {
                        issues += ValidationIssue(
                            Severity.WARNING, IssueCode.UNKNOWN_PROPERTY,
                            "Unknown property '$key'$stateLabel on '${element.name}'.",
                            element.id, element.name, key,
                        )
                    }
                    continue
                }

                if (!spec.accepts(value)) {
                    issues += ValidationIssue(
                        Severity.ERROR, IssueCode.PROPERTY_TYPE_MISMATCH,
                        "'${spec.label}'$stateLabel on '${element.name}' holds a ${value::class.simpleName} " +
                            "but expects ${spec.type.name.lowercase()}.",
                        element.id, element.name, key,
                    )
                    continue
                }

                if (spec.coerce(value) != value) {
                    issues += ValidationIssue(
                        Severity.WARNING, IssueCode.PROPERTY_OUT_OF_RANGE,
                        "'${spec.label}'$stateLabel on '${element.name}' is outside " +
                            "${spec.min?.toInt() ?: "-inf"}..${spec.max?.toInt() ?: "inf"}.",
                        element.id, element.name, key,
                        fixHint = "The value will be clamped on export.",
                    )
                }

                if (spec.type == PropType.TEXTURE) {
                    val assetId = (value as? TextureValue)?.assetId
                    if (!assetId.isNullOrBlank()) {
                        referencedTextures += assetId
                        val texture = project.texture(assetId)
                        if (texture == null) {
                            issues += ValidationIssue(
                                Severity.ERROR, IssueCode.MISSING_TEXTURE,
                                "'${element.name}' references texture '$assetId', which is not in this project.",
                                element.id, element.name, key,
                                fixHint = "Re-import the image, or clear the texture property.",
                            )
                        } else if (element.type == ElementCatalog.IMAGE_ANIMATED && key == "texture") {
                            // An animated element pointed at a still is not
                            // broken, but it will sit on one frame forever, and
                            // that looks exactly like a bug you cannot find.
                            if (!texture.isAnimated) {
                                issues += ValidationIssue(
                                    Severity.WARNING, IssueCode.ANIMATION_NOT_ANIMATED,
                                    "'${element.name}' is an animated image, but '${texture.name}' has only one frame.",
                                    element.id, element.name, key,
                                    fixHint = "Import a GIF, or a PNG whose height is a whole multiple " +
                                        "of its width with one frame per row.",
                                )
                            } else if (texture.hasVariableFrameTiming) {
                                // Minecraft has one frametime per texture. The
                                // exporter writes per-frame times where it can,
                                // but nothing else in the pipeline does.
                                issues += ValidationIssue(
                                    Severity.INFO, IssueCode.ANIMATION_TIMING_FLATTENED,
                                    "'${texture.name}' came from a source with uneven frame delays; " +
                                        "the resource pack keeps them, other exports use one average rate.",
                                    element.id, element.name, key,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Cross-edition parity: reports what would be lost if the same screen were
     * rebuilt for the other edition.  Surfaced in the export dialog so the user
     * knows before shipping a pack.
     */
    fun parityIssues(project: GuiProject): List<ValidationIssue> {
        // Nothing to report for a target with no counterpart. Other UIs is not
        // a version of a Minecraft screen, so "what would be lost porting this"
        // has no answer rather than an empty one.
        val other = project.edition.counterpart ?: return emptyList()
        val issues = mutableListOf<ValidationIssue>()
        val offenders = project.elements.walkAll()
            .mapNotNull { element -> ElementCatalog[element.type]?.let { element to it } }
            .filterNot { (_, definition) -> definition.supports(other) }
            .toList()

        offenders.forEach { (element, definition) ->
            issues += ValidationIssue(
                Severity.INFO, IssueCode.EDITION_PARITY,
                "'${definition.displayName}' has no equivalent in ${other.displayName}.",
                element.id, element.name,
                fixHint = "A ${other.displayName} port of this screen will need a replacement widget.",
            )
        }
        return issues
    }
}
