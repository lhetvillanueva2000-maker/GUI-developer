package com.mcguidesigner.web.io

import com.mcguidesigner.styles.settings.AppearanceSettings
import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Preferences and the last session, in `localStorage`.
 *
 * The desktop keeps these in `preferences.json` and Android in its own store;
 * this is the third of the three, and it persists the same
 * [AppearanceSettings] object as both, through the same `fromStored` factory -
 * so a setting cannot exist on two platforms and quietly not on the third.
 *
 * Every read and write is wrapped, because `localStorage` is not merely empty
 * in a private window or with site data blocked: *touching* it throws in some
 * browsers. An app that will not start because it could not remember your
 * theme is a worse app than one that forgets.
 */
object WebPreferences {

    private const val PREFIX = "uilabs."

    fun load(): AppearanceSettings = AppearanceSettings.fromStored(
        themeMode = read("theme"),
        chromeTheme = read("chromeTheme"),
        storedMotion = read("motion"),
        backdropEnabled = read("backdrop") != "false",
        legacyBackdropMotion = read("backdropMotion") != "false",
        profileName = read("profileName").orEmpty(),
    )

    fun save(settings: AppearanceSettings) {
        write("theme", settings.theme.name)
        write("chromeTheme", settings.chromeTheme.name)
        write("motion", settings.motion.name)
        write("backdrop", settings.backdropEnabled.toString())
        write("profileName", settings.profileName)
    }

    /** The id of the release note that has been read, or null. */
    fun dismissedNotice(): String? = read("notice")

    fun rememberNotice(id: String) = write("notice", id)

    // -----------------------------------------------------------------------
    // The session.
    //
    // A browser tab is closed and reopened far more casually than an app is,
    // and there is no file on disk to come back to - so losing the document to
    // a refresh would lose it for good. The whole project is kept as its own
    // `.mcgui` JSON, which is the format it would have been saved in anyway.
    // -----------------------------------------------------------------------

    /**
     * Roughly a megabyte of JSON.
     *
     * `localStorage` is a small, synchronous, per-origin store - typically 5MB
     * for everything an origin keeps, and writing a large value blocks the
     * frame. A design big enough to pass this is one with a lot of embedded
     * textures in it, and for that the honest answer is that it has to be
     * exported rather than left in a tab.
     */
    private const val MAX_SESSION_CHARS = 1_000_000

    fun saveSession(projectJson: String): Boolean {
        if (projectJson.length > MAX_SESSION_CHARS) {
            clearSession()
            return false
        }
        return write("session", projectJson)
    }

    fun restoreSession(): String? = read("session")

    fun clearSession() = remove("session")

    // -----------------------------------------------------------------------

    private fun read(key: String): String? = runCatching { localStorage[PREFIX + key] }.getOrNull()

    private fun write(key: String, value: String): Boolean =
        runCatching { localStorage[PREFIX + key] = value }.isSuccess

    private fun remove(key: String) {
        runCatching { localStorage.removeItem(PREFIX + key) }
    }
}
