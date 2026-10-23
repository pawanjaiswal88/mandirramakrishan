package com.mandir.manager.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.data.DonationEntity
import com.mandir.manager.data.MemberEntity
import com.mandir.manager.databinding.ActivityAddDonationBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.WhatsApp
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.util.Locale

class AddDonationActivity : BaseActivity() {

    companion object {
        const val EXTRA_MEMBER_ID = "member_id"
        const val EXTRA_TYPE = "donation_type"
        const val EXTRA_MONTH = "fee_month"
        private const val STATE_MEMBER = "selected_member"
    }

    private lateinit var b: ActivityAddDonationBinding
    private var selected: MemberEntity? = null
    private val monthKeys = Dates.recentMonths(back = 11, ahead = 2).toMutableList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddDonationBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        val presetMonth = intent.getStringExtra(EXTRA_MONTH)
        if (presetMonth != null && presetMonth !in monthKeys) monthKeys.add(presetMonth)

        b.actType.setOptions(Constants.DONATION_TYPES, intent.getStringExtra(EXTRA_TYPE) ?: Constants.TYPE_GENERAL)
        b.actMode.setOptions(Constants.PAYMENT_MODES, Constants.MODE_CASH)
        b.actMonth.setOptions(monthKeys.map { Fmt.monthLabel(it) }, Fmt.monthLabel(presetMonth ?: Fmt.monthKey()))
        b.etCollectedBy.setText(prefs.collectorName)

        b.actType.setOnItemClickListener { _, _, _, _ -> updateTypeUi(); prefillFee() }
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
        b.btnSaveWhatsApp.setOnClickListener { save(sendWhatsApp = true) }
        b.btnSaveOnly.setOnClickListener { save(sendWhatsApp = false) }
        updateTypeUi()
        updateModeUi()

        val restoreId = savedInstanceState?.getLong(STATE_MEMBER, -1L) ?: intent.getLongExtra(EXTRA_MEMBER_ID, -1L)
        lifecycleScope.launch {
            val members = db.memberDao().getAll()
            b.etDonor.setAdapter(ArrayAdapter(this@AddDonationActivity, android.R.layout.simple_dropdown_item_1line, members))
            val festivals = (db.donationDao().festivalNames() + db.expenseDao().festivalNames())
                .distinctBy { it.lowercase() }.sorted()
            b.etFestival.setAdapter(ArrayAdapter(this@AddDonationActivity, android.R.layout.simple_dropdown_item_1line, festivals))
            members.firstOrNull { it.memberId == restoreId }?.let { selectMember(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_MEMBER, selected?.memberId ?: -1L)
    }

    private fun selectMember(m: MemberEntity) {
        selected = m
        b.etDonor.setText(m.fullName, false)
        b.etDonor.setSelection(m.fullName.length)
        b.etMobile.setText(m.mobileNumber)
        b.tilDonor.error = null
        showMemberInfo()
        prefillFee()
    }

    private fun showMemberInfo() {
        val m = selected
        b.tvDonorInfo.isVisible = m != null
        if (m != null) {
            b.tvDonorInfo.text = "✓ " + listOfNotNull(
                m.recordId,
                m.designation ?: m.category,
                m.houseNo?.takeIf { it.isNotBlank() }?.let { "House $it" }
            ).joinToString(" · ")
        }
    }

    private fun prefillFee() {
        val m = selected ?: return
        if (b.actType.text.toString() == Constants.TYPE_MONTHLY && m.monthlyFee > 0 && b.etAmount.value().isEmpty()) {
            b.etAmount.setText(Fmt.plain(m.monthlyFee))
        }
    }

    private fun updateTypeUi() {
        val type = b.actType.text.toString()
        b.tilMonth.isVisible = type == Constants.TYPE_MONTHLY
        b.tilFestival.isVisible = type != Constants.TYPE_MONTHLY
        b.tilFestival.hint = if (type == Constants.TYPE_FESTIVAL) "Festival / event name" else "Festival / event (optional)"
        b.tilDonor.hint = when (type) {
            Constants.TYPE_HUNDI -> "Donor name (optional)"
            Constants.TYPE_MONTHLY -> "Member – search by name, mobile or house"
            else -> "Donor – search member or type a name"
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

    private fun save(sendWhatsApp: Boolean) {
        listOf(b.tilDonor, b.tilMobile, b.tilFestival, b.tilAmount, b.tilCollectedBy).forEach { it.error = null }

        val type = b.actType.text.toString()
        val mode = b.actMode.text.toString()
        var name = b.etDonor.value()
        val mobile = WhatsApp.localDigits(b.etMobile.value())
        val amount = b.etAmount.value().toDoubleOrNull()
        val festival = if (b.tilFestival.isVisible) b.etFestival.value().ifBlank { null } else null
        val txn = if (mode != Constants.MODE_CASH) b.etTxn.value().ifBlank { null } else null
        val collectedBy = b.etCollectedBy.value()
        val month = if (type == Constants.TYPE_MONTHLY)
            monthKeys.firstOrNull { Fmt.monthLabel(it) == b.actMonth.text.toString() } else null
        val member = selected

        if (type == Constants.TYPE_HUNDI && name.isEmpty()) name = Constants.TYPE_HUNDI

        var ok = true
        if (name.isEmpty()) { b.tilDonor.error = "Enter the donor's name"; ok = false }
        if (type == Constants.TYPE_MONTHLY && member == null) { b.tilDonor.error = "Pick a registered member from the list for a monthly fee"; ok = false }
        if (type == Constants.TYPE_FESTIVAL && festival == null) { b.tilFestival.error = "Enter the festival or event name"; ok = false }
        if (amount == null || amount <= 0) { b.tilAmount.error = "Enter an amount greater than zero"; ok = false }
        if (mobile.isNotEmpty() && mobile.length != 10) { b.tilMobile.error = "Enter a 10-digit mobile number"; ok = false }
        if (sendWhatsApp && mobile.isEmpty()) { b.tilMobile.error = "A mobile number is needed to send the WhatsApp receipt"; ok = false }
        if (collectedBy.isEmpty()) { b.tilCollectedBy.error = "Enter who collected this amount"; ok = false }
        if (!ok || amount == null) return

        prefs.collectorName = collectedBy
        val draft = DonationEntity(
            receiptNumber = "",
            timestamp = System.currentTimeMillis(),
            donorId = member?.memberId,
            donorName = name,
            donorMobile = mobile.ifEmpty { null },
            donationType = type,
            festivalName = festival,
            feeMonth = month,
            amount = amount,
            paymentMode = mode,
            transactionId = txn,
            collectedBy = collectedBy
        )

        lifecycleScope.launch {
            if (member != null && month != null) {
                val already = db.donationDao().feePaidFor(member.memberId, month, Constants.TYPE_MONTHLY)
                if (already > 0) {
                    MaterialAlertDialogBuilder(this@AddDonationActivity)
                        .setTitle("Fee already recorded")
                        .setMessage("${Fmt.money(already)} has already been received from ${member.fullName} for ${Fmt.monthLabel(month)}. Save this as an additional payment?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save anyway") { _, _ -> persist(draft, sendWhatsApp) }
                        .show()
                    return@launch
                }
            }
            persist(draft, sendWhatsApp)
        }
    }

    private fun persist(draft: DonationEntity, sendWhatsApp: Boolean) {
        b.btnSaveWhatsApp.isEnabled = false
        b.btnSaveOnly.isEnabled = false
        lifecycleScope.launch {
            val dao = db.donationDao()
            val receiptNo = "REC-${Dates.year()}-" + String.format(Locale.US, "%05d", dao.maxId() + 1)
            val donation = draft.copy(receiptNumber = receiptNo)
            dao.insert(donation)
            toast("Saved · $receiptNo")
            val mobile = donation.donorMobile
            if (sendWhatsApp && mobile != null) {
                WhatsApp.send(this@AddDonationActivity, mobile, WhatsApp.receiptMessage(prefs, donation))
            }
            finish()
        }
    }
}
