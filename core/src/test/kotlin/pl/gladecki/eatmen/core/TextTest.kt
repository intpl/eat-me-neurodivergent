package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TextTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")
    private val now = Instant.parse("2026-11-03T15:05:00Z").toEpochMilli() // 17:05 in Athens

    @Test
    fun clockIsTwentyFourHour() {
        assertEquals("17:05", Text.clock(now, athens))
    }

    @Test
    fun shortDurationsReadLikeTime() {
        assertEquals("2h 14m", Text.short(2 * HOUR_MS + 14 * MINUTE_MS))
        assertEquals("2h", Text.short(2 * HOUR_MS))
        assertEquals("45m", Text.short(45 * MINUTE_MS))
        assertEquals("0m", Text.short(-5000))
    }

    @Test
    fun spelledDurationsAreForReadingNotScanning() {
        assertEquals("9 hours", Text.spelled(9 * HOUR_MS))
        assertEquals("1 hour", Text.spelled(HOUR_MS))
        assertEquals("45 minutes", Text.spelled(45 * MINUTE_MS))
        assertEquals("1 minute", Text.spelled(30_000))
    }

    @Test
    fun theLastLineIsTimeKindAndAge() {
        val meal = Meal("m", now - 2 * HOUR_MS, Kind.MEAL)
        assertEquals("15:05 · meal · 2h ago", Text.lastLine(now, meal, athens))
        assertEquals("nothing logged", Text.lastLine(now, null, athens))
    }

    @Test
    fun theNextLineCountsToTheNextMomentTheAppSpeaks() {
        val due = Plan.Due(now + 2 * HOUR_MS, Source.SUGGESTED)
        assertEquals("19:05 · in 2h", Text.nextLine(now, due, athens))
        assertEquals("no reminder", Text.nextLine(now, null, athens))
        val quiet = Plan.TomorrowSilent(now + 8 * HOUR_MS, now + 12 * HOUR_MS)
        assertEquals("silent until 01:05", Text.nextLine(now, quiet, athens))
    }

    @Test
    fun anOverdueReminderReadsNow() {
        val late = Plan.Due(now - HOUR_MS, Source.SUGGESTED)
        assertEquals("16:05 · now", Text.nextLine(now, late, athens))
    }

    @Test
    fun theAlertStatesTheTimeAndTheGapOnly() {
        assertEquals("It's 17:05.", Text.alertTitle(now, athens))
        assertEquals("It's been 4h 30m.", Text.alertBody(now, Meal("m", now - 4 * HOUR_MS - 30 * MINUTE_MS, Kind.MEAL)))
        assertEquals("No meal logged yet.", Text.alertBody(now, null))
    }

    @Test
    fun theStaleLineAppearsOnlyAfterEightHours() {
        assertNull(Text.staleLine(now, Meal("m", now - 7 * HOUR_MS, Kind.MEAL)))
        assertEquals("😕 9 hours since your last meal", Text.staleLine(now, Meal("m", now - 9 * HOUR_MS, Kind.MEAL)))
        assertNull(Text.staleLine(now, null))
    }

    @Test
    fun noBannedWordEverReachesTheScreen() {
        val banned = listOf("missed", "skipped", "overdue", "late", "failed", "streak", "cheat", "should", "need to", "don't forget")
        val samples = listOf(
            Text.lastLine(now, Meal("m", now - 2 * HOUR_MS, Kind.MEAL), athens),
            Text.lastLine(now, null, athens),
            Text.nextLine(now, Plan.Due(now + HOUR_MS, Source.SUGGESTED), athens),
            Text.nextLine(now, Plan.Repeating(now - 1000, Source.SUGGESTED), athens),
            Text.nextLine(now, Plan.TomorrowSilent(now + HOUR_MS, now + 5 * HOUR_MS), athens),
            Text.alertTitle(now, athens),
            Text.alertBody(now, Meal("m", now - 3 * HOUR_MS, Kind.SNACK)),
            Text.warningText(now + 5 * MINUTE_MS, athens),
            Text.tomorrowSilentTitle(now, athens),
            Text.tomorrowSilentBody(Meal("m", now - 4 * HOUR_MS, Kind.MEAL), athens),
            Text.staleLine(now, Meal("m", now - 9 * HOUR_MS, Kind.MEAL))!!,
            Text.nightWarning("03:00"),
        )
        for (line in samples) {
            val lower = line.lowercase()
            for (word in banned) {
                assertTrue("banned word '$word' in '$line'", !lower.contains(word))
            }
        }
    }
}
