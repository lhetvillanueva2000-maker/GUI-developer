package com.mcguidesigner.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.MobileSheet
import com.mcguidesigner.android.diagnostics.AndroidDiagnostics
import com.mcguidesigner.core.diagnostics.Diagnostics
import com.mcguidesigner.core.diagnostics.LogLevel
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * What has gone wrong, in full, with a button that copies it.
 *
 * This screen exists because of one specific failure mode: something breaks on
 * a device that is not on a desk, and the only account of it that reaches
 * anybody is a person describing what they saw. That description is never the
 * exception's actual message, and the actual message is the whole of what is
 * needed to fix it.
 *
 * So: the real text, the whole stack trace, and Copy. Nothing is summarised for
 * readability - if it is ugly, it is ugly in exactly the way the fixer needs.
 */
@Composable
fun DiagnosticsSheet(app: AndroidAppState) {
    val palette = LocalSkinPalette.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }

    // Read once per revision rather than observed: the log is a plain buffer,
    // and a sheet that reflowed under the reader every time something was
    // recorded would be unreadable exactly when it is busiest.
    val entries = remember(revision) { Diagnostics.snapshot().asReversed() }
    val header = remember(revision) { AndroidDiagnostics.header(context) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SheetTitle(
            "Diagnostics",
            if (entries.isEmpty()) {
                "Nothing has been recorded this session."
            } else {
                "${entries.size} entr${if (entries.size == 1) "y" else "ies"}, newest first."
            },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.chromePanelAlt)
                .padding(12.dp),
        ) {
            header.forEach { (key, value) ->
                Text(
                    "$key: $value",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = palette.chromeTextMuted,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    copied = AndroidDiagnostics.copyToClipboard(context, AndroidDiagnostics.report(context))
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (copied) "Copied" else "Copy everything") }

            TextButton(onClick = {
                Diagnostics.clear()
                copied = false
                revision++
            }) { Text("Clear") }
        }

        if (copied) {
            Text(
                "On the clipboard. Paste it wherever you are reporting from.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        entries.forEach { entry ->
            val colour = when (entry.level) {
                LogLevel.CRASH, LogLevel.ERROR -> ErrorRed
                LogLevel.WARN -> WarningAmber
                LogLevel.INFO -> palette.chromeTextMuted
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.chromeBackground)
                    .padding(10.dp),
            ) {
                Text(
                    "${entry.time}  ${entry.level.label}  ${entry.tag}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colour,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    entry.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeText,
                    modifier = Modifier.padding(top = 2.dp),
                )
                entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    // Scrolls sideways rather than wrapping: a wrapped stack
                    // trace is much harder to read, and this one is meant to be
                    // read as much as copied.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.chromePanelAlt)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                    ) {
                        Text(
                            detail.trimEnd(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = palette.chromeTextMuted,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                "Anything that fails from here on will appear in this list with its " +
                    "real message and stack trace, and a crash will be waiting here the " +
                    "next time the app opens.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        TextButton(
            onClick = { app.sheet = MobileSheet.NONE },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) { Text("Close") }

        Box(Modifier.height(24.dp))
    }
}
