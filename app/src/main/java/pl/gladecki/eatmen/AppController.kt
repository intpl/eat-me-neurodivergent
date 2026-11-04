package pl.gladecki.eatmen

import android.content.Context
import pl.gladecki.eatmen.core.Actions
import pl.gladecki.eatmen.core.Event
import pl.gladecki.eatmen.core.EventCodec
import pl.gladecki.eatmen.core.EventStore
import pl.gladecki.eatmen.core.Export
import pl.gladecki.eatmen.core.HOUR_MS
import pl.gladecki.eatmen.core.IdSource
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Meal
import pl.gladecki.eatmen.core.Night
import pl.gladecki.eatmen.core.Plan
import pl.gladecki.eatmen.core.Planner
import pl.gladecki.eatmen.core.RandomIds
import pl.gladecki.eatmen.core.Reducer
import pl.gladecki.eatmen.core.Settings
import pl.gladecki.eatmen.core.State
import pl.gladecki.eatmen.core.Step
import pl.gladecki.eatmen.core.Timeline
import pl.gladecki.eatmen.core.TimelineBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * The one place that writes events, posts notifications and arms alarms. The
 * activities only call this and render [state].
 */
class AppController(
    context: Context,
    private val pinnedZone: ZoneId? = null,
    private val clock: Clock = RealClock,
    private val store: EventStore = FileEventStore(File(context.filesDir, "events.ndjson")),
    private val ids: IdSource = RandomIds,
    private val notifications: Notifications = NotificationCenter(context, pinnedZone),
    private val alarms: Alarms = AlarmScheduler(context),
    private val appVersion: String = BuildConfig.VERSION_NAME,
) {

    private val prefs = context.getSharedPreferences(PREFS_CLOCK, Context.MODE_PRIVATE)

    /** The phone's zone, read on every use so a flight does not need a restart. */
    val zone: ZoneId get() = pinnedZone ?: ZoneId.systemDefault()

    var state: State = Reducer.reduce(store.readLines())
        private set

    // ---- what the screens read -------------------------------------------------

    fun lastMeal(): Meal? = state.lastMeal()

    /** The last 24 hours, as the band on the front screen draws them. */
    fun timeline(now: Long = clock.now()): Timeline =
        TimelineBuilder.build(now, state.liveMeals(), state.settings, zone)

    /** Meals from the last 24 hours, newest first. The only history there is. */
    fun editableMeals(now: Long = clock.now()): List<Meal> =
        state.liveMeals()
            .filter { now - it.eatenAt <= 24 * HOUR_MS }
            .sortedByDescending { it.eatenAt }

    fun settings(): Settings = state.settings

    /** Whether the phone lets this app post anything at all. */
    fun notificationsEnabled(): Boolean = notifications.canNotify()

    /** The settings screen's proof that the permission, the channel and the sound all work. */
    fun sendTestNotification(now: Long = clock.now()) {
        reload()
        notifications.ensureChannels(state)
        notifications.test(state, now)
    }

    /** The alert instant, when it falls inside the night hours. */
    fun nightAlertAt(now: Long = clock.now()): Long? {
        val plan = state.plan ?: return null
        val at = Planner.alertAt(plan)
        if (at <= now) return null
        return if (Night.isNight(at, state.settings, zone)) at else null
    }

    // ---- the clock, the ladder and the alarm -----------------------------------

    fun refresh(now: Long = clock.now()) {
        reload()
        checkClockSkew(now)
        notifications.ensureChannels(state)

        val plan = state.plan
        val step = plan?.let { Planner.overdueStep(it, now) }
        if (step != null) {
            val kind = Planner.alertKindOf(step) ?: return
            write(Actions.fire(ids, now, kind))
            reload()
            when (step) {
                Step.DUE, Step.TOMORROW_DUE -> notifications.showAlert(state, now, audible = true)
                Step.REPEAT -> notifications.showAlert(state, now, audible = false)
                Step.TOMORROW_SILENT -> notifications.showSilentStep(state, now)
            }
        } else {
            render(now)
        }
        armCurrent()
    }

    /** The alarm went off. A wake-up a few milliseconds early still counts. */
    fun onAlarm(now: Long = clock.now()) {
        reload()
        val at = state.plan?.at
        refresh(if (at != null && at > now) at else now)
    }

    fun notNow(now: Long = clock.now()) {
        reload()
        write(Actions.defer(ids, now, state))
        refresh(now)
    }

    // ---- the actions behind the two buttons ------------------------------------

    /** Opening the app is an action like any other, so the log keeps it. */
    fun noteAppOpened(now: Long = clock.now()) {
        write(Actions.appOpened(ids, now))
        reload()
    }

    fun logMeal(eatenAt: Long, kind: Kind, now: Long = clock.now()) {
        reload()
        write(Actions.logMeal(ids, now, state, eatenAt, kind))
        refresh(now)
    }

    fun planAhead(at: Long, now: Long = clock.now()) {
        reload()
        write(Actions.planAhead(ids, now, at))
        refresh(now)
    }

    fun tomorrow(now: Long = clock.now()) {
        reload()
        write(Actions.tomorrow(ids, now, state.settings))
        refresh(now)
    }

    fun editMeal(mealId: String, eatenAt: Long?, kind: Kind?, now: Long = clock.now()) {
        reload()
        write(Actions.editMeal(ids, now, state, mealId, eatenAt, kind))
        refresh(now)
    }

    fun deleteMeal(mealId: String, now: Long = clock.now()) {
        reload()
        write(Actions.deleteMeal(ids, now, state, mealId))
        refresh(now)
    }

    fun changeSetting(key: String, value: String, now: Long = clock.now()) {
        reload()
        write(Actions.changeSetting(ids, now, key, value))
        refresh(now)
    }

    fun settingValue(key: String): String = state.settings.value(key)

    // ---- export and erase ------------------------------------------------------

    fun exportJson(now: Long = clock.now()): String {
        reload()
        val events = store.readLines().mapNotNull { EventCodec.decode(it) }
        val text = Export.text(events, zone, now, appVersion)
        write(Actions.exportCopied(ids, now, events.size))
        reload()
        return text
    }

    fun eraseEverything(now: Long = clock.now()) {
        store.clear()
        notifications.cancel()
        alarms.cancel()
        reload()
    }

    // ---- private ---------------------------------------------------------------

    private fun render(now: Long) {
        when (val plan = state.plan) {
            null -> notifications.cancel()
            is Plan.Due ->
                if (now < plan.at) notifications.showCountdown(plan.at, now)
                else notifications.showAlert(state, now, audible = false)
            is Plan.Repeating -> notifications.showAlert(state, now, audible = false)
            is Plan.TomorrowSilent ->
                if (now < plan.at) notifications.cancel() else notifications.showSilentStep(state, now)
            is Plan.TomorrowDue ->
                if (now < plan.at) notifications.showCountdown(plan.at, now)
                else notifications.showAlert(state, now, audible = false)
        }
    }

    private fun armCurrent() {
        val plan = state.plan
        if (plan == null) alarms.cancel() else alarms.arm(plan.at)
    }

    private fun write(events: List<Event>) {
        for (event in events) store.append(EventCodec.encode(event))
    }

    private fun reload() {
        state = Reducer.reduce(store.readLines())
    }

    private fun checkClockSkew(now: Long) {
        val wall = prefs.getLong(KEY_WALL, 0L)
        val elapsed = prefs.getLong(KEY_ELAPSED, 0L)
        if (wall != 0L) {
            val drift = (now - wall) - (clock.elapsed() - elapsed)
            if (abs(drift) > SKEW_LIMIT_MS) {
                write(Actions.clockSkew(ids, now, drift))
                reload()
            }
        }
        prefs.edit().putLong(KEY_WALL, now).putLong(KEY_ELAPSED, clock.elapsed()).apply()
    }

    companion object {
        private const val PREFS_CLOCK = "clock_check"
        private const val KEY_WALL = "wall"
        private const val KEY_ELAPSED = "elapsed"
        const val SKEW_LIMIT_MS = 2 * 60_000L
    }
}
