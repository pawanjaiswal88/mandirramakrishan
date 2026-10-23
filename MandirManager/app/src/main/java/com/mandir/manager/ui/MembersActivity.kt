package com.mandir.manager.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.R
import com.mandir.manager.data.MemberEntity
import com.mandir.manager.databinding.ActivityListBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.WhatsApp
import com.mandir.manager.util.value
import kotlinx.coroutines.launch

class MembersActivity : BaseActivity() {

    private lateinit var b: ActivityListBinding
    private var all: List<MemberEntity> = emptyList()

    private val adapter = RowAdapter<MemberEntity>(
        idOf = { it.memberId },
        bind = { m, r ->
            r.tvTitle.text = m.fullName
            r.tvSubtitle.text = listOfNotNull(
                m.designation ?: m.category,
                m.houseNo?.takeIf { it.isNotBlank() }?.let { "House $it" },
                "Family of ${m.familyCount}"
            ).joinToString(" · ")
            r.tvMeta.text = "${m.recordId} · ${m.mobileNumber}"
            r.tvAmount.text = if (m.monthlyFee > 0) "${Fmt.money(m.monthlyFee)}/mo" else ""
            r.tvAmount.setTextColor(getColor(R.color.ink_muted))
        },
        onClick = { showOptions(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.tilSearch.hint = "Search name, mobile or house no."
        b.tvEmpty.text = "No members yet.\nAdd committee members and colony residents to start tracking fees."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.fab.text = "Add member"
        b.fab.setOnClickListener { startActivity(Intent(this, AddMemberActivity::class.java)) }
        b.etSearch.doAfterTextChanged { applyFilter() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.memberDao().observeAll().collect { all = it; applyFilter() }
            }
        }
    }

    private fun applyFilter() {
        val q = b.etSearch.value().lowercase()
        val list = if (q.isEmpty()) all else all.filter { m ->
            listOf(m.fullName, m.mobileNumber, m.houseNo, m.recordId, m.category, m.designation)
                .any { it?.lowercase()?.contains(q) == true }
        }
        adapter.submitList(list)
        val residents = list.count { it.category == Constants.CAT_RESIDENT }
        b.tvSummary.text = "${list.size} members · $residents colony residents · ${list.sumOf { it.familyCount }} people"
        b.tvEmpty.isVisible = list.isEmpty()
    }

    private fun showOptions(m: MemberEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(m.fullName)
            .setItems(arrayOf("Record a donation", "Edit details", "Call", "Open WhatsApp chat", "Delete member")) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, AddDonationActivity::class.java)
                            .putExtra(AddDonationActivity.EXTRA_MEMBER_ID, m.memberId)
                            .putExtra(
                                AddDonationActivity.EXTRA_TYPE,
                                if (m.monthlyFee > 0) Constants.TYPE_MONTHLY else Constants.TYPE_GENERAL
                            )
                    )
                    1 -> startActivity(Intent(this, AddMemberActivity::class.java).putExtra(AddMemberActivity.EXTRA_ID, m.memberId))
                    2 -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${m.mobileNumber}")))
                    3 -> WhatsApp.send(this, m.mobileNumber, "")
                    4 -> confirmDelete(m)
                }
            }
            .show()
    }

    private fun confirmDelete(m: MemberEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ${m.fullName}?")
            .setMessage("Their past donations stay in the records under their name.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { db.memberDao().delete(m) } }
            .show()
    }
}
