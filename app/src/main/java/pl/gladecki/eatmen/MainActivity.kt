package pl.gladecki.eatmen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.MINUTE_MS
import pl.gladecki.eatmen.core.Text
import pl.gladecki.eatmen.databinding.ActivityMainBinding
import pl.gladecki.eatmen.ui.SheetOptions
import pl.gladecki.eatmen.ui.showOptionsSheet

/**
 * Two actions, one notification, one screen. Everything else is a short list of
 * words on a sheet.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val controller: AppController get() = EatMeApp.controller(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonAte.setOnClickListener { askWhenLogging() }
        binding.buttonWillEat.setOnClickListener { askWhenPlanning() }
        // One tap on the next line changes the reminder it is showing.
        binding.linkChange.setOnClickListener { askWhenPlanning() }
        binding.linkEditHistory.setOnClickListener {
            startActivity(Intent(this, EditHistoryActivity::class.java))
        }
        binding.linkSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        controller.noteAppOpened()
        askNotificationPermission()
        handleAction(intent)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_ATE) {
            intent.removeExtra(EXTRA_ACTION)
            askWhich(System.currentTimeMillis())
        }
    }

    private fun render() {
        controller.refresh()
        val now = System.currentTimeMillis()
        val zone = controller.zone

        binding.textLast.text = Text.lastLine(now, controller.lastMeal(), zone)
        binding.textNext.text = Text.nextLine(now, controller.state.plan, zone)
        binding.linkChange.visibility = if (controller.state.plan == null) View.GONE else View.VISIBLE

        val stale = Text.staleLine(now, controller.lastMeal())
        binding.textStale.text = stale ?: ""
        binding.textStale.visibility = if (stale == null) View.GONE else View.VISIBLE

        val today = controller.todayMeals(now)
        binding.listToday.removeAllViews()
        today.forEach { meal ->
            val row = layoutInflater.inflate(R.layout.row_meal, binding.listToday, false) as TextView
            row.text = Text.todayRow(now, meal, zone)
            binding.listToday.addView(row)
        }
        binding.textEmptyToday.visibility = if (today.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun askWhenLogging() {
        val labels = SheetOptions.pastLabels(this)
        showOptionsSheet(getString(R.string.sheet_when), labels) { index ->
            askWhich(System.currentTimeMillis() - SheetOptions.pastMinutes[index] * MINUTE_MS)
        }
    }

    private fun askWhich(eatenAt: Long) {
        showOptionsSheet(
            getString(R.string.sheet_which),
            listOf(getString(R.string.opt_meal), getString(R.string.opt_snack)),
        ) { index ->
            controller.logMeal(eatenAt, if (index == 0) Kind.MEAL else Kind.SNACK)
            render()
            warnIfNight()
        }
    }

    private fun askWhenPlanning() {
        val labels = SheetOptions.aheadLabels(this) + getString(R.string.opt_tomorrow)
        showOptionsSheet(getString(R.string.sheet_when), labels) { index ->
            if (index == SheetOptions.aheadMinutes.size) {
                controller.tomorrow()
            } else {
                controller.planAhead(System.currentTimeMillis() + SheetOptions.aheadMinutes[index] * MINUTE_MS)
            }
            render()
            warnIfNight()
        }
    }

    private fun warnIfNight() {
        val at = controller.nightAlertAt() ?: return
        val clock = Text.clock(at, controller.zone)
        MaterialAlertDialogBuilder(this)
            .setMessage(Text.nightWarning(clock))
            .setPositiveButton(getString(R.string.night_keep, clock), null)
            .setNegativeButton(R.string.action_tomorrow) { _, _ ->
                controller.tomorrow()
                render()
            }
            .show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_ACTION = "pl.gladecki.eatmen.ACTION"
        const val ACTION_ATE = "ate"
        private const val REQUEST_NOTIFICATIONS = 1
    }
}
