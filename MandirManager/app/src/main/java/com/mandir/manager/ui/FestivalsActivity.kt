package com.mandir.manager.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.R
import com.mandir.manager.data.FestivalEntity
import com.mandir.manager.databinding.ActivityListBinding
import com.mandir.manager.databinding.DialogFestivalBinding
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.util.UUID

/** Festival / event records that appear as a pop-up list in Festival Seva donations and expenses. */
class FestivalsActivity : BaseActivity() {

    private lateinit var b: ActivityListBinding
    private var collected: Map<String, Double> = emptyMap()
    private var spent: Map<String, Double> = emptyMap()

    private val adapter = RowAdapter<FestivalEntity>(
        idOf = { it.uid },
        bind = { f, r ->
            r.tvTitle.text = f.name + if (!f.active) "  (hidden)" else ""
            r.tvSubtitle.text = dates(f) ?: "No dates set"
            val inc = collected[f.name.lowercase()] ?: 0.0
            val out = spent[f.name.lowercase()] ?: 0.0
            r.tvMeta.text = "Collected ${Fmt.money(inc)} · Spent ${Fmt.money(out)}" + (f.notes?.let { "\n$it" } ?: "")
            r.tvAmount.text = Fmt.money(inc - out)
            r.tvAmount.setTextColor(getColor(if (inc >= out) R.color.income else R.color.expense))
        },
        onClick = { edit(it) }
    )

    private fun dates(f: FestivalEntity): String? = when {
        f.startDate != null && f.endDate != null -> "${Fmt.date(f.startDate)} – ${Fmt.date(f.endDate)}"
        f.startDate != null -> "From ${Fmt.date(f.startDate)}"
        else -> null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.tilSearch.isVisible = false
        b.tvEmpty.text = "No festivals yet.\nAdd Ganesh Utsav, Navratri, Janmashtami and others so they pop up in donation entry."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.fab.text = "Add festival"
        b.fab.setOnClickListener { edit(null) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.festivalDao().observeAll().collect { list ->
                    collected = db.donationDao().festivalTotals().associate { it.label.orEmpty().lowercase() to it.total }
                    spent = db.expenseDao().festivalTotals().associate { it.label.orEmpty().lowercase() to it.total }
                    adapter.submitList(list)
                    adapter.notifyDataSetChanged()
                    b.tvSummary.text = "${list.count { it.active }} shown in donation entry · ${list.size} total"
                    b.tvEmpty.isVisible = list.isEmpty()
                }
            }
        }
    }

    private fun edit(existing: FestivalEntity?) {
        val v = DialogFestivalBinding.inflate(layoutInflater)
        var start = existing?.startDate
        var end = existing?.endDate
        fun show() {
            v.etStart.setText(start?.let { Fmt.date(it) } ?: "")
            v.etEnd.setText(end?.let { Fmt.date(it) } ?: "")
        }
        fun pick(title: String, current: Long?, onPicked: (Long) -> Unit) {
            val p = MaterialDatePicker.Builder.datePicker().setTitleText(title)
                .setSelection(Dates.toPicker(current ?: System.currentTimeMillis())).build()
            p.addOnPositiveButtonClickListener { onPicked(Dates.pickedDate(it, 0L)) }
            p.show(supportFragmentManager, title)
        }
        v.etName.setText(existing?.name ?: "")
        v.etNotes.setText(existing?.notes ?: "")
        v.swActive.isChecked = existing?.active ?: true
        v.etStart.setOnClickListener { pick("Festival starts", start) { start = it; show() } }
        v.etEnd.setOnClickListener { pick("Festival ends", end) { end = it; show() } }
        show()

        lifecycleScope.launch {
            // Renaming would split the festival's totals, so the name is locked once it has entries.
            val used = existing != null &&
                (db.donationDao().countFestival(existing.name) + db.expenseDao().countFestival(existing.name)) > 0
            if (used) {
                v.etName.isEnabled = false
                v.tilName.helperText = "Name is locked because donations or expenses use it"
            }
            MaterialAlertDialogBuilder(this@FestivalsActivity)
                .setTitle(if (existing == null) "Add festival" else "Edit festival")
                .setView(v.root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val name = v.etName.value()
                    if (name.isEmpty()) { toast("Enter the festival name"); return@setPositiveButton }
                    if (start != null && end != null && end!! < start!!) { toast("End date is before start date"); return@setPositiveButton }
                    lifecycleScope.launch {
                        val clash = db.festivalDao().byName(name)
                        if (clash != null && clash.uid != existing?.uid) { toast("“$name” already exists"); return@launch }
                        db.festivalDao().upsert(
                            FestivalEntity(
                                uid = existing?.uid ?: UUID.randomUUID().toString(),
                                name = name, startDate = start, endDate = end,
                                notes = v.etNotes.value().ifBlank { null },
                                active = v.swActive.isChecked,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        toast("Festival saved")
                    }
                }
                .show()
        }
    }
}
