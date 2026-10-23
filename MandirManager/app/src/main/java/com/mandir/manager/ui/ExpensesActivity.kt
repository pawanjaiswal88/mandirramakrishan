package com.mandir.manager.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.R
import com.mandir.manager.data.ExpenseEntity
import com.mandir.manager.databinding.ActivityListBinding
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.Share
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.io.File

class ExpensesActivity : BaseActivity() {

    private lateinit var b: ActivityListBinding
    private var all: List<ExpenseEntity> = emptyList()

    private val adapter = RowAdapter<ExpenseEntity>(
        idOf = { it.expenseId },
        bind = { e, r ->
            r.tvTitle.text = e.description
            r.tvSubtitle.text = listOfNotNull(e.category, e.festivalName).joinToString(" · ") +
                if (e.billImagePath != null) "  📎 bill" else ""
            r.tvMeta.text = "${e.voucherNumber} · ${Fmt.date(e.timestamp)} · ${e.paidTo} · ${e.paymentMode}"
            r.tvAmount.text = "− ${Fmt.money(e.amountPaid)}"
            r.tvAmount.setTextColor(getColor(R.color.expense))
        },
        onClick = { showOptions(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.tilSearch.hint = "Search description, shop, category or festival"
        b.tvEmpty.text = "No expenses recorded yet.\nAdd samagri, tent, electricity and other payments here."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.fab.text = "Add expense"
        b.fab.setOnClickListener { startActivity(Intent(this, AddExpenseActivity::class.java)) }
        b.etSearch.doAfterTextChanged { applyFilter() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.expenseDao().observeAll().collect { all = it; applyFilter() }
            }
        }
    }

    private fun applyFilter() {
        val q = b.etSearch.value().lowercase()
        val list = if (q.isEmpty()) all else all.filter { e ->
            listOf(e.description, e.paidTo, e.category, e.festivalName, e.voucherNumber)
                .any { it?.lowercase()?.contains(q) == true }
        }
        adapter.submitList(list)
        b.tvSummary.text = "${list.size} expenses · ${Fmt.money(list.sumOf { it.amountPaid })}"
        b.tvEmpty.isVisible = list.isEmpty()
    }

    private fun showOptions(e: ExpenseEntity) {
        val hasBill = e.billImagePath?.let { File(it).exists() } == true
        val options = if (hasBill) arrayOf("View bill photo", "Delete expense") else arrayOf("Delete expense")
        MaterialAlertDialogBuilder(this)
            .setTitle("${e.voucherNumber} · ${Fmt.money(e.amountPaid)}")
            .setItems(options) { _, which ->
                if (hasBill && which == 0) viewBill(File(e.billImagePath!!)) else confirmDelete(e)
            }
            .show()
    }

    private fun viewBill(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Share.uri(this, file), "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) } catch (ex: ActivityNotFoundException) { toast("No app found to open images") }
    }

    private fun confirmDelete(e: ExpenseEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${e.voucherNumber}?")
            .setMessage("${Fmt.money(e.amountPaid)} paid to ${e.paidTo} will be removed from all reports. This cannot be undone.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.expenseDao().delete(e)
                    e.billImagePath?.let { File(it).delete() }
                }
            }
            .show()
    }
}
