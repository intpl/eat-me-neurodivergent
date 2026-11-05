package pl.gladecki.eatmen

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import pl.gladecki.eatmen.databinding.ActivityAccessBinding

/** The gate: what it says, where each row goes, and when it lets the app through. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications
        get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun phoneWithholdsEverything() {
        notifications.setNotificationsEnabled(false)
        notifications.setNotificationPolicyAccessGranted(false)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    private fun gate() = Robolectric.buildActivity(AccessActivity::class.java).setup().get()

    private fun rows(activity: AccessActivity): List<Pair<String, String>> {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val binding = ActivityAccessBinding.bind(root)
        return (0 until binding.accessList.childCount).map { index ->
            val row = binding.accessList.getChildAt(index)
            row.findViewById<TextView>(R.id.settingTitle).text.toString() to
                row.findViewById<TextView>(R.id.settingValue).text.toString()
        }
    }

    @Test
    fun theGateNamesEveryWithheldSwitch() {
        val titles = rows(gate()).map { it.first }
        assertTrue(titles.contains("notifications are off for this app"))
        assertTrue(titles.contains("exact reminders are off, times can drift"))
        assertTrue(titles.contains("do-not-disturb access is off, a reminder may sound during it"))
        assertTrue(titles.contains("Check again"))
    }

    @Test
    fun theGateDropsTheSwitchesThePhoneAllows() {
        notifications.setNotificationsEnabled(true)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals(
            listOf("do-not-disturb access is off, a reminder may sound during it", "Check again"),
            rows(gate()).map { it.first },
        )
    }

    @Test
    fun eachRowOpensThePhonesOwnPage() {
        val activity = gate()
        val list = (0 until 3).map { index ->
            val binding = ActivityAccessBinding.bind(
                activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0),
            )
            binding.accessList.getChildAt(index)
        }

        list[0].performClick()
        assertEquals(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            shadowOf(activity).nextStartedActivity.action,
        )
        list[1].performClick()
        assertEquals(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            shadowOf(activity).nextStartedActivity.action,
        )
        list[2].performClick()
        assertEquals(
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            shadowOf(activity).nextStartedActivity.action,
        )
    }

    @Test
    fun theGateLetsTheAppThroughWhenNothingIsWithheld() {
        val controller = Robolectric.buildActivity(AccessActivity::class.java).setup()
        grantEverything(context)
        controller.pause().resume()
        val activity = controller.get()
        assertEquals(
            MainActivity::class.java.name,
            shadowOf(activity).nextStartedActivity.component?.className,
        )
        assertTrue(activity.isFinishing)
    }
}
