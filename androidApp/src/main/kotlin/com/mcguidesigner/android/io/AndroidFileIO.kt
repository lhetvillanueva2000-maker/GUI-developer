package com.mcguidesigner.android.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mcguidesigner.exporters.ExportBundle
import com.mcguidesigner.exporters.bytes
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Storage access on Android.
 *
 * Everything goes through the Storage Access Framework, so the app needs no
 * storage permissions at all: the user picks exactly which document to read or
 * create, and Android hands back a scoped [Uri].
 */
object AndroidFileIO {

    const val PROJECT_MIME = "application/json"
    const val ZIP_MIME = "application/zip"

    val IMAGE_MIME_TYPES = arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun readText(context: Context, uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Could not open the selected document.")
    }

    fun readBytes(context: Context, uri: Uri): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not open the selected file.")
    }

    fun writeText(context: Context, uri: Uri, text: String): Result<Unit> = runCatching {
        // "wt" truncates: without it, saving a shorter document would leave
        // the tail of the previous version behind.
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
            ?: error("Could not write to the selected document.")
    }

    /**
     * Writes a whole export bundle into a single zip at [uri].
     *
     * Android has no reliable "pick a folder and write a tree" story across
     * OEMs, so the mobile export is always a zip - which is also the format
     * both editions want for distribution anyway.
     */
    fun writeBundleZip(context: Context, uri: Uri, bundle: ExportBundle): Result<Int> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                bundle.files.sortedBy { it.path }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.path))
                    zip.write(file.bytes())
                    zip.closeEntry()
                }
            }
        } ?: error("Could not create the zip file.")
        bundle.files.size
    }

    /** Best-effort display name for a content Uri. */
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    /** Keeps read/write access to a picked document across app restarts. */
    fun persistPermission(context: Context, uri: Uri, writable: Boolean) {
        runCatching {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (writable) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    /** Cache-directory file used when sharing generated code with other apps. */
    fun writeToCache(context: Context, fileName: String, content: String): Result<File> = runCatching {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        File(directory, fileName).apply { writeText(content) }
    }
}
