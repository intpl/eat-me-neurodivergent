package pl.gladecki.eatmen

import android.app.NotificationManager
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Settings
import pl.gladecki.eatmen.databinding.ActivityEditHistoryBinding
import pl.gladecki.eatmen.databinding.ActivitySettingsBinding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsAndHistoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller: AppController get() = EatMeApp.controller(context)

    private fun dialog() = org.robolectric.shadows.ShadowDialog.getLatestDialog()

    /** AlertDialog button listeners are posted, so the main looper has to catch up. */
    private fun idleMain() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun root(activity: android.app.Activity): android.view.View =
        activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)

    @Test
    fun theSettingsScreenShowsEverySettingAndItsValue() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val binding = ActivitySettingsBinding.bind(root(activity))
        val list = binding.settingsList

        val rows = (0 until list.childCount).map { index ->
            val row = list.getChildAt(index)
            val title = row.findViewById<TextView>(R.id.settingTitle).text.toString()
            val value = row.findViewById<TextView>(R.id.settingValue)?.text?.toString() ?: ""
            title to value
        }
        assertTrue(rows.contains("Meal gap" to "4h"))
        assertTrue(rows.contains("Snack gap" to "2h"))
        assertTrue(rows.contains("Tomorrow silence" to "12h"))
        assertTrue(rows.contains("Not now" to "10m"))
        assertTrue(rows.contains("Night hours" to "23:00–07:00"))
        assertTrue(rows.contains("Sound" to "Eat something"))
        assertTrue(rows.any { it.first == "Vibration" })
        assertTrue(rows.any { it.first == "Send a test notification" })
        assertTrue(rows.any { it.first == "Copy JSON" })
        assertTrue(rows.any { it.first == "Erase everything" })
    }

    private fun settingsRows(activity: android.app.Activity): List<Pair<String, android.view.View>> {
        val binding = ActivitySettingsBinding.bind(root(activity))
        return (0 until binding.settingsList.childCount).map { index ->
            val row = binding.settingsList.getChildAt(index)
            val title = row.findViewById<TextView>(R.id.settingTitle).text.toString()
            title to row
        }
    }

    @Test
    fun theSettingsScreenOffersTheNotificationSwitchesWhenTheyAreOff() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val rows = settingsRows(activity).map { it.first }
        assertTrue(rows.contains("Allow notifications") || rows.contains("Allow exact reminders"))
    }

    @Test
    fun theTestRowSaysSoWhenTheAppCannotNotify() {
        org.robolectric.Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationsEnabled(false)
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val row = settingsRows(activity).first { it.first == "Send a test notification" }.second
        row.performClick()
        assertEquals(
            "notifications are off for this app",
            org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun aGapCanBeChangedFromTheSettingsScreen() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val binding = ActivitySettingsBinding.bind(root(activity))
        val mealGapRow = (0 until binding.settingsList.childCount)
            .map { binding.settingsList.getChildAt(it) }
            .first { it.findViewById<TextView>(R.id.settingTitle).text.toString() == "Meal gap" }

        mealGapRow.performClick()
        val dialog = dialog()
        assertNotNull(dialog)
        dialog.findViewById<EditText>(R.id.numberInput).setText("3")
        (dialog as androidx.appcompat.app.AlertDialog)
            .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertEquals(3 * 3_600_000L, controller.settings().mealGapMs)
        assertEquals("3h", controller.settingValue(Settings.KEY_MEAL_GAP).let { pl.gladecki.eatmen.core.Text.short(it.toLong()) })
    }

    @Test
    fun theNightHoursRowShowsTheWindowThePickIsStoredAs() {
        // The picker calls this exact thing with clock times when both ends are chosen.
        controller.changeSetting(Settings.KEY_NIGHT, "21:00-09:00")
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val row = settingsRows(activity).first { it.first == "Night hours" }.second
        assertEquals("21:00–09:00", row.findViewById<TextView>(R.id.settingValue).text.toString())
    }

    @Test
    fun theEditHistoryShowsOnlyTheLastDay() {
        val now = System.currentTimeMillis()
        controller.logMeal(now - 2 * 3_600_000L, Kind.MEAL)
        controller.logMeal(now - 30 * 3_600_000L, Kind.SNACK)

        val activity = Robolectric.buildActivity(EditHistoryActivity::class.java).setup().get()
        val binding = ActivityEditHistoryBinding.bind(root(activity))
        assertEquals(1, binding.recycler.adapter?.itemCount)
    }

    @Test
    fun deletingFromTheEditHistoryMovesTheCountdown() {
        controller.logMeal(System.currentTimeMillis() - 2 * 3_600_000L, Kind.MEAL)
        val activity = Robolectric.buildActivity(EditHistoryActivity::class.java).setup().get()
        val binding = ActivityEditHistoryBinding.bind(root(activity))

        binding.recycler.measure(0, 0)
        binding.recycler.layout(0, 0, 1080, 1920)
        binding.recycler.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        val sheet = dialog()
        val options = sheet.findViewById<LinearLayout>(R.id.sheetOptions)
        options.getChildAt(2).performClick()          // Delete
        val confirm = dialog() as androidx.appcompat.app.AlertDialog
        confirm.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertTrue(controller.state.meals.isEmpty())
        assertEquals(0, binding.recycler.adapter?.itemCount)
    }
}
