package com.mcguidesigner.core

import com.mcguidesigner.core.diagnostics.Diagnostics
import com.mcguidesigner.core.diagnostics.LogLevel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsTest {

    @BeforeTest fun setUp() = Diagnostics.clear()

    @AfterTest fun tearDown() = Diagnostics.clear()

    private fun record(level: LogLevel, message: String, detail: String? = null) =
        Diagnostics.record(1_000L, level, "test", message, detail)

    @Test
    fun `the log keeps the newest entries and drops the oldest`() {
        // A log that grows without bound on a phone short on memory is a second
        // bug wearing the first one's coat.
        repeat(Diagnostics.MAX_ENTRIES + 50) { index -> record(LogLevel.INFO, "entry $index") }

        assertEquals(Diagnostics.MAX_ENTRIES, Diagnostics.size)
        val first = Diagnostics.snapshot().first()
        assertEquals("entry 50", first.message, "the oldest 50 should have been dropped")
        assertEquals("entry ${Diagnostics.MAX_ENTRIES + 49}", Diagnostics.snapshot().last().message)
    }

    @Test
    fun `entries come back oldest first`() {
        record(LogLevel.INFO, "one")
        record(LogLevel.INFO, "two")
        record(LogLevel.INFO, "three")
        assertEquals(listOf("one", "two", "three"), Diagnostics.snapshot().map { it.message })
    }

    @Test
    fun `only real faults are counted`() {
        // The badge exists to say "something broke", so a chatty INFO log must
        // not make it look like something did.
        record(LogLevel.INFO, "fine")
        record(LogLevel.WARN, "hmm")
        record(LogLevel.ERROR, "broken")
        record(LogLevel.CRASH, "dead")
        assertEquals(2, Diagnostics.faultCount())
    }

    @Test
    fun `a report carries the header and every entry`() {
        record(LogLevel.ERROR, "could not save", "java.io.IOException: nope\n\tat Foo.bar(Foo.kt:12)")

        val report = Diagnostics.report(mapOf("Version" to "1.9.0", "Device" to "Pixel"))

        assertTrue("Version: 1.9.0" in report, report)
        assertTrue("Device: Pixel" in report, report)
        assertTrue("could not save" in report, report)
        assertTrue("java.io.IOException: nope" in report, "the stack trace has to survive:\n$report")
        assertTrue("at Foo.bar(Foo.kt:12)" in report, "every frame, not just the first:\n$report")
    }

    @Test
    fun `an empty log still produces something worth pasting`() {
        // Otherwise somebody copies a blank string and reports "it gave me
        // nothing", which is a worse conversation than "nothing recorded".
        val report = Diagnostics.report(mapOf("Version" to "1.9.0"))
        assertTrue("Version: 1.9.0" in report)
        assertTrue("nothing recorded" in report, report)
    }

    @Test
    fun `every entry is on its own line with its level and time`() {
        record(LogLevel.ERROR, "first")
        record(LogLevel.WARN, "second")
        val report = Diagnostics.report(emptyMap())

        assertTrue(report.lineSequence().any { it.contains("ERROR") && it.contains("first") }, report)
        assertTrue(report.lineSequence().any { it.contains("WARN") && it.contains("second") }, report)
    }

    @Test
    fun `times are formatted rather than printed as milliseconds`() {
        Diagnostics.record(0L, LogLevel.INFO, "t", "epoch")
        assertEquals("--:--:--", Diagnostics.snapshot().single().time, "zero is no time at all")

        Diagnostics.clear()
        // 01:02:03 UTC on any day.
        Diagnostics.record(((1 * 3600) + (2 * 60) + 3) * 1000L, LogLevel.INFO, "t", "x")
        assertEquals("01:02:03", Diagnostics.snapshot().single().time)
    }
}
