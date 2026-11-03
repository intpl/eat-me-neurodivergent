package pl.gladecki.eatmen

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.gladecki.eatmen.core.HOUR_MS
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.MINUTE_MS
import pl.gladecki.eatmen.core.Plan
import pl.gladecki.eatmen.core.Source

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller: AppController get() = EatMeApp.controller(context)
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun granted() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun aRebootRearmsTheAlarmFromTheLog() {
        controller.logMeal(System.currentTimeMillis(), Kind.MEAL)
        val armedBefore = shadowOf(alarms).nextScheduledAlarm
        assertNotNull(armedBefore)

        // A reboot drops every alarm in the system, then the receiver has to put it back.
        shadowOf(alarms).scheduledAlarms.clear()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val armedAfter = shadowOf(alarms).nextScheduledAlarm
        assertNotNull(armedAfter)
        assertEquals(armedBefore!!.triggerAtTime, armedAfter!!.triggerAtTime)
    }

    @Test
    fun aRebootRestoresTheCountdownNotification() {
        controller.logMeal(System.currentTimeMillis(), Kind.MEAL)
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
        assertEquals(0, shadowOf(notifications).allNotifications.size)

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(1, shadowOf(notifications).allNotifications.size)
        assertEquals(NotificationCenter.CH_COUNTDOWN, shadowOf(notifications).allNotifications.single().channelId)
    }

    @Test
    fun aPackageUpdateAlsoReArms() {
        controller.logMeal(System.currentTimeMillis(), Kind.MEAL)
        shadowOf(alarms).scheduledAlarms.clear()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertNotNull(shadowOf(alarms).nextScheduledAlarm)
    }

    @Test
    fun theAlarmReceiverLeavesAWorkingPlanAndOneNotification() {
        val now = System.currentTimeMillis()
        controller.logMeal(now - 6 * HOUR_MS, Kind.MEAL)   // already overdue
        ReminderReceiver().onReceive(context, Intent(context, ReminderReceiver::class.java))

        val plan = controller.state.plan
        assertTrue(plan is Plan.Repeating)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
        assertNotNull(shadowOf(alarms).nextScheduledAlarm)
    }

    @Test
    fun notNowFromTheNotificationPushesTheAlertTenMinutes() {
        val now = System.currentTimeMillis()
        controller.logMeal(now, Kind.MEAL)
        assertEquals(Plan.Due(now + 4 * HOUR_MS, Source.SUGGESTED), controller.state.plan)

        ReminderReceiver().onReceive(
            context,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DEFER),
        )

        val plan = controller.state.plan as Plan.Repeating
        assertTrue(plan.at > System.currentTimeMillis())
        assertTrue(plan.at <= System.currentTimeMillis() + 10 * MINUTE_MS + 1_000)
    }

    @Test
    fun theAlertActionsCarryTheRightIntents() {
        val center = NotificationCenter(context, java.time.ZoneId.of("Europe/Athens"))
        val state = controller.state.copy(
            meals = listOf(pl.gladecki.eatmen.core.Meal("m", System.currentTimeMillis() - HOUR_MS, Kind.MEAL)),
        )
        center.ensureChannels(state)
        center.showAlert(state, System.currentTimeMillis(), audible = true)
        val notification = shadowOf(notifications).allNotifications.single()

        val ate = shadowOf(notification.actions[0].actionIntent)
        assertEquals(MainActivity::class.java.name, ate.savedIntent.component?.className)
        assertEquals(MainActivity.ACTION_ATE, ate.savedIntent.getStringExtra(MainActivity.EXTRA_ACTION))

        val notNow = shadowOf(notification.actions[1].actionIntent)
        assertEquals(ReminderReceiver::class.java.name, notNow.savedIntent.component?.className)
        assertEquals(ReminderReceiver.ACTION_DEFER, notNow.savedIntent.action)
    }

    @Test
    fun theCountdownOpensTheApp() {
        val center = NotificationCenter(context, java.time.ZoneId.of("Europe/Athens"))
        val state = controller.state
        center.ensureChannels(state)
        center.showCountdown(System.currentTimeMillis() + HOUR_MS, System.currentTimeMillis())
        val notification = shadowOf(notifications).allNotifications.single()
        val content = shadowOf(notification.contentIntent)
        assertEquals(MainActivity::class.java.name, content.savedIntent.component?.className)
    }

    @Test
    fun withoutNotificationPermissionNothingIsPostedAndNothingThrows() {
        shadowOf(notifications).setNotificationsEnabled(false)
        try {
            val center = NotificationCenter(context, java.time.ZoneId.of("Europe/Athens"))
            controller.logMeal(System.currentTimeMillis(), Kind.MEAL)
            center.ensureChannels(controller.state)
            center.showAlert(controller.state, System.currentTimeMillis(), audible = true)
            assertEquals(0, shadowOf(notifications).allNotifications.size)
            assertNotNull(controller.state.plan)
        } finally {
            shadowOf(notifications).setNotificationsEnabled(true)
        }
    }

    @Test
    fun theAlertIsSilentWhenThePhoneIsInDoNotDisturb() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val shadowClass = Class.forName("org.robolectric.shadows.ShadowNotificationManager")
        val grantField = shadowClass.getDeclaredField("isNotificationPolicyAccessGranted")
        val filterField = shadowClass.getDeclaredField("currentInterruptionFilter")
        grantField.isAccessible = true
        filterField.isAccessible = true
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        filterField.setInt(null, NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        try {
            val center = NotificationCenter(context, java.time.ZoneId.of("Europe/Athens"))
            val state = controller.state.copy(
                meals = listOf(pl.gladecki.eatmen.core.Meal("m", System.currentTimeMillis() - HOUR_MS, Kind.MEAL)),
            )
            center.ensureChannels(state)
            center.showAlert(state, System.currentTimeMillis(), audible = true)
            val notification = shadowOf(manager).allNotifications.single()
            assertEquals(NotificationCenter.CH_SILENT, notification.channelId)
            assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        } finally {
            filterField.setInt(null, NotificationManager.INTERRUPTION_FILTER_ALL)
            shadowOf(manager).setNotificationPolicyAccessGranted(false)
            grantField.setBoolean(null, false)
        }
    }
}
