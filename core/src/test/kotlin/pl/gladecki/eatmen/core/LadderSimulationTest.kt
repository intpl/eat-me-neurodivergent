package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Three days of use, one minute at a time, through the same Actions and Reducer
 * the app uses. The invariant after every step is that the plan is never already
 * overdue: that is what a missed hand-over between warning, due, repeat and
 * tomorrow would break.
 */
class LadderSimulationTest {

    @Test
    fun threeDaysOfMealsAlarmsAndDeferralsKeepThePlanHonest() {
        val store = MutableLog()
        val ids = sequentialIds()
        val start = Instant.parse("2026-11-03T06:00:00Z").toEpochMilli()
        var now = start
        var state = State()
        var meals = 0
        var alerts = 0

        fun write(events: List<Event>) {
            events.forEach(store::write)
            state = Reducer.reduce(store.lines)
        }

        // What an alarm would do at this instant.
        fun fireIfDue() {
            val plan = state.plan ?: return
            val step = Planner.overdueStep(plan, now) ?: return
            write(Actions.fire(ids, now, Planner.alertKindOf(step)!!))
            alerts++
        }

        var minute = 0
        while (minute < 3 * 24 * 60) {
            now = start + minute * MINUTE_MS

            fireIfDue()

            // A rough human rhythm: a meal every five hours, a snack every three.
            if (minute > 0 && minute % (5 * 60) == 0) {
                write(Actions.logMeal(ids, now, state, now, Kind.MEAL))
                meals++
            }
            if (minute > 0 && minute % (3 * 60) == 0) {
                write(Actions.logMeal(ids, now, state, now - 20 * MINUTE_MS, Kind.SNACK))
                meals++
            }
            // A tomorrow now and then, and a deferral when it would otherwise nag.
            if (minute % (26 * 60) == 0) write(Actions.tomorrow(ids, now, state.settings))
            if (minute % (97 * 60) == 0) write(Actions.defer(ids, now, state))

            val plan = state.plan
            assertTrue("no plan at minute $minute", plan != null)
            assertNull(
                "a step was left overdue at minute $minute",
                Planner.overdueStep(plan!!, now),
            )
            assertEquals(meals, state.meals.size)

            minute++
        }

        assertTrue("meals were not logged", meals > 20)
        assertTrue("alarms did not fire", alerts > 50)
        assertTrue("the log stayed small: ${store.lines.size} lines", store.lines.size > 100)
        assertEquals(meals, state.meals.size)
        assertEquals(store.lines.size, state.eventCount)
    }
}
