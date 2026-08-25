package com.mcguidesigner.core.diagnostics

/** How bad an entry is. */
enum class LogLevel(val label: String) {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),

    /** The app died. Recorded before the process goes, read on next launch. */
    CRASH("CRASH"),
}

/**
 * One thing worth telling somebody about.
 *
 * [detail] is where a stack trace goes. Kept separate from [message] so the
 * list can show one line per entry and still hand over the whole trace when
 * it is copied - a log you can read but not copy is only half a log.
 */
data class LogEntry(
    val atMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val detail: String? = null,
) {
    /** `14:32:07` in UTC. */
    val time: String get() = formatTime(atMillis)

    val oneLine: String get() = "[$time] ${level.label} $tag: $message"
}

/**
 * What went wrong, kept where it can be read and copied.
 *
 * The app already had somewhere to put messages - the notification strip - and
 * it was exactly the wrong place for a fault. Those messages are transient by
 * design, so anything that fails scrolls past in a few seconds and is gone; a
 * caught exception's actual text never reached the screen at all, because every
 * failure path turned it into a friendly sentence. That is fine for somebody
 * using the app and useless for somebody testing it, who needs the real message
 * and needs to be able to paste it to whoever will fix it.
 *
 * So this is a plain ring buffer with a formatter on it. Deliberately in
 * `sharedCore` and deliberately free of any platform type: the desktop has the
 * same problem, and a log that only exists on one of them is a log you cannot
 * compare across the two.
 *
 * Not a replacement for logcat - it is what somebody without a cable can get at.
 */
object Diagnostics {

    /**
     * How many entries are kept.
     *
     * Enough to hold a session's worth of faults and short enough that the
     * whole thing pastes into a message without being trimmed. A log nobody can
     * send is not doing its job.
     */
    const val MAX_ENTRIES = 200

    private val entries = ArrayDeque<LogEntry>()

    /** Everything recorded, oldest first. */
    fun snapshot(): List<LogEntry> = entries.toList()

    val size: Int get() = entries.size

    /** How many entries are actual faults, for a badge that means something. */
    fun faultCount(): Int = entries.count { it.level == LogLevel.ERROR || it.level == LogLevel.CRASH }

    fun record(
        atMillis: Long,
        level: LogLevel,
        tag: String,
        message: String,
        detail: String? = null,
    ) {
        entries.addLast(LogEntry(atMillis, level, tag, message, detail))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun clear() = entries.clear()

    /**
     * The whole log as one block of text, ready to paste.
     *
     * [header] carries what the entries cannot know and whoever reads this will
     * ask for first: which build, which device, which Android version. A stack
     * trace without a version number costs a round trip to establish something
     * the sender already knew.
     */
    fun report(header: Map<String, String>, entries: List<LogEntry> = snapshot()): String = buildString {
        appendLine("=== UILabs diagnostics ===")
        header.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("Entries: ${entries.size}")
        appendLine()

        if (entries.isEmpty()) {
            appendLine("(nothing recorded)")
            return@buildString
        }

        entries.forEach { entry ->
            appendLine(entry.oneLine)
            entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                detail.trimEnd().lineSequence().forEach { appendLine("    $it") }
            }
        }
    }
}

/**
 * `14:32:07`, in UTC, without pulling in a date library.
 *
 * UTC rather than local time on purpose: these get pasted into a conversation
 * with somebody in another timezone, and an unlabelled local time is worse than
 * a labelled absolute one. Seconds are the useful resolution - the question is
 * always "what happened just before this", never "what happened this
 * millisecond".
 */
internal fun formatTime(atMillis: Long): String {
    if (atMillis <= 0L) return "--:--:--"
    val totalSeconds = atMillis / 1000
    val hours = ((totalSeconds / 3600) % 24).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return "${pad(hours)}:${pad(minutes)}:${pad(seconds)}"
}

private fun pad(value: Int): String = if (value < 10) "0$value" else value.toString()
