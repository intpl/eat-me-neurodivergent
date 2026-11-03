package pl.gladecki.eatmen

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Meal
import pl.gladecki.eatmen.core.Settings
import pl.gladecki.eatmen.core.State
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationCenterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val athens: ZoneId = ZoneId.of("Europe/Athens")
    private val now = FakeClock.BASE
    private lateinit var center: NotificationCenter
    private lateinit var manager: NotificationManager

    private val state = State(
        meals = listOf(Meal("m1", now - 4 * HOUR_MS, Kind.MEAL)),
        settings = Settings(),
    )

    @Before
    fun setUp() {
        center = NotificationCenter(context, athens)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    private fun posted(): Notification? = shadowOf(manager).allNotifications.lastOrNull()

    @Test
    fun theChannelsAreCreatedWithTheThreeRoles() {
        center.ensureChannels(state)
        val ids = manager.notificationChannels.map { it.id }
        assertTrue(ids.contains(NotificationCenter.CH_COUNTDOWN))
        assertTrue(ids.contains(NotificationCenter.CH_SILENT))
        assertTrue(ids.contains("alert_sound_eat_something_v"))

        val countdown = manager.getNotificationChannel(NotificationCenter.CH_COUNTDOWN)
        assertEquals(NotificationManager.IMPORTANCE_LOW, countdown.importance)
        assertNull(countdown.sound)

        val alert = manager.getNotificationChannel("alert_sound_eat_something_v")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alert.importance)
        assertNotNull(alert.sound)
        assertTrue(alert.shouldVibrate())
    }

    @Test
    fun theCountdownIsASilentChronometer() {
        center.ensureChannels(state)
        val at = now + 2 * HOUR_MS
        center.showCountdown(at, now)
        val notification = posted()!!
        assertEquals(NotificationCenter.CH_COUNTDOWN, notification.channelId)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(at, notification.`when`)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun theAlertStatesTheTimeTheGapAndTwoActions() {
        center.ensureChannels(state)
        center.showAlert(state, now, audible = true)
        val notification = posted()!!
        assertEquals("It's 12:00.", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("It's been 4h.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(2, notification.actions.size)
        assertEquals("Ate", notification.actions[0].title.toString())
        assertEquals("Not now", notification.actions[1].title.toString())
    }

    @Test
    fun aRepeatGoesToTheSilentChannel() {
        center.ensureChannels(state)
        center.showAlert(state, now, audible = false)
        assertEquals(NotificationCenter.CH_SILENT, posted()!!.channelId)
    }

    @Test
    fun theWarningIsSilentAndNamesTheTime() {
        center.ensureChannels(state)
        val due = now + 5 * MINUTE_MS
        center.showWarning(due)
        val notification = posted()!!
        assertEquals(NotificationCenter.CH_SILENT, notification.channelId)
        assertEquals("12:05 in 5 minutes", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun theTomorrowStepIsSilentAndSaysTheLastMeal() {
        center.ensureChannels(state)
        center.showSilentStep(state, now)
        val notification = posted()!!
        assertEquals(NotificationCenter.CH_SILENT, notification.channelId)
        assertEquals("Last meal 08:00.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(0, notification.actions?.size ?: 0)
    }

    @Test
    fun aChangedSoundBecomesANewChannelAndTheOldOneGoes() {
        center.ensureChannels(state)
        assertTrue(manager.notificationChannels.any { it.id == "alert_sound_eat_something_v" })
        center.ensureChannels(state.copy(settings = Settings(soundId = "it_is_time")))
        val ids = manager.notificationChannels.map { it.id }
        assertTrue(ids.contains("alert_sound_it_is_time_v"))
        assertTrue(!ids.contains("alert_sound_eat_something_v"))
    }

    @Test
    fun turningVibrationOffBecomesANewChannel() {
        center.ensureChannels(state)
        center.ensureChannels(state.copy(settings = Settings(vibration = false)))
        val quiet = manager.getNotificationChannel("alert_sound_eat_something_q")
        assertNotNull(quiet)
        assertTrue(!quiet.shouldVibrate())
    }

    @Test
    fun cancelRemovesTheOneNotification() {
        center.ensureChannels(state)
        center.showCountdown(now + HOUR_MS, now)
        assertEquals(1, shadowOf(manager).allNotifications.size)
        center.cancel()
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun thereIsOnlyEverOneNotificationId() {
        center.ensureChannels(state)
        center.showCountdown(now + HOUR_MS, now)
        center.showAlert(state, now, audible = true)
        center.showSilentStep(state, now)
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val MINUTE_MS = 60_000L
    }
}
