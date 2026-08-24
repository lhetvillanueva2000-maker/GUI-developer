package com.mcguidesigner.desktop.dialogs

import com.mcguidesigner.core.Branding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.desktop.ActiveDialog
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.desktop.io.Workspace
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Height of the template and recent-project columns on the welcome screen. */
private val COLUMN_HEIGHT = 230.dp

/**
 * The first thing a new user sees.
 *
 * A design tool that drops you straight into an untitled document leaves you
 * guessing at what it can do; this offers the four things you actually want on
 * launch - start blank in either edition, start from a template, open
 * something, or pick up where you left off.
 */
@Composable
fun WelcomeDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    var dontShowAgain by remember { mutableStateOf(!app.preferences.showWelcomeOnStart) }

    fun dismiss() {
        app.persistPreferences(showWelcomeOnStart = !dontShowAgain)
        app.dialog = ActiveDialog.NONE
    }

    AlertDialog(
        onDismissRequest = { dismiss() },
        title = {
            Column {
                Text(Branding.NAME, fontWeight = FontWeight.SemiBold)
                Text(
                    "Design screens for Java Edition and Bedrock Edition, then export a " +
                        "resource pack, a UI pack or ready-to-paste code.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        text = {
            Column(Modifier.width(640.dp)) {
                Text("Start something new", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Edition.entries.forEach { edition ->
                        val skin = SkinRegistry.forEdition(edition)
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.chromePanelAlt)
                                .border(1.dp, palette.chromeBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    app.persistPreferences(showWelcomeOnStart = !dontShowAgain)
                                    app.newProject(edition, "Untitled ${edition.displayName} Screen")
                                }
                                .padding(14.dp),
                        ) {
                            Text(
                                "Blank ${edition.displayName} screen",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(Modifier.height(3.dp))
                            Text(
                                skin.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chromeTextMuted,
                            )
                        }
                    }
                }

                Box(Modifier.height(14.dp))
                Divider(color = palette.chromeBorder)
                Box(Modifier.height(12.dp))

                // Both lists are given the same fixed height so the dialog is
                // the same size however many recents there are, and so a list
                // that overflows scrolls instead of being cut off mid-item.
                Row(
                    Modifier.height(COLUMN_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // -- Templates -------------------------------------------
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text("Start from a template", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.height(6.dp))
                        LazyColumn(Modifier.fillMaxHeight()) {
                            items(BuiltInTemplates.all, key = { it.id }) { template ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.chromePanelAlt)
                                        .clickable {
                                            app.persistPreferences(showWelcomeOnStart = !dontShowAgain)
                                            app.newFromTemplate(template.id)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            template.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            template.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.chromeTextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Box(Modifier.width(8.dp))
                                    Text(
                                        template.edition.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.accent,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    // -- Recent files ----------------------------------------
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text("Recent projects", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.height(6.dp))
                        if (app.recentFiles.isEmpty()) {
                            Text(
                                "Nothing yet. Projects you save are listed here and in File › Open Recent.",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chromeTextMuted,
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxHeight()) {
                                items(app.recentFiles, key = { it.absolutePath }) { file ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.chromePanelAlt)
                                            .clickable {
                                                app.persistPreferences(showWelcomeOnStart = !dontShowAgain)
                                                app.openFile(file)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            file.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            file.parent.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.chromeTextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Text(
                        "Don't show this on startup",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { dontShowAgain = !dontShowAgain },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.persistPreferences(showWelcomeOnStart = !dontShowAgain)
                app.dialog = ActiveDialog.NONE
                app.open()
            }) { Text("Open project…") }
        },
        dismissButton = { TextButton(onClick = { dismiss() }) { Text("Close") } },
    )
}

/**
 * Save / Discard / Cancel, shown before anything that would replace the
 * document.
 *
 * Deliberately not dismissible by clicking away: the whole point is that the
 * user makes an explicit choice about their unsaved work.
 */
@Composable
fun UnsavedChangesDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    val name = app.controller.current.project.name

    AlertDialog(
        onDismissRequest = { app.cancelPendingAction() },
        title = { Text("Save changes to '$name'?") },
        text = {
            Column(Modifier.width(420.dp)) {
                Text(
                    "This project has changes that have not been written to disk.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(Modifier.height(6.dp))
                Text(
                    "Discarding them will ${app.pendingActionLabel} and the edits will be lost.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { app.resolveUnsavedByDiscarding() }) {
                    Text("Discard", color = WarningAmber)
                }
                TextButton(onClick = { app.resolveUnsavedBySaving() }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = { app.cancelPendingAction() }) { Text("Cancel") } },
    )
}

/**
 * Offered at startup when the previous session left an autosave snapshot
 * behind - that is, when it was killed rather than closed.
 */
@Composable
fun RecoveryDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    val recovery = remember { Workspace.pendingRecovery() }

    if (recovery == null) {
        // The snapshot disappeared between startup and this frame; nothing to
        // offer, so fall through to the normal startup path.
        app.discardRecovery()
        return
    }

    val savedAt = remember(recovery.marker.savedAtMillis) {
        if (recovery.marker.savedAtMillis <= 0L) {
            "an earlier session"
        } else {
            SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault())
                .format(Date(recovery.marker.savedAtMillis))
        }
    }

    AlertDialog(
        onDismissRequest = { /* an explicit choice is required */ },
        title = { Text("Recover unsaved work?") },
        text = {
            Column(Modifier.width(460.dp)) {
                Text(
                    "The designer closed unexpectedly with unsaved changes to " +
                        "'${recovery.marker.projectName}'.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(Modifier.height(8.dp))
                Row {
                    Text(
                        "Autosaved",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                        modifier = Modifier.width(70.dp),
                    )
                    Text(savedAt, style = MaterialTheme.typography.labelSmall)
                }
                recovery.originalFile?.let { file ->
                    Row(Modifier.padding(top = 2.dp)) {
                        Text(
                            "File",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                            modifier = Modifier.width(70.dp),
                        )
                        Text(file.absolutePath, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
                Box(Modifier.height(10.dp))
                Text(
                    "Recovering opens the snapshot as unsaved work - nothing is written " +
                        "over until you save it yourself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { app.adoptRecovery(recovery) }) { Text("Recover") }
        },
        dismissButton = {
            TextButton(onClick = { app.discardRecovery() }) { Text("Discard", color = Color(0xFFB0483F)) }
        },
    )
}
