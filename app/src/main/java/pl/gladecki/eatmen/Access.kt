package pl.gladecki.eatmen

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The four things the phone has to allow before the app will do anything.
 *
 * They are not equivalent: notifications decide whether the app can speak at
 * all, exact reminders whether it speaks at the time it was told to,
 * do-not-disturb access whether it can stay silent while the phone is, and the
 * battery exemption whether the phone lets its alarms run at all.
 */
enum class Access {
    NOTIFICATIONS,
    EXACT_ALARMS,
    DO_NOT_DISTURB,
    BATTERY,
}

/** Reads the phone's switches. Nothing is cached: a change is seen on the next resume. */
object Accesses {

    fun missing(context: Context): List<Access> = Access.values().filter { !isGranted(context, it) }

    fun isGranted(context: Context, access: Access): Boolean = when (access) {
        Access.NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        Access.EXACT_ALARMS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Access.DO_NOT_DISTURB ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        Access.BATTERY -> batteryUnrestricted(context)
    }

    /** Some phones throw here instead of answering; such a phone is taken at its word. */
    private fun batteryUnrestricted(context: Context): Boolean = try {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        true
    }

    /** The phone's own page for the switch behind [access], or null where there is none. */
    fun settingsIntent(context: Context, access: Access): Intent? = when (access) {
        Access.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        Access.EXACT_ALARMS ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + context.packageName),
            )
        Access.DO_NOT_DISTURB -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        Access.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName),
        )
    }
}
