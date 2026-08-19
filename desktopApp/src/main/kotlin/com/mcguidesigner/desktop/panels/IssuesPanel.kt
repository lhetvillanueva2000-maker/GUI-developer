package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.core.validation.ValidationIssue
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.InfoBlue
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * Continuous validation output.
 *
 * Clicking a row selects the offending element, which is the fastest possible
 * path from "something is wrong" to "I am now editing the thing that is
 * wrong".
 */
@Composable
fun IssuesPanel(
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val issues = state.validation.issues

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Counter("Errors", state.validation.errorCount, ErrorRed)
            Counter("Warnings", state.validation.warningCount, WarningAmber)
            Counter("Notes", state.validation.infos.size, InfoBlue)
            Box(Modifier.weight(1f))
            Text(
                "Re-check",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                modifier = Modifier.clickable { controller.revalidate() }.padding(4.dp),
            )
        }
        Divider(color = palette.chromeBorder)

        if (issues.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Everything checks out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.accent,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(issues) { issue ->
                IssueRow(issue, selected = issue.elementId != null && issue.elementId == state.primarySelection) {
                    issue.elementId?.let { controller.select(it) }
                }
            }
        }
    }
}

@Composable
private fun Counter(label: String, count: Int, color: Color) {
    val palette = LocalSkinPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(if (count > 0) color else palette.chromeBorder))
        Text(
            "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = if (count > 0) palette.chromeText else palette.chromeTextMuted,
        )
    }
}

@Composable
private fun IssueRow(issue: ValidationIssue, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    val color = when (issue.severity) {
        Severity.ERROR -> ErrorRed
        Severity.WARNING -> WarningAmber
        Severity.INFO -> InfoBlue
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.selectionFill else Color.Transparent)
            .clickable(enabled = issue.elementId != null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color))
        Column(Modifier.weight(1f)) {
            Text(issue.message, style = MaterialTheme.typography.labelMedium, color = palette.chromeText)
            issue.fixHint?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
            }
            Text(
                buildString {
                    append(issue.code)
                    issue.elementName?.let { append("  ·  ").append(it) }
                    issue.propertyKey?.let { append("  ·  ").append(it) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeBorder,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
