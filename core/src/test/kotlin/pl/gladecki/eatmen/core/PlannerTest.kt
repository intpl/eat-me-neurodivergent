package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PlannerTest {

    private val now = Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()
    private val settings = Settings()
    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    @Test
    fun aMealPlansTheNextOneByItsOwnGap() {
        val meal = Meal("m", now - HOUR_MS, Kind.MEAL)
        assertEquals(now - HOUR_MS + 4 * HOUR_MS, Planner.nextAfterMeal(meal, settings, now))
        val snack = Meal("s", now - HOUR_MS, Kind.SNACK)
        assertEquals(now - HOUR_MS + 2 * HOUR_MS, Planner.nextAfterMeal(snack, settings, now))
    }

    @Test
    fun anAlreadyPastReminderStartsAMinuteOut() {
        val meal = Meal("m", now - 6 * HOUR_MS, Kind.MEAL)
        assertEquals(now + MINUTE_MS, Planner.nextAfterMeal(meal, settings, now))
    }

    @Test
    fun tomorrowIsADurationAndNotAClockTime() {
        val (silenceUntil, normalAt) = Planner.tomorrowSilence(now, settings)
        assertEquals(now + 8 * HOUR_MS, silenceUntil)
        assertEquals(silenceUntil + 4 * HOUR_MS, normalAt)
    }

    @Test
    fun theAlertInstantIsTheMealTimeNotTheWarning() {
        val warning = Plan.Warning(now + 55 * MINUTE_MS, now + 60 * MINUTE_MS)
        assertEquals(now + 60 * MINUTE_MS, Planner.alertAt(warning))
        val due = Plan.Due(now + 60 * MINUTE_MS, Source.SUGGESTED)
        assertEquals(now + 60 * MINUTE_MS, Planner.alertAt(due))
    }

    @Test
    fun missedStepsAreReportedOnceTheInstantHasPassed() {
        assertNull(Planner.overdueStep(Plan.Due(now + 1000, Source.SUGGESTED), now))
        assertEquals(Step.DUE, Planner.overdueStep(Plan.Due(now, Source.SUGGESTED), now))
        assertEquals(Step.REPEAT, Planner.overdueStep(Plan.Repeating(now - 1, Source.SUGGESTED), now))
        val silent = Plan.TomorrowSilent(now + 1000, now + 5000)
        assertNull(Planner.overdueStep(silent, now))
        assertEquals(Step.TOMORROW_SILENT, Planner.overdueStep(silent, now + 2000))
        assertEquals(Step.TOMORROW_DUE, Planner.overdueStep(silent, now + 6000))
    }

    @Test
    fun theWarningStepIsDueAtItsOwnInstant() {
        val plan = Plan.Warning(now + 5 * MINUTE_MS, now + 10 * MINUTE_MS)
        assertNull(Planner.overdueStep(plan, now))
        assertEquals(Step.WARNING, Planner.overdueStep(plan, now + 5 * MINUTE_MS))
        assertEquals(Step.DUE, Planner.overdueStep(plan, now + 10 * MINUTE_MS))
    }

    @Test
    fun onlyTheFirstStepOfARunIsAudible() {
        assertTrue(Planner.isAudible(Step.DUE))
        assertTrue(Planner.isAudible(Step.TOMORROW_DUE))
        assertTrue(!Planner.isAudible(Step.REPEAT))
        assertTrue(!Planner.isAudible(Step.WARNING))
        assertTrue(!Planner.isAudible(Step.TOMORROW_SILENT))
    }

    @Test
    fun nightHoursWrapAroundMidnight() {
        val late = Instant.parse("2026-11-03T23:30:00Z").toEpochMilli()
        val early = Instant.parse("2026-11-03T04:30:00Z").toEpochMilli()
        val noon = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
        assertTrue(Night.isNight(late, settings, athens))
        assertTrue(Night.isNight(early, settings, athens))
        assertTrue(!Night.isNight(noon, settings, athens))
    }
}
