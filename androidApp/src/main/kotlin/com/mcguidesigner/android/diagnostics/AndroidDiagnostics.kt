package com.mcguidesigner.android.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.mcguidesigner.android.BuildConfig
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.diagnostics.Diagnostics
import com.mcguidesigner.core.diagnostics.LogLevel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * The Android end of the log: crash capture, device facts and the clipboard.
 *
 * The pure ring buffer lives in `sharedCore`; everything here is the part that
 * needs a `Context` - which is also the part that makes the log worth having on
 * a phone, where there is no console to read and no cable attached.
 */
object AndroidDiagnostics {

    /**
     * Where a crash is parked between dying and the next launch.
     *
     * A file rather than anything in memory for the obvious reason: the process
     * is about to stop existing. Written synchronously in the handler, because
     * there is no "later" to defer it to.
     */
    private const val CRASH_FILE = "last-crash.txt"

    /** Installs the handler that catches whatever nothing else caught. */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = stackTraceOf(error)
                record(LogLevel.CRASH, "crash", "${error::class.java.simpleName} on ${thread.name}", trace)
                File(appContext.filesDir, CRASH_FILE).writeText(
                    Diagnostics.report(header(appContext)),
                )
            }
            // Always hand back to whoever was there. Swallowing the crash would
            // leave the process wedged instead of restarting, and would hide it
            // from the platform's own reporting.
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * The crash from the previous run, if the previous run had one.
     *
     * Read once and deleted: a crash report that keeps reappearing after it has
     * been dealt with trains people to dismiss it without reading.
     */
    fun consumePreviousCrash(context: Context): String? {
        val file = File(context.applicationContext.filesDir, CRASH_FILE)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return text?.takeIf { it.isNotBlank() }
    }

    /** Records an entry with the current time. */
    fun record(level: LogLevel, tag: String, message: String, detail: String? = null) {
        Diagnostics.record(System.currentTimeMillis(), level, tag, message, detail)
    }

    /** Records a caught exception, keeping its real text rather than a summary. */
    fun recordFailure(tag: String, message: String, error: Throwable) {
        record(LogLevel.ERROR, tag, message, stackTraceOf(error))
    }

    /**
     * Chatter that is only worth keeping while testing.
     *
     * Gated on the build type so the release app's log stays a fault log. A
     * hundred routine lines between two errors is how a log stops being read.
     */
    fun trace(tag: String, message: String) {
        if (BuildConfig.DEBUG) record(LogLevel.INFO, tag, message)
    }

    /** What whoever reads a report will ask for first. */
    fun header(context: Context): Map<String, String> = linkedMapOf(
        "App" to "${Branding.NAME} ${Branding.VERSION}",
        "Build" to if (BuildConfig.DEBUG) "debug" else "release",
        "Package" to context.packageName,
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
    )

    /** The whole log, formatted and ready to paste. */
    fun report(context: Context): String = Diagnostics.report(header(context))

    /**
     * Puts [text] on the clipboard.
     *
     * The entire point of this screen: an error you can read but not copy is
     * an error you have to retype, and nobody retypes a stack trace correctly.
     */
    fun copyToClipboard(context: Context, text: String): Boolean = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("${Branding.NAME} diagnostics", text))
        true
    }.getOrDefault(false)

    private fun stackTraceOf(error: Throwable): String {
        val writer = StringWriter()
        PrintWriter(writer).use { error.printStackTrace(it) }
        return writer.toString()
    }
}
