package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReducerTest {

    private val now = Instant.parse("2026-11-03T10:00:00Z").toEpochMilli()
    private val ids = sequentialIds()
    private val settings = Settings()

    private fun log(store: MutableLog, at: Long = now, kind: Kind = Kind.MEAL) {
        Actions.logMeal(ids, at, Reducer.reduce(store.lines), at, kind).forEach(store::write)
    }

    @Test
    fun anEmptyLogIsAFreshInstall() {
        val store = MutableLog()
        val state = Reducer.reduce(store.lines)
        assertTrue(state.isFresh())
        assertNull(state.plan)
        assertTrue(state.meals.isEmpty())
    }

    @Test
    fun loggingAMealSetsTheNextReminder() {
        val store = MutableLog()
        log(store)
        val state = Reducer.reduce(store.lines)
        assertEquals(1, state.meals.size)
        assertEquals(Plan.Due(now + 4 * HOUR_MS, Source.SUGGESTED), state.plan)
    }

    @Test
    fun anExplicitReminderIsDueAtItsOwnTimeAndNotBefore() {
        val store = MutableLog()
        Actions.planAhead(ids, now, now + HOUR_MS).forEach(store::write)
        val plan = Reducer.reduce(store.lines).plan
        assertEquals(Plan.Due(now + HOUR_MS, Source.EXPLICIT), plan)
        assertNull(Reducer.reduce(store.lines).plan?.let { Planner.overdueStep(it, now + 55 * MINUTE_MS) })
    }

    @Test
    fun aWarningFromAnOlderLogReplaysWithoutChangingAnything() {
        val store = MutableLog()
        Actions.planAhead(ids, now, now + HOUR_MS).forEach(store::write)
        Actions.fire(ids, now + 55 * MINUTE_MS, AlertKind.WARNING).forEach(store::write)
        assertEquals(Plan.Due(now + HOUR_MS, Source.EXPLICIT), Reducer.reduce(store.lines).plan)
    }

    @Test
    fun aDueAlertTurnsIntoRepeatingSilence() {
        val store = MutableLog()
        log(store)
        Actions.fire(ids, now + 4 * HOUR_MS, AlertKind.DUE).forEach(store::write)
        val plan = Reducer.reduce(store.lines).plan
        assertEquals(Plan.Repeating(now + 4 * HOUR_MS + 10 * MINUTE_MS, Source.SUGGESTED), plan)
    }

    @Test
    fun repeatsContinueOnTheSameRhythm() {
        val store = MutableLog()
        log(store)
        var t = now + 4 * HOUR_MS
        repeat(3) {
            Actions.fire(ids, t, AlertKind.DUE).forEach(store::write)
            t += 10 * MINUTE_MS
        }
        val state = Reducer.reduce(store.lines)
        assertEquals(Plan.Repeating(t, Source.SUGGESTED), state.plan)
    }

    @Test
    fun tomorrowSetsTheSilentBlockAndTheNormalStepAfterIt() {
        val store = MutableLog()
        Actions.tomorrow(ids, now, settings).forEach(store::write)
        assertEquals(
            Plan.TomorrowSilent(now + 12 * HOUR_MS, now + 16 * HOUR_MS),
            Reducer.reduce(store.lines).plan,
        )
    }

    @Test
    fun theSilentStepHandsOverToTheAudibleOne() {
        val store = MutableLog()
        Actions.tomorrow(ids, now, settings).forEach(store::write)
        Actions.fire(ids, now + 12 * HOUR_MS, AlertKind.SILENT).forEach(store::write)
        assertEquals(Plan.TomorrowDue(now + 16 * HOUR_MS), Reducer.reduce(store.lines).plan)
    }

    @Test
    fun settingsAreReplayedNotStored() {
        val store = MutableLog()
        Actions.changeSetting(ids, now, Settings.KEY_MEAL_GAP, (5 * HOUR_MS).toString()).forEach(store::write)
        Actions.changeSetting(ids, now, Settings.KEY_NIGHT, "22:0-6:30").forEach(store::write)
        Actions.changeSetting(ids, now, Settings.KEY_VIBRATION, "false").forEach(store::write)
        val state = Reducer.reduce(store.lines)
        assertEquals(5 * HOUR_MS, state.settings.mealGapMs)
        assertEquals(22 * 60, state.settings.nightStartMinute)
        assertEquals(6 * 60 + 30, state.settings.nightEndMinute)
        assertEquals(false, state.settings.vibration)
    }

    @Test
    fun aSettingWithABadValueIsIgnored() {
        val store = MutableLog()
        Actions.changeSetting(ids, now, Settings.KEY_MEAL_GAP, "banana").forEach(store::write)
        assertEquals(4 * HOUR_MS, Reducer.reduce(store.lines).settings.mealGapMs)
    }

    @Test
    fun anEditIsANewEventThatPointsAtTheOldOne() {
        val store = MutableLog()
        log(store)
        val mealId = Reducer.reduce(store.lines).meals.single().id
        Actions.editMeal(ids, now + 1000, Reducer.reduce(store.lines), mealId, now - HOUR_MS, Kind.SNACK)
            .forEach(store::write)
        val state = Reducer.reduce(store.lines)
        assertEquals(1, state.meals.size)
        assertEquals(now - HOUR_MS, state.meals.single().eatenAt)
        assertEquals(Kind.SNACK, state.meals.single().kind)
    }

    @Test
    fun aCorruptLineDoesNotLoseTheRestOfTheLog() {
        val store = MutableLog()
        log(store)
        store.raw("{\"id\":\"x\",\"at\":help")
        log(store, now + 1000)
        assertEquals(2, Reducer.reduce(store.lines).meals.size)
    }

    @Test
    fun aLogOfTenThousandEventsStillReplays() {
        val store = MutableLog()
        repeat(2_000) { index ->
            Actions.logMeal(ids, now + index, Reducer.reduce(store.lines), now + index - 30 * MINUTE_MS, Kind.SNACK)
                .forEach(store::write)
        }
        store.write(Event.SettingsChanged("settings", now, Settings.KEY_MEAL_GAP, (5 * HOUR_MS).toString()))
        val state = Reducer.reduce(store.lines)
        assertEquals(4_000 + 1, state.eventCount)
        assertEquals(2_000, state.meals.size)
        assertEquals(5 * HOUR_MS, state.settings.mealGapMs)
    }

    @Test
    fun theEventCountCountsWhatIsInTheFile() {
        val store = MutableLog()
        log(store)
        assertEquals(2, Reducer.reduce(store.lines).eventCount)
    }
}

/** In-memory log for tests, and the same shape the file store implements. */
class MutableLog : EventStore {
    val lines = ArrayList<String>()
    override fun readLines(): List<String> = lines.toList()
    override fun append(line: String) { lines.add(line) }
    override fun clear() { lines.clear() }
    fun raw(line: String) { lines.add(line) }
    fun write(event: Event) { append(EventCodec.encode(event)) }
}
