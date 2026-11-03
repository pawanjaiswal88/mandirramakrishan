package com.mandir.manager.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.R
import com.mandir.manager.data.CommitteeTermEntity
import com.mandir.manager.databinding.ActivityListBinding
import com.mandir.manager.databinding.DialogTermBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.Session
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch

/**
 * Committee history: who held which post from month-year to month-year.
 * Type a year (e.g. 2025) in the search box to see the committee of that year.
 */
class CommitteeActivity : BaseActivity() {

    private lateinit var b: ActivityListBinding
    private var all: List<CommitteeTermEntity> = emptyList()
    private val months = Dates.recentMonths(back = 120, ahead = 12)

    private val adapter = RowAdapter<CommitteeTermEntity>(
        idOf = { it.uid },
        bind = { t, r ->
            r.tvTitle.text = t.memberName
            r.tvSubtitle.text = t.designation
            r.tvMeta.text = "${Fmt.monthLabel(t.fromMonth)} – ${t.toMonth?.let { Fmt.monthLabel(it) } ?: "present"}"
            r.tvAmount.text = if (t.toMonth == null) "Current" else "Past"
            r.tvAmount.setTextColor(getColor(if (t.toMonth == null) R.color.income else R.color.ink_muted))
        },
        onClick = { if (Session.isAdmin) edit(it) else toast("Only an admin can correct committee history") }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.fab.isVisible = false
        b.tilSearch.hint = "Search name or post, or type a year like 2025"
        b.tvEmpty.text = "No committee members yet.\nSet a member's category to Committee Member to start their record."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.etSearch.doAfterTextChanged { applyFilter() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.committeeDao().observeAll().collect { all = it; applyFilter() }
            }
        }
    }

    private fun applyFilter() {
        val q = b.etSearch.value().lowercase()
        val list = when {
            q.isEmpty() -> all
            Regex("^\\d{4}$").matches(q) -> all.filter { t ->
                t.fromMonth <= "$q-12" && (t.toMonth == null || t.toMonth >= "$q-01")
            }
            else -> all.filter { t -> t.memberName.lowercase().contains(q) || t.designation.lowercase().contains(q) }
        }
        adapter.submitList(list)
        val year = q.takeIf { Regex("^\\d{4}$").matches(it) }
        b.tvSummary.text = if (year != null) "Committee in $year: ${list.size} posts"
        else "${list.count { it.toMonth == null }} current posts · ${list.count { it.toMonth != null }} past"
        b.tvEmpty.isVisible = list.isEmpty()
    }

    private fun edit(t: CommitteeTermEntity) {
        val v = DialogTermBinding.inflate(layoutInflater)
        val labels = months.map { Fmt.monthLabel(it) }
        v.actDesignation.setOptions(Constants.DESIGNATIONS, t.designation)
        v.actFrom.setOptions(labels, Fmt.monthLabel(t.fromMonth))
        v.actTo.setOptions(listOf("Present") + labels, t.toMonth?.let { Fmt.monthLabel(it) } ?: "Present")
        MaterialAlertDialogBuilder(this)
            .setTitle(t.memberName)
            .setView(v.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val from = months.firstOrNull { Fmt.monthLabel(it) == v.actFrom.text.toString() } ?: t.fromMonth
                val toText = v.actTo.text.toString()
                val to = if (toText == "Present") null else months.firstOrNull { Fmt.monthLabel(it) == toText }
                if (to != null && to < from) { toast("End month is before start month"); return@setPositiveButton }
                lifecycleScope.launch {
                    if (to == null && t.toMonth != null && db.committeeDao().openFor(t.memberUid) != null) {
                        toast("${t.memberName} already has a current post. End that one first."); return@launch
                    }
                    db.committeeDao().upsert(
                        t.copy(designation = v.actDesignation.text.toString(), fromMonth = from, toMonth = to,
                            updatedAt = System.currentTimeMillis())
                    )
                    toast("Committee record updated")
                }
            }
            .show()
    }
}
