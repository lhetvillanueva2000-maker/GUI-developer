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
    )

    fun export(project: GuiProject, target: ExportTarget, codeTarget: CodeTarget = CodeTarget.HTML_CSS): ExportBundle =
        when (target) {
            ExportTarget.JAVA_RESOURCE_PACK -> JavaEditionExporter.export(project)
                .withCrossEditionNotice(project, Edition.JAVA)

            ExportTarget.BEDROCK_UI_PACK -> BedrockEditionExporter.export(project)
                .withCrossEditionNotice(project, Edition.BEDROCK)

            ExportTarget.PROJECT_JSON -> projectBundle(project)
            ExportTarget.CODE -> codeBundle(project, codeTarget)
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
