package pl.gladecki.eatmen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import pl.gladecki.eatmen.databinding.ActivityAccessBinding

/**
 * The gate. It names each switch the phone withholds and opens the phone's own
 * page for it; the app opens by itself once nothing is missing.
 */
class AccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.textIntro.text = getString(R.string.access_intro)
    }

    override fun onResume() {
        super.onResume()
        check()
    }

    /** Re-reads the phone. Leaves for the front screen as soon as nothing is withheld. */
    private fun check() {
        val missing = Accesses.missing(this)
        if (missing.isEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        render(missing)
    }

    private fun render(missing: List<Access>) {
        val list = binding.accessList
        list.removeAllViews()
        missing.forEach { access ->
            addRow(list, titleOf(access), getString(R.string.access_allow)) { open(access) }
        }
        addRow(list, getString(R.string.access_check), "") { check() }
    }

    private fun addRow(list: LinearLayout, title: String, value: String, onClick: () -> Unit) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_setting, list, false)
        row.findViewById<TextView>(R.id.settingTitle).text = title
        row.findViewById<TextView>(R.id.settingValue).text = value
        row.setOnClickListener { onClick() }
        list.addView(row)
    }

    /**
     * The phone's page for the switch. A phone that does not have that page gets
     * the app's own info page instead, so the gate always has a way forward.
     */
    private fun open(access: Access) {
        val intent = Accesses.settingsIntent(this, access) ?: return
        try {
            startActivity(intent)
        } catch (missing: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun titleOf(access: Access): String = getString(
        when (access) {
            Access.NOTIFICATIONS -> R.string.access_notifications
            Access.EXACT_ALARMS -> R.string.access_exact_alarms
            Access.DO_NOT_DISTURB -> R.string.access_do_not_disturb
            Access.BATTERY -> R.string.access_battery
        },
    )
}
