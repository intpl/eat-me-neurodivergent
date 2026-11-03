package pl.gladecki.eatmen.ui

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import pl.gladecki.eatmen.R

/** Every choice in the app is a short list of words. There is no typing in the flows. */
fun Activity.showOptionsSheet(title: String, options: List<String>, onPick: (Int) -> Unit) {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.sheet_options, null)
    view.findViewById<android.widget.TextView>(R.id.sheetTitle).text = title
    val container = view.findViewById<android.widget.LinearLayout>(R.id.sheetOptions)
    options.forEachIndexed { index, label ->
        val button = layoutInflater.inflate(R.layout.item_option, container, false) as MaterialButton
        button.text = label
        button.setOnClickListener {
            dialog.dismiss()
            onPick(index)
        }
        container.addView(button)
    }
    dialog.setContentView(view)
    dialog.show()
    // The sheet is part of the app, not a Material sample: give it the same
    // warm surface and the rounded top the rest of the screens use.
    dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
        ?.setBackgroundResource(R.drawable.sheet_background)
}

/** Settings are the only place with typing, and only for a number of hours. */
fun Activity.showHoursDialog(title: String, initial: String, onValue: (Double) -> Unit) {
    val container = FrameLayout(this)
    val input = EditText(this).apply {
        id = R.id.numberInput
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(initial)
        setSelection(initial.length)
        hint = getString(R.string.settings_gap_hint)
    }
    container.addView(input)
    val padding = (24 * resources.displayMetrics.density).toInt()
    container.setPadding(padding, padding, padding, 0)
    val dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(container)
        .setPositiveButton(R.string.action_ok) { _, _ ->
            val hours = input.text.toString().trim().replace(',', '.').toDoubleOrNull()
            if (hours != null && hours > 0.0) onValue(hours)
        }
        .setNegativeButton(R.string.action_cancel, null)
        .create()
    dialog.show()
}
