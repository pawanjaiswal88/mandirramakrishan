package com.mandir.manager.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.data.DonationEntity
import com.mandir.manager.data.FestivalEntity
import com.mandir.manager.data.MemberEntity
import com.mandir.manager.databinding.ActivityAddDonationBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.Numbering
import com.mandir.manager.util.Session
import com.mandir.manager.util.WhatsApp
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.util.UUID

class AddDonationActivity : BaseActivity() {

    companion object {
        const val EXTRA_MEMBER_UID = "member_uid"
        const val EXTRA_TYPE = "donation_type"
        const val EXTRA_PERIOD = "fee_period"
        private const val STATE_MEMBER = "selected_member"
        private const val STATE_TIME = "donation_time"
    }

    private lateinit var b: ActivityAddDonationBinding
    private var selected: MemberEntity? = null
    private val monthKeys = Dates.recentMonths(back = 23, ahead = 2).toMutableList()
    private val yearKeys = Dates.yearOptions().toMutableList()
    private var presetPeriod: String? = null
    private var shownType = ""
    private var festivals: List<String> = emptyList()
    private var time = System.currentTimeMillis()
    private var autoAmount: String? = null   // amount we filled in ourselves (safe to replace)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivityAddDonationBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        time = savedInstanceState?.getLong(STATE_TIME, time) ?: time
        presetPeriod = intent.getStringExtra(EXTRA_PERIOD)?.also { p ->
            if (p.length == 7 && p !in monthKeys) monthKeys.add(p)
            if (p.length == 4 && p !in yearKeys) yearKeys.add(p)
        }

        showDate()
        b.etDate.setOnClickListener { pickDate() }
        b.actType.setOptions(Constants.DONATION_TYPES, intent.getStringExtra(EXTRA_TYPE) ?: Constants.TYPE_GENERAL)
        b.actMode.setOptions(Constants.PAYMENT_MODES, Constants.MODE_CASH)
        b.etCollectedBy.setText(Session.name)

        b.actType.setOnItemClickListener { _, _, _, _ ->
            updateTypeUi()
            prefillAmount()
            if (typeText() == Constants.TYPE_FESTIVAL) b.etFestival.post { b.etFestival.requestFocus(); b.etFestival.showDropDown() }
        }
        b.actPeriod.setOnItemClickListener { _, _, _, _ -> prefillAmount() }
        b.actMode.setOnItemClickListener { _, _, _, _ -> updateModeUi() }
        b.etDonor.setOnItemClickListener { parent, _, position, _ ->
            (parent.getItemAtPosition(position) as? MemberEntity)?.let { selectMember(it) }
        }
        b.etDonor.doAfterTextChanged { text ->
            val s = selected
            if (s != null && text.toString() != s.fullName) {
                selected = null
                showMemberInfo()
            }
        }
        // Festival list pops up when the field is tapped.
        b.etFestival.setOnClickListener { if (festivals.isNotEmpty()) b.etFestival.showDropDown() }
        b.etFestival.setOnFocusChangeListener { _, has -> if (has && festivals.isNotEmpty()) b.etFestival.showDropDown() }

        b.btnSaveWhatsApp.setOnClickListener { save(sendWhatsApp = true) }
        b.btnSaveOnly.setOnClickListener { save(sendWhatsApp = false) }
        updateTypeUi()
        updateModeUi()

        val restoreUid = savedInstanceState?.getString(STATE_MEMBER) ?: intent.getStringExtra(EXTRA_MEMBER_UID)
        lifecycleScope.launch {
            val members = db.memberDao().getAll()
            b.etDonor.setAdapter(ArrayAdapter(this@AddDonationActivity, android.R.layout.simple_dropdown_item_1line, members))
            festivals = db.festivalDao().activeNames()
            b.etFestival.setAdapter(ArrayAdapter(this@AddDonationActivity, android.R.layout.simple_dropdown_item_1line, festivals))
            b.etFestival.threshold = 0
            members.firstOrNull { it.uid == restoreUid }?.let { selectMember(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MEMBER, selected?.uid)
        outState.putLong(STATE_TIME, time)
    }

    private fun typeText() = b.actType.text.toString()

    // ---------- date ----------
    private fun showDate() {
        val backDated = Dates.dayRange().first > time
        b.etDate.setText(Fmt.date(time) + if (backDated) "  (back-dated)" else "")
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Donation date")
            .setSelection(Dates.toPicker(time))
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build())
            .build()
        picker.addOnPositiveButtonClickListener { utc ->
            time = Dates.pickedDate(utc)
            showDate()
            // For a back-dated monthly fee, default the fee month to the donation month.
            val month = Fmt.monthKey(time)
            if (typeText() == Constants.TYPE_MONTHLY && month in monthKeys) {
                b.actPeriod.setText(Fmt.monthLabel(month), false)
                prefillAmount()
            }
        }
        picker.show(supportFragmentManager, "donation_date")
    }

    // ---------- member ----------
    private fun selectMember(m: MemberEntity) {
        selected = m
        b.etDonor.setText(m.fullName, false)
        b.etDonor.setSelection(m.fullName.length)
        b.etMobile.setText(m.mobileNumber)
        b.tilDonor.error = null
        val planType = Constants.feeTypeFor(m.feePlan)
        if (Constants.isFeeType(typeText()) && planType != null && planType != typeText()) {
            b.actType.setText(planType, false)
            updateTypeUi()
        }
        showMemberInfo()
        prefillAmount()
    }

    private fun showMemberInfo() {
        val m = selected
        b.tvDonorInfo.isVisible = m != null
        if (m != null) {
            val fee = when (m.feePlan) {
                Constants.PLAN_MONTHLY -> "${Fmt.money(m.feeAmount)}/month"
                Constants.PLAN_YEARLY -> "${Fmt.money(m.feeAmount)}/year"
                else -> null
            }
            val reg = if (m.registrationFee > 0) "registration ${Fmt.money(m.registrationFee)}" else null
            b.tvDonorInfo.text = "✓ " + listOfNotNull(
                m.recordId, m.designation ?: m.category,
                m.houseNo?.takeIf { it.isNotBlank() }?.let { "House $it" }, fee, reg
            ).joinToString(" · ")
        }
    }

    /** Fills the amount still owed for the chosen period, using the fee rate valid in that period. */
    private fun prefillAmount() {
        val m = selected ?: return
        val type = typeText()
        val current = b.etAmount.value()
        if (current.isNotEmpty() && current != autoAmount) return
        lifecycleScope.launch {
            val due: Double? = when (type) {
                Constants.TYPE_REGISTRATION ->
                    (m.registrationFee - db.donationDao().paidOfType(m.uid, type)).takeIf { m.registrationFee > 0 }
                Constants.TYPE_MONTHLY, Constants.TYPE_YEARLY -> {
                    val period = selectedPeriod() ?: return@launch
                    val rate = db.feeRateDao().effective(m.uid, Dates.rateKey(period))
                    val amount = when {
                        rate != null && Constants.feeTypeFor(rate.plan) == type -> rate.amount
                        rate == null && Constants.feeTypeFor(m.feePlan) == type -> m.feeAmount
                        else -> null
                    }
                    amount?.let { it - db.donationDao().feePaidFor(m.uid, period, type) }
                }
                else -> null
            }
            val text = due?.takeIf { it > 0 }?.let { Fmt.plain(it) } ?: ""
            if (b.etAmount.value().isEmpty() || b.etAmount.value() == autoAmount) {
                b.etAmount.setText(text)
                autoAmount = text
            }
        }
    }

    // ---------- type / mode ----------
    private fun updateTypeUi() {
        val type = typeText()
        val monthly = type == Constants.TYPE_MONTHLY
        val yearly = type == Constants.TYPE_YEARLY
        b.tilPeriod.isVisible = monthly || yearly
        b.tilFestival.isVisible = !monthly && !yearly && type != Constants.TYPE_REGISTRATION
        if (type != shownType) {
            if (monthly) {
                b.tilPeriod.hint = "Fee for month"
                b.actPeriod.setOptions(
                    monthKeys.map { Fmt.monthLabel(it) },
                    Fmt.monthLabel(presetPeriod?.takeIf { it.length == 7 } ?: Fmt.monthKey(time))
                )
            } else if (yearly) {
                b.tilPeriod.hint = "Fee for year"
                b.actPeriod.setOptions(yearKeys, presetPeriod?.takeIf { it.length == 4 } ?: Dates.yearOf(time).toString())
            }
            shownType = type
        }
        b.tilFestival.hint = if (type == Constants.TYPE_FESTIVAL) "Festival – tap to choose" else "Festival / event (optional)"
        val hundi = type == Constants.TYPE_HUNDI
        b.tilDonor.hint = when {
            hundi -> "Donor name (optional)"
            Constants.needsMember(type) -> "Member – search by name, mobile or house"
            else -> "Donor – search member or type a name"
        }
        b.tilMobile.hint = if (hundi) "Mobile number (optional)" else "Mobile number"
    }

    private fun selectedPeriod(): String? {
        val text = b.actPeriod.text.toString()
        return when (typeText()) {
            Constants.TYPE_MONTHLY -> monthKeys.firstOrNull { Fmt.monthLabel(it) == text }
            Constants.TYPE_YEARLY -> text.takeIf { it in yearKeys }
            else -> null
        }
    }

    private fun updateModeUi() {
        val mode = b.actMode.text.toString()
        b.tilTxn.isVisible = mode != Constants.MODE_CASH
        b.tilTxn.hint = when (mode) {
            Constants.MODE_UPI -> "UPI reference / UTR no."
            Constants.MODE_CHEQUE -> "Cheque number"
            else -> "Bank transaction ID"
        }
    }

    // ---------- save ----------
    private fun save(sendWhatsApp: Boolean) {
        listOf(b.tilDonor, b.tilMobile, b.tilFestival, b.tilAmount, b.tilCollectedBy, b.tilPeriod).forEach { it.error = null }

        val type = typeText()
        val hundi = type == Constants.TYPE_HUNDI
        val mode = b.actMode.text.toString()
        var name = b.etDonor.value()
        val mobile = WhatsApp.localDigits(b.etMobile.value())
        val amount = b.etAmount.value().toDoubleOrNull()
        val festivalText = if (b.tilFestival.isVisible) b.etFestival.value().ifBlank { null } else null
        val txn = if (mode != Constants.MODE_CASH) b.etTxn.value().ifBlank { null } else null
        val collectedBy = b.etCollectedBy.value()
        val period = selectedPeriod()
        val member = selected
        val isFee = Constants.isFeeType(type)

        if (hundi && name.isEmpty()) name = Constants.TYPE_HUNDI

        var ok = true
        if (name.isEmpty()) { b.tilDonor.error = "Enter the donor's name"; ok = false }
        if (Constants.needsMember(type) && member == null) { b.tilDonor.error = "Pick a registered member from the list"; ok = false }
        if (type == Constants.TYPE_REGISTRATION && member != null && member.registrationFee <= 0) {
            b.tilDonor.error = "${member.fullName} has no registration fee set. Edit the member first."; ok = false
        }
        if (isFee && period == null) { b.tilPeriod.error = "Choose the fee period"; ok = false }
        if (type == Constants.TYPE_FESTIVAL && festivalText == null) { b.tilFestival.error = "Choose the festival"; ok = false }
        if (amount == null || amount <= 0) { b.tilAmount.error = "Enter an amount greater than zero"; ok = false }
        if (mobile.isNotEmpty() && mobile.length != 10) { b.tilMobile.error = "Enter a 10-digit mobile number"; ok = false }
        if (sendWhatsApp && mobile.isEmpty() && !hundi) { b.tilMobile.error = "A mobile number is needed to send the WhatsApp receipt"; ok = false }
        if (collectedBy.isEmpty()) { b.tilCollectedBy.error = "Enter who collected this amount"; ok = false }
        if (!ok || amount == null) return

        val now = System.currentTimeMillis()
        val draft = DonationEntity(
            uid = UUID.randomUUID().toString(),
            receiptNumber = "",
            timestamp = time,
            donorUid = member?.uid,
            donorName = name,
            donorMobile = mobile.ifEmpty { null },
            donationType = type,
            festivalName = festivalText,
            feePeriod = if (isFee) period else null,
            amount = amount,
            paymentMode = mode,
            transactionId = txn,
            collectedBy = collectedBy,
            enteredBy = Session.username,
            updatedAt = now,
            note = b.etNote.value().ifBlank { null },
            createdAt = now
        )

        lifecycleScope.launch {
            // Festival Seva must use a festival from the list; offer to add a new one.
            if (festivalText != null) {
                val known = db.festivalDao().byName(festivalText)
                if (known == null && type == Constants.TYPE_FESTIVAL) {
                    MaterialAlertDialogBuilder(this@AddDonationActivity)
                        .setTitle("New festival?")
                        .setMessage("“$festivalText” is not in the festival list. Add it and save this donation?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Add festival") { _, _ ->
                            lifecycleScope.launch {
                                db.festivalDao().upsert(
                                    FestivalEntity(uid = UUID.randomUUID().toString(), name = festivalText, updatedAt = System.currentTimeMillis())
                                )
                                checkDuplicateAndSave(draft, member, period, sendWhatsApp)
                            }
                        }
                        .show()
                    return@launch
                }
                if (known != null) {
                    checkDuplicateAndSave(draft.copy(festivalName = known.name), member, period, sendWhatsApp)
                    return@launch
                }
            }
            checkDuplicateAndSave(draft, member, period, sendWhatsApp)
        }
    }

    private suspend fun checkDuplicateAndSave(draft: DonationEntity, member: MemberEntity?, period: String?, sendWhatsApp: Boolean) {
        val type = draft.donationType
        val already = when {
            member == null -> 0.0
            Constants.isFeeType(type) && period != null -> db.donationDao().feePaidFor(member.uid, period, type)
            type == Constants.TYPE_REGISTRATION -> db.donationDao().paidOfType(member.uid, type)
            else -> 0.0
        }
        if (member != null && already > 0) {
            val what = if (type == Constants.TYPE_REGISTRATION) "the registration fee" else Fmt.periodLabel(period)
            MaterialAlertDialogBuilder(this)
                .setTitle("Already received")
                .setMessage("${Fmt.money(already)} has already been received from ${member.fullName} for $what. Save this as an additional payment?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save anyway") { _, _ -> persist(draft, sendWhatsApp) }
                .show()
        } else {
            persist(draft, sendWhatsApp)
        }
    }

    private fun persist(draft: DonationEntity, sendWhatsApp: Boolean) {
        b.btnSaveWhatsApp.isEnabled = false
        b.btnSaveOnly.isEnabled = false
        lifecycleScope.launch {
            val receiptNo = Numbering.nextReceipt(db, prefs, Dates.yearOf(draft.timestamp))
            val donation = draft.copy(receiptNumber = receiptNo)
            db.donationDao().insert(donation)
            val mobile = donation.donorMobile
            if (sendWhatsApp && mobile != null) {
                toast("Saved · $receiptNo")
                WhatsApp.send(this@AddDonationActivity, mobile, WhatsApp.receiptMessage(prefs, donation))
            } else {
                toast(if (sendWhatsApp) "Saved · $receiptNo (no mobile, receipt not sent)" else "Saved · $receiptNo")
            }
            finish()
        }
    }
}
