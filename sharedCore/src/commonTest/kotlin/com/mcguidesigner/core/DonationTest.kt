package com.mcguidesigner.core

import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.core.support.SupportedApps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The payment details.
 *
 * Worth testing precisely because it is static data: a typo in an account
 * number does not fail to compile, does not throw, and does not show up in any
 * other test - it just quietly sends someone's money to nobody. These pin the
 * shape of every field a donor is asked to read or copy.
 */
class DonationTest {

    @Test
    fun `every detail shown is filled in`() {
        assertTrue(Donation.details.isNotEmpty())
        Donation.details.forEach { detail ->
            assertTrue(detail.label.isNotBlank(), "a detail has no label")
            assertTrue(detail.value.isNotBlank(), "${detail.label} has no value")
        }
    }

    @Test
    fun `the account numbers are digits and spacing, nothing else`() {
        // A stray letter here is a number nobody can pay into.
        listOf(Donation.MOBILE_NUMBER, Donation.MAYA_BANK_NUMBER).forEach { number ->
            assertTrue(
                number.all { it.isDigit() || it == ' ' || it == '-' },
                "'$number' has something in it that is not part of a number",
            )
            assertTrue(number.count { it.isDigit() } >= 11, "'$number' is too short to be real")
        }
    }

    @Test
    fun `the Maya handle is a handle`() {
        assertTrue(Donation.MAYA_USERNAME.startsWith("@"), "a username without its @ is ambiguous")
        assertTrue(Donation.MAYA_USERNAME.length > 1)
        // The qualifier matters: the handle is worthless in any other app, and
        // omitting that is how someone types it into GCash and loses the money.
        assertTrue(Donation.MAYA_USERNAME_NOTE.isNotBlank())
    }

    @Test
    fun `the QR is asked for by one name and saved under another`() {
        // The asset name is an implementation detail of the two builds; the
        // save name is what lands in someone's Downloads folder and has to say
        // what it is.
        assertTrue(Donation.QR_ASSET_NAME.endsWith(".png"))
        assertTrue(Donation.QR_FILE_NAME.endsWith(".png"))
        assertTrue(
            Donation.QR_FILE_NAME.contains("donate", ignoreCase = true),
            "a file called '${Donation.QR_FILE_NAME}' means nothing a month later",
        )
    }

    @Test
    fun `the page has something to say`() {
        assertTrue(Donation.HEADLINE.isNotBlank())
        assertTrue(Donation.MESSAGE.isNotEmpty())
        Donation.MESSAGE.forEach { assertTrue(it.isNotBlank()) }
    }
}

/**
 * The list of apps that can pay the code.
 *
 * It is long, hand-transcribed and grouped, which is three good ways to end up
 * with a duplicate or an empty bullet.
 */
class SupportedAppsTest {

    @Test
    fun `every group has a title and at least one app`() {
        assertTrue(SupportedApps.groups.isNotEmpty())
        SupportedApps.groups.forEach { group ->
            assertTrue(group.title.isNotBlank(), "a group has no title")
            assertTrue(group.apps.isNotEmpty(), "${group.title} lists nothing")
            group.apps.forEach { app ->
                assertTrue(app.isNotBlank(), "${group.title} has a blank entry")
                assertEquals(app.trim(), app, "'$app' has stray whitespace")
            }
        }
    }

    @Test
    fun `no app is listed twice`() {
        val all = SupportedApps.groups.flatMap { it.apps }
        val duplicates = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicates.isEmpty(), "listed more than once: $duplicates")
    }

    @Test
    fun `no group title is used twice`() {
        val titles = SupportedApps.groups.map { it.title }
        assertEquals(titles.distinct().size, titles.size, "duplicate group titles in $titles")
    }

    @Test
    fun `the count shown on the collapsed header is the real one`() {
        assertEquals(SupportedApps.groups.sumOf { it.apps.size }, SupportedApps.count)
    }
}
