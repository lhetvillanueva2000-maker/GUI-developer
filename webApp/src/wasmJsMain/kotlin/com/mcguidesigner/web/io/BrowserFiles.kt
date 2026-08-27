package com.mcguidesigner.web.io

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader

/**
 * Opening and saving files, the only way a web page is allowed to.
 *
 * There is no path here and there cannot be one: a page may hand the browser
 * bytes to save and may read a file the person deliberately chose, and that is
 * the whole of it. So the editor's "save" is a download and its "open" is a
 * file picker, and nothing in the app above this layer knows the difference -
 * [com.mcguidesigner.core.editor.EditorState.filePath] simply stays null, as
 * it already does on Android for a document opened from a content URI.
 *
 * The File System Access API (`showSaveFilePicker`) would give real
 * save-in-place, but only in Chromium and only over HTTPS, and a save button
 * that works in one browser is worse than one that works the same everywhere.
 */
object BrowserFiles {

    /**
     * Hands [bytes] to the browser as a download named [fileName].
     *
     * The object URL is revoked on the next turn of the event loop rather than
     * immediately: Safari starts the download asynchronously and cancels it if
     * the URL has already gone, which produces a "download failed" with no
     * error anywhere and nothing to look at.
     */
    fun save(fileName: String, bytes: ByteArray, mime: String = "application/octet-stream") {
        val array = bytes.toUint8Array()
        val blob = Blob(arrayOfUint8(array), BlobPropertyBag(type = mime))
        val url = URL.createObjectURL(blob)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = fileName
        anchor.style.display = "none"
        document.body?.appendChild(anchor)
        anchor.click()
        document.body?.removeChild(anchor)
        revokeSoon(url)
    }

    /** [save], for text. UTF-8, because everything this app writes is. */
    fun saveText(fileName: String, text: String, mime: String = "text/plain;charset=utf-8") {
        save(fileName, text.encodeToByteArray(), mime)
    }

    /**
     * Opens the file picker and calls [onChosen] with what came back.
     *
     * [accept] is the `accept` attribute - a hint, not a guarantee, since every
     * picker lets you defeat it. Nothing happens if the picker is dismissed.
     *
     * The input element is created per call and thrown away. Reusing one looks
     * tidier and does not work: choosing the same file twice in a row fires no
     * `change` event, because from the element's point of view the value did
     * not change - which reads as "opening that file did nothing this time".
     */
    fun open(accept: String, onChosen: (name: String, bytes: ByteArray) -> Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = accept
        input.style.display = "none"
        input.onchange = {
            val file = input.files?.item(0)
            if (file != null) {
                val reader = FileReader()
                reader.onload = { _ ->
                    val buffer = reader.result
                    onChosen(file.name, buffer.toByteArray())
                    null
                }
                reader.readAsArrayBuffer(file)
            }
            document.body?.removeChild(input)
            null
        }
        document.body?.appendChild(input)
        input.click()
    }

    /** Opens [url] in a new tab, with the opener detached. */
    fun openLink(url: String) {
        if (url.isBlank()) return
        kotlinx.browser.window.open(url, "_blank", "noopener,noreferrer")
    }

    /** Puts [text] on the clipboard. Returns false when the browser refuses. */
    fun copyToClipboard(text: String): Boolean = runCatching {
        writeClipboard(text)
        true
    }.getOrElse { false }
}

// ---------------------------------------------------------------------------
// The JavaScript edge.
//
// Kotlin/Wasm cannot hand a ByteArray to a browser API directly: the two live
// in different memories, and everything crossing between them has to be copied
// through a typed array explicitly. These four are that copy, kept in one place
// so the rest of the shell never touches an external interface type.
// ---------------------------------------------------------------------------

private fun ByteArray.toUint8Array(): JsAny {
    val out = newUint8Array(size)
    for (i in indices) setByte(out, i, this[i].toInt())
    return out
}

private fun JsAny?.toByteArray(): ByteArray {
    if (this == null) return ByteArray(0)
    val view = newUint8ArrayOf(this)
    val length = byteLength(view)
    return ByteArray(length) { getByte(view, it).toByte() }
}

@Suppress("UNUSED_PARAMETER")
private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

@Suppress("UNUSED_PARAMETER")
private fun newUint8ArrayOf(buffer: JsAny): JsAny = js("new Uint8Array(buffer)")

@Suppress("UNUSED_PARAMETER")
private fun setByte(array: JsAny, index: Int, value: Int) { js("array[index] = value") }

@Suppress("UNUSED_PARAMETER")
private fun getByte(array: JsAny, index: Int): Int = js("array[index]")

@Suppress("UNUSED_PARAMETER")
private fun byteLength(array: JsAny): Int = js("array.length")

@Suppress("UNUSED_PARAMETER")
private fun arrayOfUint8(part: JsAny): JsArray<JsAny?> = js("[part]")

@Suppress("UNUSED_PARAMETER")
private fun revokeSoon(url: String) { js("setTimeout(function(){ URL.revokeObjectURL(url); }, 60000)") }

@Suppress("UNUSED_PARAMETER")
private fun writeClipboard(text: String) { js("navigator.clipboard && navigator.clipboard.writeText(text)") }
