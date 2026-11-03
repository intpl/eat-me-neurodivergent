package pl.gladecki.eatmen

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import pl.gladecki.eatmen.core.HOUR_MS
import pl.gladecki.eatmen.core.Settings
import pl.gladecki.eatmen.core.Text
import pl.gladecki.eatmen.databinding.ActivitySettingsBinding
import pl.gladecki.eatmen.ui.showHoursDialog
import pl.gladecki.eatmen.ui.showOptionsSheet

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val controller: AppController get() = EatMeApp.controller(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.settings_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val settings = controller.settings()
        val list = binding.settingsList
        list.removeAllViews()

        addRow(list, getString(R.string.settings_meal_gap), Text.short(settings.mealGapMs)) {
            editGap(getString(R.string.settings_meal_gap), Settings.KEY_MEAL_GAP, settings.mealGapMs)
        }
        addRow(list, getString(R.string.settings_snack_gap), Text.short(settings.snackGapMs)) {
            editGap(getString(R.string.settings_snack_gap), Settings.KEY_SNACK_GAP, settings.snackGapMs)
        }
        addRow(list, getString(R.string.settings_tomorrow_silence), Text.short(settings.tomorrowSilenceMs)) {
            editGap(getString(R.string.settings_tomorrow_silence), Settings.KEY_TOMORROW_SILENCE, settings.tomorrowSilenceMs)
        }
        addRow(list, getString(R.string.settings_not_now), Text.short(settings.notNowMs)) {
            editGap(getString(R.string.settings_not_now), Settings.KEY_NOT_NOW, settings.notNowMs)
        }
        addRow(list, getString(R.string.settings_night_hours), clockOf(settings.nightStartMinute) + "–" + clockOf(settings.nightEndMinute)) {
            editNightHours(settings)
        }
        addRow(list, getString(R.string.settings_sound), soundName(settings.soundId)) {
            editSound(settings.soundId)
        }
        addSwitch(list, getString(R.string.settings_vibration), settings.vibration) { checked ->
            controller.changeSetting(Settings.KEY_VIBRATION, checked.toString())
            render()
        }
        if (!notificationPolicyGranted()) {
            addRow(list, getString(R.string.settings_dnd), "") {
                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        addRow(list, getString(R.string.settings_copy_json), "") { copyJson() }
        addRow(list, getString(R.string.settings_erase), "") { confirmErase() }
    }

    private fun addRow(list: LinearLayout, title: String, value: String, onClick: () -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting, list, false)
        row.findViewById<TextView>(R.id.settingTitle).text = title
        row.findViewById<TextView>(R.id.settingValue).text = value
        row.setOnClickListener { onClick() }
        list.addView(row)
    }

    private fun addSwitch(list: LinearLayout, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting_switch, list, false)
        row.findViewById<TextView>(R.id.settingTitle).text = title
        val switch = row.findViewById<MaterialSwitch>(R.id.settingSwitch)
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, value -> onChange(value) }
        row.setOnClickListener { switch.isChecked = !switch.isChecked }
        list.addView(row)
    }

    private fun editGap(title: String, key: String, currentMs: Long) {
        val initial = formatHours(currentMs)
        showHoursDialog(title, initial) { hours ->
            val ms = (hours * HOUR_MS).toLong()
            controller.changeSetting(key, ms.toString())
            render()
        }
    }

    private fun editNightHours(settings: Settings) {
        TimePickerDialog(
            this,
            { _, startHour, startMinute ->
                TimePickerDialog(
                    this,
                    { _, endHour, endMinute ->
                        val start = startHour * 60 + startMinute
                        val end = endHour * 60 + endMinute
                        controller.changeSetting(Settings.KEY_NIGHT, "$start-$end")
                        render()
                    },
                    settings.nightEndMinute / 60,
                    settings.nightEndMinute % 60,
                    true,
                ).show()
            },
            settings.nightStartMinute / 60,
            settings.nightStartMinute % 60,
            true,
        ).show()
    }

    private fun editSound(current: String) {
        val ids = SOUNDS.map { it.first }
        val names = SOUNDS.map { getString(it.second) }
        showOptionsSheet(getString(R.string.settings_sound), names) { index ->
            controller.changeSetting(Settings.KEY_SOUND, ids[index])
            render()
        }
    }

    private fun copyJson() {
        val json = controller.exportJson()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("eat-me-neurodivergent", json))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun confirmErase() {
        MaterialAlertDialogBuilder(this)
            .setMessage(Text.eraseQuestion())
            .setPositiveButton(R.string.action_erase) { _, _ ->
                controller.eraseEverything()
                finish()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun notificationPolicyGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = getSystemService(NotificationManager::class.java)
        return manager.isNotificationPolicyAccessGranted
    }

    private fun soundName(id: String): String =
        SOUNDS.firstOrNull { it.first == id }?.let { getString(it.second) } ?: id

    private fun formatHours(ms: Long): String {
        val hours = ms.toDouble() / HOUR_MS
        return if (hours % 1.0 == 0.0) hours.toInt().toString() else hours.toString()
    }

    private fun clockOf(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    companion object {
        private val SOUNDS = listOf(
            "eat_something" to R.string.sound_eat_something,
            "food_oclock" to R.string.sound_food_oclock,
            "it_is_time" to R.string.sound_it_is_time,
        )
    }
}
