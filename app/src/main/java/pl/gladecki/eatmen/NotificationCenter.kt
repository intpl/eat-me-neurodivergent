package pl.gladecki.eatmen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.gladecki.eatmen.core.State
import pl.gladecki.eatmen.core.Text
import java.time.ZoneId

/**
 * One notification id for the app's whole life. The same notification is the
 * countdown, the warning, the alert and the silent repeat; only its channel and
 * its words change.
 */
class NotificationCenter(
    private val context: Context,
    private val zone: ZoneId,
) : Notifications {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun ensureChannels(state: State) {
        val countdown = NotificationChannel(CH_COUNTDOWN, context.getString(R.string.channel_countdown), NotificationManager.IMPORTANCE_LOW)
        countdown.setSound(null, null)
        countdown.enableVibration(false)
        countdown.setShowBadge(false)
        countdown.setDescription(context.getString(R.string.channel_countdown_description))
        manager.createNotificationChannel(countdown)

        val silent = NotificationChannel(CH_SILENT, context.getString(R.string.channel_silent), NotificationManager.IMPORTANCE_HIGH)
        silent.setSound(null, null)
        silent.enableVibration(false)
        silent.setShowBadge(true)
        silent.setDescription(context.getString(R.string.channel_silent_description))
        manager.createNotificationChannel(silent)

        val alertId = alertChannelId(state)
        val alert = NotificationChannel(alertId, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
        alert.setSound(soundUri(state), audioAttributes())
        alert.enableVibration(state.settings.vibration)
        if (state.settings.vibration) {
            alert.vibrationPattern = longArrayOf(0, 250, 150, 250)
        }
        alert.setShowBadge(true)
        alert.setDescription(context.getString(R.string.channel_alert_description))
        manager.createNotificationChannel(alert)

        // A channel's sound is fixed once it exists, so a changed sound or a
        // changed vibration toggle becomes a new channel and the old ones go.
        manager.notificationChannels
            .map { it.id }
            .filter { it.startsWith(ALERT_PREFIX) && it != alertId }
            .forEach { manager.deleteNotificationChannel(it) }
    }

    override fun showCountdown(at: Long, now: Long) {
        val builder = base(CH_COUNTDOWN)
            .setContentTitle(context.getString(R.string.countdown_title))
            .setContentText(Text.clock(at, zone))
            .setWhen(at)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
        notify(builder)
    }

    override fun showWarning(dueAt: Long) {
        val builder = base(CH_SILENT)
            .setContentTitle(context.getString(R.string.warning_title))
            .setContentText(Text.warningText(dueAt, zone))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        notify(builder)
    }

    override fun showAlert(state: State, now: Long, audible: Boolean) {
        val channel = if (!audible || doNotDisturbActive()) CH_SILENT else alertChannelId(state)
        val builder = base(channel)
            .setContentTitle(Text.alertTitle(now, zone))
            .setContentText(Text.alertBody(now, state.lastMeal()))
            .setOngoing(true)
            .setOnlyAlertOnce(!audible)
            .setShowWhen(false)
            .addAction(0, context.getString(R.string.notification_ate), atePendingIntent())
            .addAction(0, context.getString(R.string.action_not_now), deferPendingIntent())
        notify(builder)
    }

    override fun showSilentStep(state: State, now: Long) {
        val builder = base(CH_SILENT)
            .setContentTitle(Text.tomorrowSilentTitle(now, zone))
            .setContentText(Text.tomorrowSilentBody(state.lastMeal(), zone))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        notify(builder)
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun base(channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.accent))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent())
            .setAutoCancel(false)

    private fun notify(builder: NotificationCompat.Builder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // No notification permission: the timing still works, the reminder cannot be posted.
        }
    }

    private fun openPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun atePendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_ATE,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_ATE)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun deferPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_DEFER,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DEFER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun soundUri(state: State): Uri = Uri.parse("android.resource://${context.packageName}/${soundRes(state.settings.soundId)}")

    private fun soundRes(soundId: String): Int = when (soundId) {
        "food_oclock" -> R.raw.food_oclock
        "it_is_time" -> R.raw.it_is_time
        else -> R.raw.eat_something
    }

    private fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun alertChannelId(state: State): String =
        ALERT_PREFIX + state.settings.soundId + if (state.settings.vibration) "_v" else "_q"

    private fun doNotDisturbActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            if (!manager.isNotificationPolicyAccessGranted) return false
            manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CH_COUNTDOWN = "countdown"
        const val CH_SILENT = "alert_silent"
        const val ALERT_PREFIX = "alert_sound_"

        private const val REQUEST_OPEN = 10
        private const val REQUEST_ATE = 11
        private const val REQUEST_DEFER = 12
    }
}
