package pl.gladecki.eatmen

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import pl.gladecki.eatmen.core.State

/** The phone a set-up install runs on: every switch the gate asks for is on. */
fun grantEverything(context: Context) {
    shadowOf(context.getSystemService(NotificationManager::class.java)).apply {
        setNotificationsEnabled(true)
        setNotificationPolicyAccessGranted(true)
    }
    ShadowAlarmManager.setCanScheduleExactAlarms(true)
    shadowOf(context.getSystemService(PowerManager::class.java))
        .setIgnoringBatteryOptimizations(context.packageName, true)
}

class FakeClock(var wall: Long = BASE, var elapsedMs: Long = 1_000) : Clock {
    override fun now(): Long = wall
    override fun elapsed(): Long = elapsedMs
    fun advance(ms: Long) {
        wall += ms
        elapsedMs += ms
    }
    /** A wall-clock change with no real time passing. */
    fun jump(ms: Long) {
        wall += ms
    }
    companion object {
        /** 2026-11-03T10:00:00Z */
        const val BASE = 1_793_700_000_000L
    }
}

class RecordingNotifications : Notifications {
    sealed interface Call {
        data class Countdown(val at: Long) : Call
        data class Alert(val now: Long, val audible: Boolean) : Call
        data class SilentStep(val now: Long) : Call
        data class Test(val now: Long) : Call
        data object Cancel : Call
    }

    val calls = ArrayList<Call>()
    var channelsEnsured = 0
    var allowed = true

    override fun ensureChannels(state: State) {
        channelsEnsured++
    }

    override fun showCountdown(at: Long, now: Long) {
        calls.add(Call.Countdown(at))
    }

    override fun showAlert(state: State, now: Long, audible: Boolean) {
        calls.add(Call.Alert(now, audible))
    }

    override fun showSilentStep(state: State, now: Long) {
        calls.add(Call.SilentStep(now))
    }

    override fun cancel() {
        calls.add(Call.Cancel)
    }

    override fun canNotify(): Boolean = allowed

    override fun test(state: State, now: Long) {
        calls.add(Call.Test(now))
    }

    fun last(): Call? = calls.lastOrNull()
    fun clear() = calls.clear()
}

class RecordingAlarms : Alarms {
    var armedAt: Long? = null
    var cancelCount = 0

    override fun arm(at: Long) {
        armedAt = at
    }

    override fun cancel() {
        armedAt = null
        cancelCount++
    }
}
