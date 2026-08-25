package com.mcguidesigner.core

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.validation.IssueCode
import com.mcguidesigner.core.validation.ProjectValidator
import kotlin.test.Test
import kotlin.test.assertTrue

class OtherUiTemplateTest {

    @Test
    fun `every bundled template validates cleanly in strict mode`() {
        // Not just the new ones: a starter template is the first thing anybody
        // opens, and one that arrives with warnings on it teaches people that
        // the warnings panel is noise. Getting this wrong is how "unknown
        // property" ends up on every element of every new document - which is
        // exactly what the first cut of the Other UIs templates did, because
        // they used property names that were never in the catalog.
        val complaints = BuiltInTemplates.all.flatMap { template ->
            ProjectValidator.validate(template.canonical(), strict = true).issues
                .filter { it.code == IssueCode.UNKNOWN_PROPERTY || it.code == IssueCode.UNSUPPORTED_PROPERTY }
                .map { "${template.id}: ${it.message}" }
        }
        assertTrue(complaints.isEmpty(), complaints.joinToString("\n"))
    }

    @Test
    fun `the new target has somewhere to start`() {
        val templates = BuiltInTemplates.forEdition(Edition.OTHER)
        assertTrue(templates.isNotEmpty(), "Other UIs needs starter templates like the other two")
        templates.forEach { template ->
            val project = template.canonical()
            assertTrue(project.elements.isNotEmpty(), "${template.id} is empty")
            assertTrue(project.edition == Edition.OTHER, "${template.id} is not an Other UIs project")
        }
    }

    @Test
    fun `no Minecraft furniture leaks into a non-Minecraft screen`() {
        // Inventory slots and hotbars exist because Minecraft has them. On a
        // login form they are furniture from a different building.
        val minecraftOnly = setOf("panel.chest", "panel.tooltip", "slot.inventory", "strip.hotbar")
        BuiltInTemplates.forEdition(Edition.OTHER).forEach { template ->
            template.canonical().elements.forEach { element ->
                assertTrue(
                    element.type !in minecraftOnly,
                    "${template.id} uses ${element.type}",
                )
            }
        }
    }
}
