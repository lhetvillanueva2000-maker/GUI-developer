package com.mcguidesigner.exporters

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.PROJECT_EXTENSION
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.core.validation.ProjectValidator
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.core.validation.ValidationIssue

/**
 * Single entry point the UI layers call to produce an export.
 *
 * Keeping the orchestration here means desktop and Android share the same
 * warnings, the same folder names and the same edition rules; the only
 * platform-specific part is writing [ExportFile]s to storage.
 */
object ExportManager {

    /** Targets offered for a project of [edition], in menu order. */
    fun availableTargets(edition: Edition): List<ExportTarget> = listOf(
        when (edition) {
            Edition.JAVA -> ExportTarget.JAVA_RESOURCE_PACK
            Edition.BEDROCK -> ExportTarget.BEDROCK_UI_PACK
        },
        ExportTarget.PROJECT_JSON,
        ExportTarget.CODE,
        // The opposite edition is always offered too, with parity warnings
        // attached, because porting a screen is a common reason to export.
        when (edition) {
            Edition.JAVA -> ExportTarget.BEDROCK_UI_PACK
            Edition.BEDROCK -> ExportTarget.JAVA_RESOURCE_PACK
        },
        ExportTarget.EVERYTHING,
    )

    fun export(project: GuiProject, target: ExportTarget, codeTarget: CodeTarget = CodeTarget.HTML_CSS): ExportBundle =
        when (target) {
            ExportTarget.JAVA_RESOURCE_PACK -> JavaEditionExporter.export(project)
                .withCrossEditionNotice(project, Edition.JAVA)

            ExportTarget.BEDROCK_UI_PACK -> BedrockEditionExporter.export(project)
                .withCrossEditionNotice(project, Edition.BEDROCK)

            ExportTarget.PROJECT_JSON -> projectBundle(project)
            ExportTarget.CODE -> codeBundle(project, codeTarget)
            ExportTarget.EVERYTHING -> everythingBundle(project)
        }

    /**
     * Both edition packs, every code target and the project document, in one
     * tree.
     *
     * The opposite edition's pack and its edition-specific code target are
     * included on purpose: this target means "everything", and someone porting
     * a screen between editions is exactly who reaches for it.  The parity
     * warnings that come with a cross-edition export are carried through, so
     * the output is never quietly wrong.
     */
    private fun everythingBundle(project: GuiProject): ExportBundle {
        val base = Ids.slug(project.name)
        val root = "${base}_everything"
        val files = mutableListOf<ExportFile>()
        val warnings = mutableListOf<ValidationIssue>()

        fun adopt(folder: String, bundle: ExportBundle) {
            bundle.files.forEach { files += it.movedTo("$root/$folder/${it.path}") }
            warnings += bundle.warnings
        }

        adopt("java-edition", export(project, ExportTarget.JAVA_RESOURCE_PACK))
        adopt("bedrock-edition", export(project, ExportTarget.BEDROCK_UI_PACK))
        adopt("project", projectBundle(project))

        // One file per language rather than one bundle per language: the code
        // targets all describe the same screen, so they belong side by side.
        CodeTarget.entries.forEach { codeTarget ->
            val generated = CodeGenerator.generate(project, codeTarget)
            files += ExportFile.Text("$root/code/${generated.fileName}", generated.source)
        }
        warnings += ProjectValidator.validate(project).issues.filter { it.severity != Severity.INFO }

        files += ExportFile.Text("$root/README.md", everythingReadme(project, base))

        return ExportBundle(
            target = ExportTarget.EVERYTHING,
            rootName = root,
            files = files.distinctBy { it.path },
            warnings = warnings.distinct(),
        )
    }

    private fun everythingReadme(project: GuiProject, base: String): String = buildString {
        appendLine("# ${project.name}")
        appendLine()
        appendLine("Everything this screen exports to, produced in one pass by Minecraft GUI Designer.")
        appendLine("Designed for **${project.edition.displayName}**, ${project.canvas.width}x${project.canvas.height} GUI pixels.")
        appendLine()
        appendLine("| Folder | What it is |")
        appendLine("| --- | --- |")
        appendLine("| `java-edition/` | Java Edition resource pack plus a `Screen` subclass. |")
        appendLine("| `bedrock-edition/` | Bedrock Edition JSON-UI resource pack. |")
        appendLine("| `code/` | The same screen as HTML, CSS, Compose, Java and Bedrock JSON. |")
        appendLine("| `project/` | `$base.mcgui` - reopen this to keep editing. |")
        appendLine()
        appendLine("The pack for the edition this screen was **not** designed for is a best-effort")
        appendLine("port: elements with no equivalent are exported as plain panels. Check the")
        appendLine("warnings the export reported before shipping it.")
    }

    private fun projectBundle(project: GuiProject): ExportBundle {
        val base = Ids.slug(project.name)
        return ExportBundle(
            target = ExportTarget.PROJECT_JSON,
            rootName = base,
            files = listOf(ExportFile.Text("$base.$PROJECT_EXTENSION", ProjectSerializer.encode(project))),
            warnings = ProjectValidator.validate(project).issues.filter { it.severity != Severity.INFO },
        )
    }

    private fun codeBundle(project: GuiProject, codeTarget: CodeTarget): ExportBundle {
        val generated = CodeGenerator.generate(project, codeTarget)
        return ExportBundle(
            target = ExportTarget.CODE,
            rootName = Ids.slug(project.name) + "_code",
            files = listOf(ExportFile.Text(generated.fileName, generated.source)),
            warnings = ProjectValidator.validate(project).issues.filter { it.severity == Severity.ERROR },
        )
    }

    /**
     * Everything at once: both edition packs, the project document and an HTML
     * preview.  This is what `build-scripts/export-samples` uses to regenerate
     * `templates/sample-output/`.
     */
    fun exportAll(project: GuiProject): List<ExportBundle> = listOf(
        JavaEditionExporter.export(project),
        BedrockEditionExporter.export(project),
        projectBundle(project),
        codeBundle(project, CodeTarget.HTML_CSS),
    )

    /**
     * Adds an explicit heads-up when a screen is exported for the edition it
     * was *not* designed for, listing exactly what will not survive.
     */
    private fun ExportBundle.withCrossEditionNotice(project: GuiProject, exportEdition: Edition): ExportBundle {
        if (project.edition == exportEdition) return this
        val lost = ProjectValidator.parityIssues(project)
        val notice = ValidationIssue(
            severity = Severity.WARNING,
            code = com.mcguidesigner.core.validation.IssueCode.EDITION_PARITY,
            message = "This project targets ${project.edition.displayName} but is being exported for " +
                "${exportEdition.displayName}. ${lost.size} element(s) have no direct equivalent.",
            fixHint = "Review the listed elements; they are exported as generic panels.",
        )
        return copy(warnings = listOf(notice) + lost + warnings)
    }
}
