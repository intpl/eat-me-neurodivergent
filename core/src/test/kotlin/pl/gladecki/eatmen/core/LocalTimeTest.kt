package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The device clock is not a steady thing: zones shift, days end, daylight saving jumps. */
class LocalTimeTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    @Test
    fun theClockIsLocalNotUtc() {
        val winter = Instant.parse("2026-01-15T10:00:00Z").toEpochMilli()
        val summer = Instant.parse("2026-07-15T10:00:00Z").toEpochMilli()
        assertEquals("12:00", Text.clock(winter, athens))
        assertEquals("13:00", Text.clock(summer, athens))
    }

    @Test
    fun aMealJustBeforeMidnightBelongsToTheDayThatIsEnding() {
        val justBefore = Instant.parse("2026-11-03T21:50:00Z").toEpochMilli() // 23:50 in Athens
        val justAfter = Instant.parse("2026-11-03T22:10:00Z").toEpochMilli()  // 00:10 in Athens
        val inDay = java.time.Instant.ofEpochMilli(justBefore).atZone(athens).toLocalDate()
        val nextDay = java.time.Instant.ofEpochMilli(justAfter).atZone(athens).toLocalDate()
        assertEquals(1L, nextDay.toEpochDay() - inDay.toEpochDay())
    }

    @Test
    fun theNightWindowFollowsTheLocalClockAcrossTheSpringForward() {
        val settings = Settings()
        // 2026-03-29: 03:00 EET becomes 04:00 EEST, so the wall clock skips an hour.
        assertTrue(Night.isNight(Instant.parse("2026-03-29T00:45:00Z").toEpochMilli(), settings, athens))
        assertTrue(Night.isNight(Instant.parse("2026-03-29T01:15:00Z").toEpochMilli(), settings, athens))
        assertTrue(!Night.isNight(Instant.parse("2026-03-29T10:00:00Z").toEpochMilli(), settings, athens))
    }

    @Test
    fun theNightWindowFollowsTheLocalClockAcrossTheFallBack() {
        val settings = Settings()
        // 2026-10-25: 04:00 EEST becomes 03:00 EET, so 03:30 happens twice.
        assertTrue(Night.isNight(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), settings, athens))
        assertTrue(Night.isNight(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(), settings, athens))
        assertTrue(!Night.isNight(Instant.parse("2026-10-25T08:00:00Z").toEpochMilli(), settings, athens))
    }

    @Test
    fun aNightWindowThatWrapsCoversBothEnds() {
        val settings = Settings(nightStartMinute = 23 * 60, nightEndMinute = 7 * 60)
        assertTrue(Night.isNight(Instant.parse("2026-11-03T21:00:00Z").toEpochMilli(), settings, athens))  // 23:00
        assertTrue(Night.isNight(Instant.parse("2026-11-03T04:59:00Z").toEpochMilli(), settings, athens))  // 06:59
        assertTrue(!Night.isNight(Instant.parse("2026-11-03T05:00:00Z").toEpochMilli(), settings, athens)) // 07:00
        assertTrue(!Night.isNight(Instant.parse("2026-11-03T20:59:00Z").toEpochMilli(), settings, athens)) // 22:59
    }

    @Test
    fun aNightWindowWithNoWidthIsNeverNight() {
        val settings = Settings(nightStartMinute = 60, nightEndMinute = 60)
        assertTrue(!Night.isNight(Instant.parse("2026-11-03T21:00:00Z").toEpochMilli(), settings, athens))
    }
}
