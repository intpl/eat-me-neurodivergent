package pl.gladecki.eatmen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pl.gladecki.eatmen.core.HOUR_MS
import pl.gladecki.eatmen.core.Kind
import pl.gladecki.eatmen.core.Meal
import pl.gladecki.eatmen.core.MINUTE_MS
import pl.gladecki.eatmen.core.Text
import pl.gladecki.eatmen.databinding.ActivityEditHistoryBinding
import pl.gladecki.eatmen.ui.showOptionsSheet

/** Meals are editable for 24 hours. Everything older lives in the JSON export. */
class EditHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditHistoryBinding
    private val adapter = MealAdapter()

    private val controller: AppController get() = EatMeApp.controller(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.edit_history_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        adapter.onClick = { meal -> showMealActions(meal) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val meals = controller.editableMeals(now)
        adapter.submit(meals, controller.zone, now)
        binding.textEmpty.visibility = if (meals.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showMealActions(meal: Meal) {
        showOptionsSheet(
            Text.todayRow(System.currentTimeMillis(), meal, controller.zone),
            listOf(
                getString(R.string.opt_change_time),
                getString(R.string.opt_change_kind),
                getString(R.string.opt_delete),
            ),
        ) { index ->
            when (index) {
                0 -> changeTime(meal)
                1 -> changeKind(meal)
                else -> confirmDelete(meal)
            }
        }
    }

    private fun changeTime(meal: Meal) {
        val offsets = listOf(0L, 30 * MINUTE_MS, HOUR_MS, 2 * HOUR_MS, 3 * HOUR_MS, 4 * HOUR_MS, 5 * HOUR_MS)
        val labels = listOf(
            getString(R.string.opt_now),
            getString(R.string.opt_minutes_ago, 30),
            getString(R.string.opt_hours_ago, 1),
            getString(R.string.opt_hours_ago, 2),
            getString(R.string.opt_hours_ago, 3),
            getString(R.string.opt_hours_ago, 4),
            getString(R.string.opt_hours_ago, 5),
        )
        showOptionsSheet(getString(R.string.sheet_when), labels) { index ->
            controller.editMeal(meal.id, System.currentTimeMillis() - offsets[index], null)
            render()
        }
    }

    private fun changeKind(meal: Meal) {
        showOptionsSheet(
            getString(R.string.sheet_which),
            listOf(getString(R.string.opt_meal), getString(R.string.opt_snack)),
        ) { index ->
            controller.editMeal(meal.id, null, if (index == 0) Kind.MEAL else Kind.SNACK)
            render()
        }
    }

    private fun confirmDelete(meal: Meal) {
        MaterialAlertDialogBuilder(this)
            .setMessage(Text.deleteMealQuestion())
            .setPositiveButton(R.string.action_delete) { _, _ ->
                controller.deleteMeal(meal.id)
                render()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private class MealAdapter : RecyclerView.Adapter<MealAdapter.Holder>() {

        private var meals: List<Meal> = emptyList()
        private var lines: List<String> = emptyList()
        var onClick: (Meal) -> Unit = {}

        fun submit(meals: List<Meal>, zone: java.time.ZoneId, now: Long) {
            this.meals = meals
            this.lines = meals.map { Text.todayRow(now, it, zone) }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_edit_meal, parent, false)
            return Holder(view as TextView)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = lines[position]
            holder.text.setOnClickListener { onClick(meals[position]) }
        }

        override fun getItemCount(): Int = meals.size

        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
    }
}
