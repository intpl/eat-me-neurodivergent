package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ActionsTest {

    private val now = Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()
    private val ids = sequentialIds()

    private fun stateAfter(vararg events: Event): State {
        var state = State()
        events.forEach { state = Reducer.apply(state, it) }
        return state
    }

    @Test
    fun loggingWritesTheMealAndTheReminder() {
        val events = Actions.logMeal(ids, now, State(), now - HOUR_MS, Kind.SNACK)
        assertEquals(2, events.size)
        assertTrue(events[0] is Event.MealLogged)
        val reminder = events[1] as Event.ReminderSet
        assertEquals(now - HOUR_MS + 2 * HOUR_MS, reminder.dueAt)
        assertEquals(Source.SUGGESTED, reminder.source)
    }

    @Test
    fun deletingTheNewestMealMovesTheReminderBack() {
        val base = Actions.logMeal(ids, now, State(), now - 2 * HOUR_MS, Kind.MEAL)
        val state = stateAfter(*base.toTypedArray())
        val newestId = state.meals.single().id
        val events = Actions.deleteMeal(ids, now, state, newestId)
        assertEquals(2, events.size)
        assertTrue(events[1] is Event.ReminderCleared)
        val after = stateAfter(*(base + events).toTypedArray())
        assertTrue(after.meals.isEmpty())
        assertEquals(null, after.plan)
    }

    @Test
    fun deletingTheNewestOfTwoFallsBackToTheOlder() {
        val first = Actions.logMeal(ids, now - 3 * HOUR_MS, State(), now - 3 * HOUR_MS, Kind.SNACK)
        val second = Actions.logMeal(ids, now - HOUR_MS, stateAfter(*first.toTypedArray()), now - HOUR_MS, Kind.MEAL)
        val state = stateAfter(*(first + second).toTypedArray())
        val newestId = state.newest()!!.id
        val events = Actions.deleteMeal(ids, now, state, newestId)
        val after = stateAfter(*(first + second + events).toTypedArray())
        // The older meal's snack gap has already passed, so the next reminder starts a minute out.
        assertEquals(now + MINUTE_MS, Planner.alertAt(after.plan!!))
    }

    @Test
    fun anExplicitReminderIsNotMovedByAnEdit() {
        val base = Actions.logMeal(ids, now, State(), now - HOUR_MS, Kind.MEAL)
        val planned = Actions.planAhead(ids, now, now + 3 * HOUR_MS)
        val state = stateAfter(*(base + planned).toTypedArray())
        val mealId = state.meals.single().id
        val edit = Actions.editMeal(ids, now, state, mealId, now - 2 * HOUR_MS, null)
        assertEquals(1, edit.size)
        val after = stateAfter(*(base + planned + edit).toTypedArray())
        assertEquals(now + 3 * HOUR_MS, Planner.alertAt(after.plan!!))
    }

    @Test
    fun deferringKeepsTheSourceAndTheRhythm() {
        val base = Actions.logMeal(ids, now, State(), now - 4 * HOUR_MS, Kind.MEAL)
        var state = stateAfter(*base.toTypedArray())
        state = Reducer.apply(state, Actions.fire(ids, now, AlertKind.DUE).single())
        val events = Actions.defer(ids, now + MINUTE_MS, state)
        val deferred = stateAfter(*(base + events).toTypedArray())
        assertEquals(Plan.Repeating(now + MINUTE_MS + 10 * MINUTE_MS, Source.SUGGESTED), deferred.plan)
    }

    @Test
    fun anUnknownSettingKeyWritesNothing() {
        assertTrue(Actions.changeSetting(ids, now, "nonsense", "1").isEmpty())
    }
}
