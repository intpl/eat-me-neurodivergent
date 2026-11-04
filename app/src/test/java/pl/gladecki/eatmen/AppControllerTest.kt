package pl.gladecki.eatmen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.gladecki.eatmen.core.EventCodec
import pl.gladecki.eatmen.core.HOUR_MS
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.MINUTE_MS
import pl.gladecki.eatmen.core.Plan
import pl.gladecki.eatmen.core.Planner
import pl.gladecki.eatmen.core.Source
import pl.gladecki.eatmen.core.sequentialIds
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val athens: ZoneId = ZoneId.of("Europe/Athens")
    private lateinit var clock: FakeClock
    private lateinit var notifications: RecordingNotifications
    private lateinit var alarms: RecordingAlarms
    private lateinit var store: FileEventStore
    private lateinit var controller: AppController

    @Before
    fun setUp() {
        clock = FakeClock()
        notifications = RecordingNotifications()
        alarms = RecordingAlarms()
        store = FileEventStore(File(context.filesDir, "events-test-" + System.nanoTime() + ".ndjson"))
        controller = newController()
    }

    private fun newController() = AppController(
        context = context,
        pinnedZone = athens,
        clock = clock,
        store = store,
        ids = sequentialIds(),
        notifications = notifications,
        alarms = alarms,
        appVersion = "2.0.0",
    )

    private fun lines(): List<String> = store.readLines()

    @Test
    fun aFreshInstallSaysNothing() {
        controller.refresh()
        assertEquals(listOf(RecordingNotifications.Call.Cancel), notifications.calls)
        assertNull(alarms.armedAt)
        assertTrue(controller.state.isFresh())
    }

    @Test
    fun loggingAMealShowsOneCountdownAndArmsOneAlarm() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        val expected = FakeClock.BASE + 4 * HOUR_MS
        assertEquals(RecordingNotifications.Call.Countdown(expected), notifications.last())
        assertEquals(expected, alarms.armedAt)
        assertEquals(Plan.Due(expected, Source.SUGGESTED), controller.state.plan)
    }

    @Test
    fun theDueAlarmIsTheOnlyAudibleOneOfARun() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        notifications.clear()
        clock.advance(4 * HOUR_MS)
        controller.onAlarm()
        assertEquals(RecordingNotifications.Call.Alert(clock.wall, true), notifications.last())
        assertEquals(Plan.Repeating(clock.wall + 10 * MINUTE_MS, Source.SUGGESTED), controller.state.plan)
        assertEquals(clock.wall + 10 * MINUTE_MS, alarms.armedAt)

        notifications.clear()
        clock.advance(10 * MINUTE_MS)
        controller.onAlarm()
        assertEquals(RecordingNotifications.Call.Alert(clock.wall, false), notifications.last())
    }

    @Test
    fun notNowPushesTheReminderTenMinutesAndKeepsTheRunSilent() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        clock.advance(4 * HOUR_MS)
        controller.onAlarm()
        notifications.clear()
        clock.advance(MINUTE_MS)
        controller.notNow()
        assertEquals(clock.wall + 10 * MINUTE_MS, alarms.armedAt)
        assertTrue(notifications.calls.none { it is RecordingNotifications.Call.Alert && it.audible })
        assertEquals(Plan.Repeating(clock.wall + 10 * MINUTE_MS, Source.SUGGESTED), controller.state.plan)
    }

    @Test
    fun tomorrowIsSilentForEightHoursAndNeverCountsDown() {
        controller.tomorrow()
        assertEquals(RecordingNotifications.Call.Cancel, notifications.last())
        assertEquals(FakeClock.BASE + 8 * HOUR_MS, alarms.armedAt)
        assertEquals(Plan.TomorrowSilent(FakeClock.BASE + 8 * HOUR_MS, FakeClock.BASE + 12 * HOUR_MS), controller.state.plan)
    }

    @Test
    fun tomorrowSpeaksOnceSilentlyThenOnceAudiblyAMealGapLater() {
        controller.tomorrow()
        clock.advance(8 * HOUR_MS)
        controller.onAlarm()
        assertEquals(RecordingNotifications.Call.SilentStep(clock.wall), notifications.last())
        assertEquals(FakeClock.BASE + 12 * HOUR_MS, alarms.armedAt)

        clock.advance(4 * HOUR_MS)
        controller.onAlarm()
        assertEquals(RecordingNotifications.Call.Alert(clock.wall, true), notifications.last())
    }

    @Test
    fun anExplicitReminderSpeaksAtItsOwnTimeAndNotBefore() {
        val at = FakeClock.BASE + HOUR_MS
        controller.planAhead(at)
        assertEquals(RecordingNotifications.Call.Countdown(at), notifications.last())
        assertEquals(at, alarms.armedAt)

        clock.advance(HOUR_MS - 5 * MINUTE_MS)
        controller.refresh()
        assertEquals(RecordingNotifications.Call.Countdown(at), notifications.last())
        assertEquals(at, alarms.armedAt)

        clock.advance(5 * MINUTE_MS)
        controller.onAlarm()
        assertEquals(RecordingNotifications.Call.Alert(clock.wall, true), notifications.last())
    }

    @Test
    fun theScreenCanTellWhetherThePhoneWillLetItNotify() {
        assertTrue(controller.notificationsEnabled())
        notifications.allowed = false
        assertFalse(controller.notificationsEnabled())
    }

    @Test
    fun theTestNotificationGoesThroughTheChannelTheReminderWould() {
        controller.sendTestNotification()
        assertEquals(RecordingNotifications.Call.Test(clock.wall), notifications.last())
        assertTrue(notifications.channelsEnsured > 0)
    }

    @Test
    fun deletingTheNewestMealMovesTheCountdownBack() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        val mealId = controller.state.meals.single().id
        controller.deleteMeal(mealId)
        assertNull(controller.state.plan)
        assertEquals(RecordingNotifications.Call.Cancel, notifications.last())
        assertNull(alarms.armedAt)
    }

    @Test
    fun editingAMEalMovesTheCountdownWithIt() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        val mealId = controller.state.meals.single().id
        controller.editMeal(mealId, FakeClock.BASE - 2 * HOUR_MS, Kind.SNACK)
        // The snack gap has already passed, so the countdown starts a minute out.
        assertEquals(FakeClock.BASE + MINUTE_MS, alarms.armedAt)
    }

    @Test
    fun openingTheAppAfterALongAbsenceCatchesUpWithOneAlert() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        notifications.clear()
        clock.advance(30 * HOUR_MS)
        val restarted = newController()
        restarted.refresh()
        assertEquals(RecordingNotifications.Call.Alert(clock.wall, true), notifications.last())
        assertTrue(restarted.state.plan is Plan.Repeating)
    }

    @Test
    fun aWallClockJumpIsWrittenDownAndTheAlarmFollowsIt() {
        controller.refresh()
        clock.jump(3 * HOUR_MS)
        controller.refresh()
        val skews = lines().mapNotNull { EventCodec.decode(it) }.filterIsInstance<pl.gladecki.eatmen.core.Event.ClockSkew>()
        assertEquals(1, skews.size)
        assertEquals(3 * HOUR_MS, skews.single().deltaMs)
    }

    @Test
    fun theExportCarriesEverythingAndLeavesATrace() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        val json = controller.exportJson()
        assertTrue(json.contains("\"meal_logged\""))
        assertTrue(json.contains("\"reminder_set\""))
        assertTrue(lines().any { EventCodec.decode(it) is pl.gladecki.eatmen.core.Event.ExportCopied })
    }

    @Test
    fun erasingEverythingLeavesNothingToReplay() {
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        controller.eraseEverything()
        assertTrue(lines().isEmpty())
        assertTrue(controller.state.isFresh())
        assertEquals(RecordingNotifications.Call.Cancel, notifications.last())
        assertNull(alarms.armedAt)
    }

    @Test
    fun theNightWarningOnlyFiresWhenTheAlertLandsInTheNightHours() {
        // 10:00 UTC is 12:00 in Athens; four hours later is 16:00, not night.
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        assertNull(controller.nightAlertAt())
        // Six hours later, at 20:00 UTC = 22:00 Athens, the next alert lands at 02:00.
        controller.logMeal(FakeClock.BASE + 10 * HOUR_MS, Kind.MEAL)
        assertNotNull(controller.nightAlertAt(FakeClock.BASE + 10 * HOUR_MS))
    }

    @Test
    fun theBandIsTheLastDayEndingAtTheClock() {
        // 10:00Z is noon in Athens. A meal four hours back is on the band; one
        // from yesterday at the same time is exactly on its left edge.
        clock.wall = FakeClock.BASE
        controller.logMeal(clock.wall - 4 * HOUR_MS, Kind.MEAL)
        controller.logMeal(clock.wall - 25 * HOUR_MS, Kind.SNACK)

        val band = controller.timeline()
        assertEquals(clock.wall, band.end)
        assertEquals(clock.wall - 24 * HOUR_MS, band.start)
        assertEquals(1, band.meals.size)
        assertEquals(Kind.MEAL, band.meals.single().kind)
    }

    @Test
    fun aSettingChangeIsWrittenAndApplied() {
        controller.changeSetting(pl.gladecki.eatmen.core.Settings.KEY_MEAL_GAP, (3 * HOUR_MS).toString())
        assertEquals(3 * HOUR_MS, controller.settings().mealGapMs)
        controller.logMeal(FakeClock.BASE, Kind.MEAL)
        assertEquals(FakeClock.BASE + 3 * HOUR_MS, Planner.alertAt(controller.state.plan!!))
    }
}
