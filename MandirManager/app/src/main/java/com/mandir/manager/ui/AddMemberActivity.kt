package com.mandir.manager.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.data.CommitteeTermEntity
import com.mandir.manager.data.MemberEntity
import com.mandir.manager.data.MemberFeeRateEntity
import com.mandir.manager.databinding.ActivityAddMemberBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.Numbering
import com.mandir.manager.util.WhatsApp
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.util.UUID

class AddMemberActivity : BaseActivity() {

    companion object { const val EXTRA_UID = "member_uid" }

    private lateinit var b: ActivityAddMemberBinding
    private var editing: MemberEntity? = null
    private var openTerm: CommitteeTermEntity? = null
    private val monthKeys = Dates.recentMonths(back = 36, ahead = 12)
    private val yearKeys = Dates.yearOptions()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivityAddMemberBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.actCategory.setOptions(Constants.MEMBER_CATEGORIES, Constants.CAT_RESIDENT)
        b.actDesignation.setOptions(Constants.DESIGNATIONS, null)
        b.actPlan.setOptions(Constants.FEE_PLANS, Constants.PLAN_MONTHLY)
        b.actRoleFrom.setOptions(monthKeys.map { Fmt.monthLabel(it) }, Fmt.monthLabel(Fmt.monthKey()))
        b.etFee.setText(prefs.defaultMonthlyFee)
        b.etRegFee.setText("0")
        b.etFamily.setText("1")
        b.actCategory.setOnItemClickListener { _, _, _, _ -> onCategoryChanged() }
        b.actPlan.setOnItemClickListener { _, _, _, _ -> onPlanChanged() }
        b.btnSave.setOnClickListener { save() }
        updateFeeFrom()
        updateVisibility()

        intent.getStringExtra(EXTRA_UID)?.let { uid ->
            title = "Edit member"
            b.btnSave.text = "Save changes"
            lifecycleScope.launch {
                editing = db.memberDao().byUid(uid)
                openTerm = db.committeeDao().openFor(uid)
                if (savedInstanceState == null) editing?.let { fill(it) }
                updateVisibility()
                showHistory(uid)
            }
        }
    }

    private fun plan() = b.actPlan.text.toString()
    private fun isCommittee() = b.actCategory.text.toString() == Constants.CAT_COMMITTEE

    private fun fill(m: MemberEntity) {
        b.etName.setText(m.fullName)
        b.actCategory.setText(m.category, false)
        b.actDesignation.setText(m.designation ?: "", false)
        b.etMobile.setText(m.mobileNumber)
        b.etHouse.setText(m.houseNo ?: "")
        b.etFamily.setText(m.familyCount.toString())
        b.actPlan.setText(m.feePlan, false)
        b.etFee.setText(Fmt.plain(m.feeAmount))
        b.etRegFee.setText(Fmt.plain(m.registrationFee))
        updateFeeFrom()
    }

    /** "Fee applies from" lists months for monthly fees and calendar years for yearly fees. */
    private fun updateFeeFrom() {
        if (plan() == Constants.PLAN_YEARLY) {
            b.tilFeeFrom.hint = "Yearly fee applies from year"
            b.actFeeFrom.setOptions(yearKeys, Dates.year().toString())
        } else {
            b.tilFeeFrom.hint = if (plan() == Constants.PLAN_NONE) "No fee from month" else "Monthly fee applies from month"
            b.actFeeFrom.setOptions(monthKeys.map { Fmt.monthLabel(it) }, Fmt.monthLabel(Fmt.monthKey()))
        }
    }

    private fun feeFromKey(): String {
        val text = b.actFeeFrom.text.toString()
        return if (plan() == Constants.PLAN_YEARLY) Dates.rateKey(text.takeIf { it in yearKeys } ?: Dates.year().toString())
        else monthKeys.firstOrNull { Fmt.monthLabel(it) == text } ?: Fmt.monthKey()
    }

    private fun roleFromKey(): String =
        monthKeys.firstOrNull { Fmt.monthLabel(it) == b.actRoleFrom.text.toString() } ?: Fmt.monthKey()

    private fun updateVisibility() {
        val committee = isCommittee()
        b.tilDesignation.isVisible = committee
        b.tilRegFee.isVisible = committee
        b.tilRoleFrom.isVisible = committee || openTerm != null
        b.tilRoleFrom.hint = if (committee) "Committee post held from" else "Left the committee from"
        b.tilFee.isEnabled = plan() != Constants.PLAN_NONE
    }

    /** Defaults: residents pay monthly, committee members yearly, outside devotees nothing. */
    private fun onCategoryChanged() {
        val plan = when (b.actCategory.text.toString()) {
            Constants.CAT_COMMITTEE -> Constants.PLAN_YEARLY
            Constants.CAT_EXTERNAL -> Constants.PLAN_NONE
            else -> Constants.PLAN_MONTHLY
        }
        if (editing == null) {
            b.actPlan.setText(plan, false)
            onPlanChanged()
        }
        updateVisibility()
    }

    private fun onPlanChanged() {
        b.etFee.setText(
            when (plan()) {
                Constants.PLAN_MONTHLY -> prefs.defaultMonthlyFee
                Constants.PLAN_YEARLY -> prefs.defaultYearlyFee
                else -> "0"
            }
        )
        updateFeeFrom()
        updateVisibility()
    }

    private fun rateText(plan: String, amount: Double) = when (plan) {
        Constants.PLAN_MONTHLY -> "${Fmt.money(amount)} per month"
        Constants.PLAN_YEARLY -> "${Fmt.money(amount)} per year"
        else -> "No fee"
    }

    private fun showHistory(uid: String) {
        lifecycleScope.launch {
            val rates = db.feeRateDao().forMember(uid)
            val terms = db.committeeDao().forMember(uid)
            if (rates.isEmpty() && terms.isEmpty()) return@launch
            val sb = StringBuilder()
            if (rates.isNotEmpty()) {
                sb.append("Fee history")
                rates.forEach { r ->
                    val from = if (r.plan == Constants.PLAN_YEARLY) "year ${r.effectiveFrom.take(4)}" else Fmt.monthLabel(r.effectiveFrom)
                    sb.append("\n• ${rateText(r.plan, r.amount)} from $from")
                }
            }
            if (terms.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("Committee history")
                terms.forEach { t ->
                    sb.append("\n• ${t.designation}: ${Fmt.monthLabel(t.fromMonth)} – ${t.toMonth?.let { Fmt.monthLabel(it) } ?: "present"}")
                }
            }
            b.tvHistory.text = sb.toString()
            b.tvHistory.isVisible = true
        }
    }

    private fun save() {
        listOf(b.tilName, b.tilMobile, b.tilFee, b.tilRegFee).forEach { it.error = null }
        val name = b.etName.value()
        val category = b.actCategory.text.toString()
        val committee = category == Constants.CAT_COMMITTEE
        val designation = if (committee) b.actDesignation.text.toString().ifBlank { "Member" } else null
        val mobile = WhatsApp.localDigits(b.etMobile.value())
        val house = b.etHouse.value().ifBlank { null }
        val family = b.etFamily.value().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val plan = plan()
        val fee = if (plan == Constants.PLAN_NONE) 0.0 else b.etFee.value().toDoubleOrNull()
        val regFee = if (committee) b.etRegFee.value().ifEmpty { "0" }.toDoubleOrNull() else 0.0

        var ok = true
        if (name.isEmpty()) { b.tilName.error = "Enter the member's name"; ok = false }
        if (mobile.length != 10) { b.tilMobile.error = "Enter a 10-digit mobile number"; ok = false }
        if (fee == null || fee < 0 || (plan != Constants.PLAN_NONE && fee == 0.0)) {
            b.tilFee.error = "Enter the ${plan.lowercase()} fee amount"; ok = false
        }
        if (regFee == null || regFee < 0) { b.tilRegFee.error = "Enter 0 or more"; ok = false }
        if (!ok || fee == null || regFee == null) return

        val feeFrom = feeFromKey()
        val roleFrom = roleFromKey()

        lifecycleScope.launch {
            val write: suspend () -> Unit = {
                val now = System.currentTimeMillis()
                val e = editing
                val recordId = e?.recordId ?: Numbering.nextMemberId(db, prefs)
                val member = e?.copy(
                    fullName = name, category = category, designation = designation, mobileNumber = mobile,
                    houseNo = house, familyCount = family, feePlan = plan, feeAmount = fee,
                    registrationFee = regFee, updatedAt = now
                ) ?: MemberEntity(
                    uid = UUID.randomUUID().toString(), recordId = recordId, fullName = name, category = category,
                    designation = designation, mobileNumber = mobile, houseNo = house, familyCount = family,
                    feePlan = plan, feeAmount = fee, registrationFee = regFee, createdAt = now, updatedAt = now
                )

                db.withTransaction {
                    db.memberDao().upsert(member)
                    saveFeeRate(member.uid, plan, fee, feeFrom, now)
                    saveCommitteeTerm(member, committee, designation, roleFrom, now)
                }
                toast(if (e == null) "Member added · $recordId" else "Member updated")
                finish()
            }

            if (db.memberDao().countWithMobile(mobile, editing?.uid ?: "") > 0) {
                MaterialAlertDialogBuilder(this@AddMemberActivity)
                    .setTitle("Mobile number already registered")
                    .setMessage("Another member uses $mobile. Save anyway (for example, two people in the same family)?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save anyway") { _, _ -> lifecycleScope.launch { write() } }
                    .show()
            } else {
                write()
            }
        }
    }

    /**
     * Adds a fee-history entry only when the plan or amount actually changes. Old periods keep the
     * rate that applied to them; a second change in the same month/year replaces that entry.
     */
    private suspend fun saveFeeRate(memberUid: String, plan: String, amount: Double, from: String, now: Long) {
        val dao = db.feeRateDao()
        val history = dao.forMember(memberUid)
        val inEffect = history.firstOrNull { it.effectiveFrom <= from }
        if (inEffect != null && inEffect.plan == plan && inEffect.amount == amount && history.none { it.effectiveFrom > from }) return
        val sameStart = history.firstOrNull { it.effectiveFrom == from }
        dao.upsert(
            MemberFeeRateEntity(
                uid = sameStart?.uid ?: UUID.randomUUID().toString(),
                memberUid = memberUid, plan = plan, amount = amount, effectiveFrom = from,
                createdAt = sameStart?.createdAt ?: now, updatedAt = now
            )
        )
    }

    /** Keeps a dated record of every committee post: Treasurer Apr 2025 – Mar 2026, President Apr 2026 – present. */
    private suspend fun saveCommitteeTerm(m: MemberEntity, committee: Boolean, designation: String?, from: String, now: Long) {
        val dao = db.committeeDao()
        val open = dao.openFor(m.uid)
        fun newTerm(d: String) = CommitteeTermEntity(
            uid = UUID.randomUUID().toString(), memberUid = m.uid, memberName = m.fullName,
            designation = d, fromMonth = from, toMonth = null, updatedAt = now
        )
        if (committee && designation != null) {
            when {
                open == null -> dao.upsert(newTerm(designation))
                open.designation == designation ->
                    if (open.memberName != m.fullName) dao.upsert(open.copy(memberName = m.fullName, updatedAt = now))
                from <= open.fromMonth -> dao.upsert(open.copy(designation = designation, memberName = m.fullName, updatedAt = now))
                else -> {
                    dao.upsert(open.copy(toMonth = Dates.shiftMonth(from, -1), updatedAt = now))
                    dao.upsert(newTerm(designation))
                }
            }
        } else if (open != null) {
            val end = Dates.shiftMonth(from, -1).let { if (it < open.fromMonth) open.fromMonth else it }
            dao.upsert(open.copy(toMonth = end, updatedAt = now))
        }
    }
}
