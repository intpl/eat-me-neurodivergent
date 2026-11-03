package pl.gladecki.eatmen

import android.app.AlertDialog
import android.content.Context
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Plan
import pl.gladecki.eatmen.core.Planner
import pl.gladecki.eatmen.core.Source
import pl.gladecki.eatmen.databinding.ActivityMainBinding

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller: AppController get() = EatMeApp.controller(context)

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        return activity
    }

    private fun pickOption(activity: MainActivity, index: Int) {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull("no sheet was shown", dialog)
        val options = dialog.findViewById<LinearLayout>(R.id.sheetOptions)
        assertNotNull("the sheet has no options container", options)
        options.getChildAt(index).performClick()
    }

    private fun bindingOf(activity: MainActivity): ActivityMainBinding {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        return ActivityMainBinding.bind(root)
    }

    @Test
    fun aFreshInstallShowsTheTwoButtonsAndSaysNothingElse() {
        val activity = launch()
        val binding = bindingOf(activity)
        assertEquals("nothing logged", binding.textLast.text.toString())
        assertEquals("no reminder", binding.textNext.text.toString())
        assertEquals(android.view.View.VISIBLE, binding.textEmptyToday.visibility)
        assertEquals(android.view.View.GONE, binding.textStale.visibility)
        assertNull(controller.state.plan)
        assertEquals(0, controller.state.meals.size)
    }

    @Test
    fun openingTheAppIsWrittenDown() {
        launch()
        assertTrue(controller.exportJson().contains("\"app_opened\""))
    }

    @Test
    fun threeTapsLogAMealNow() {
        val activity = launch()
        bindingOf(activity).buttonAte.performClick()
        pickOption(activity, 0)          // Now
        pickOption(activity, 0)          // Meal

        assertEquals(1, controller.state.meals.size)
        val meal = controller.state.meals.single()
        assertEquals(Kind.MEAL, meal.kind)
        assertEquals(Plan.Due(meal.eatenAt + 4 * 3_600_000L, Source.SUGGESTED), controller.state.plan)
    }

    @Test
    fun theFrontScreenFollowsTheLog() {
        val activity = launch()
        bindingOf(activity).buttonAte.performClick()
        pickOption(activity, 0)
        pickOption(activity, 1)          // Snack

        val binding = bindingOf(activity)
        assertTrue(binding.textLast.text.toString().contains("snack"))
        assertTrue(binding.textNext.text.toString().contains("·"))
        assertTrue(binding.listToday.childCount > 0)
    }

    @Test
    fun iWillEatSetsAReminderAndLogsNothing() {
        val activity = launch()
        bindingOf(activity).buttonWillEat.performClick()
        pickOption(activity, 1)          // In 1 hour

        assertEquals(0, controller.state.meals.size)
        val plan = controller.state.plan!!
        assertTrue(plan is Plan.Warning)
        assertTrue(plan.source == Source.EXPLICIT)
    }

    @Test
    fun theChangeLinkChangesTheReminderInOneTap() {
        val activity = launch()
        bindingOf(activity).buttonAte.performClick()
        pickOption(activity, 0)          // Now
        pickOption(activity, 0)          // Meal
        val mealPlan = controller.state.plan

        bindingOf(activity).linkChange.performClick()
        pickOption(activity, 2)          // In 2 hours

        val changed = controller.state.plan
        assertTrue(changed is Plan.Warning)
        assertTrue(changed!!.source == Source.EXPLICIT)
        assertTrue(Planner.alertAt(changed) != Planner.alertAt(mealPlan!!))
    }

    @Test
    fun theChangeLinkIsHiddenWithNoReminder() {
        val activity = launch()
        assertEquals(android.view.View.GONE, bindingOf(activity).linkChange.visibility)
    }

    @Test
    fun tomorrowIsOnThePlanningSheet() {
        val activity = launch()
        bindingOf(activity).buttonWillEat.performClick()
        pickOption(activity, 5)          // Tomorrow

        val plan = controller.state.plan!!
        assertTrue(plan is Plan.TomorrowSilent)
        assertTrue(bindingOf(activity).textNext.text.toString().startsWith("silent until "))
    }

    @Test
    fun theStaleLineIsOnTheFrontWhenTheLastMealIsOld() {
        val longAgo = System.currentTimeMillis() - 9 * 3_600_000L
        controller.logMeal(longAgo, Kind.MEAL)
        val activity = launch()
        val binding = bindingOf(activity)
        assertEquals(android.view.View.VISIBLE, binding.textStale.visibility)
        assertTrue(binding.textStale.text.toString().contains("since your last meal"))
    }

    @Test
    fun ateFromTheNotificationOpensTheMealSheetStraightAway() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java,
        ).putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_ATE)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()

        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertTrue(dialog is com.google.android.material.bottomsheet.BottomSheetDialog)
        // One pick and the meal is logged at now; nothing was logged before it.
        assertEquals(0, controller.state.meals.size)
        pickOption(activity, 0)          // Meal
        assertEquals(1, controller.state.meals.size)
        assertEquals(Kind.MEAL, controller.state.meals.single().kind)
    }

    @Test
    fun theEditHistoryAndSettingsLinksOpenTheirScreens() {
        val activity = launch()
        bindingOf(activity).linkEditHistory.performClick()
        assertTrue(shadowOf(activity).nextStartedActivity.component?.className == EditHistoryActivity::class.java.name)
        bindingOf(activity).linkSettings.performClick()
        assertTrue(shadowOf(activity).nextStartedActivity.component?.className == SettingsActivity::class.java.name)
    }
}
