package com.mcguidesigner.desktop.io

import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.PROJECT_EXTENSION
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.exporters.ExportBundle
import com.mcguidesigner.exporters.bytes
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Desktop file access.
 *
 * Uses AWT's native [FileDialog] rather than Swing's `JFileChooser` so the
 * dialogs are the real OS ones on Windows and macOS.
 */
object DesktopFileIO {

    /** Where "Open"/"Save" start when the project has never been saved. */
    var lastDirectory: File = File(System.getProperty("user.home"), Branding.NAME)
        .also { runCatching { it.mkdirs() } }

    // -- Project documents -------------------------------------------------

    fun openProjectDialog(owner: Frame?): File? =
        showDialog(owner, "Open GUI project", FileDialog.LOAD, "*.$PROJECT_EXTENSION")

    fun saveProjectDialog(owner: Frame?, suggestedName: String): File? {
        val chosen = showDialog(
            owner, "Save GUI project", FileDialog.SAVE,
            suggestedName.ensureExtension(PROJECT_EXTENSION),
        ) ?: return null
        return chosen.ensureExtension(PROJECT_EXTENSION)
    }

    fun readProject(file: File): LoadResult = runCatching {
        ProjectSerializer.decode(file.readText())
    }.getOrElse { LoadResult.Failure("Could not read ${file.name}: ${it.message}", it) }

    fun writeProject(file: File, project: GuiProject): Result<File> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(ProjectSerializer.encode(project))
        lastDirectory = file.parentFile ?: lastDirectory
        file
    }

    // -- Texture import ----------------------------------------------------

    fun importImageDialog(owner: Frame?): List<File> {
        val dialog = FileDialog(owner, "Import textures", FileDialog.LOAD).apply {
            directory = lastDirectory.absolutePath
            isMultipleMode = true
            file = "*.png;*.jpg;*.jpeg;*.gif;*.webp"
            setFilenameFilter { _, name ->
                name.lowercase().substringAfterLast('.') in IMAGE_EXTENSIONS
            }
            isVisible = true
        }
        val files = dialog.files?.toList().orEmpty()
        files.firstOrNull()?.parentFile?.let { lastDirectory = it }
        return files.filter { it.isFile }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    // -- Resource packs ----------------------------------------------------

    /**
     * Picks a resource pack archive.
     *
     * Both editions ship their packs as plain zips - Java packs are sometimes
     * renamed to `.mcpack` on the Bedrock side - so the filter accepts all
     * three rather than insisting on `.zip`.
     */
    fun openPackDialog(owner: Frame?): File? {
        val dialog = FileDialog(owner, "Import a resource pack", FileDialog.LOAD).apply {
            directory = lastDirectory.absolutePath
            file = "*.zip;*.mcpack;*.jar"
            setFilenameFilter { _, name ->
                name.lowercase().substringAfterLast('.') in PACK_EXTENSIONS
            }
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(directory, name).also { lastDirectory = it.parentFile ?: lastDirectory }
    }

    private val PACK_EXTENSIONS = setOf("zip", "mcpack", "jar", "mcaddon")

    // -- Exports -----------------------------------------------------------

    fun chooseDirectoryDialog(owner: Frame?, title: String): File? {
        // AWT has no native directory picker on Windows, so this is the one
        // place Swing's chooser is the better option.
        val chooser = javax.swing.JFileChooser(lastDirectory).apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val result = chooser.showDialog(owner, "Select")
        if (result != javax.swing.JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.also { lastDirectory = it }
    }

    fun saveFileDialog(owner: Frame?, title: String, suggestedName: String): File? =
        showDialog(owner, title, FileDialog.SAVE, suggestedName)

    /** Writes every file of [bundle] under `<target>/<bundle.rootName>`. */
    fun writeBundle(bundle: ExportBundle, target: File): Result<File> = runCatching {
        val root = target.resolve(bundle.rootName)
        bundle.files.forEach { file ->
            // Bundle paths already start with the root folder name.
            val destination = target.resolve(file.path)
            destination.parentFile?.mkdirs()
            destination.writeBytes(file.bytes())
        }
        lastDirectory = target
        if (root.exists()) root else target
    }

    /** Writes [bundle] straight into a `.zip`, preserving its folder layout. */
    fun writeBundleAsZip(bundle: ExportBundle, zipFile: File): Result<File> = runCatching {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            bundle.files.sortedBy { it.path }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.path))
                zip.write(file.bytes())
                zip.closeEntry()
            }
        }
        lastDirectory = zipFile.parentFile ?: lastDirectory
        zipFile
    }

    fun writeText(file: File, content: String): Result<File> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(content)
        lastDirectory = file.parentFile ?: lastDirectory
        file
    }

    // -- Helpers -----------------------------------------------------------

    private fun showDialog(owner: Frame?, title: String, mode: Int, fileName: String): File? {
        val dialog = FileDialog(owner, title, mode).apply {
            directory = lastDirectory.absolutePath
            file = fileName
            isVisible = true
        }
        val directory = dialog.directory ?: return null
        val name = dialog.file ?: return null
        val result = File(directory, name)
        lastDirectory = result.parentFile ?: lastDirectory
        return result
    }

    private fun String.ensureExtension(extension: String): String =
        if (endsWith(".$extension", ignoreCase = true)) this else "$this.$extension"

    private fun File.ensureExtension(extension: String): File =
        if (name.endsWith(".$extension", ignoreCase = true)) this else File(parentFile, "$name.$extension")
}
