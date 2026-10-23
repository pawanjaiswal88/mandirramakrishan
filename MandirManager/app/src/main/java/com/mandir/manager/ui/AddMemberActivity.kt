package com.mandir.manager.ui

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.data.MemberEntity
import com.mandir.manager.databinding.ActivityAddMemberBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.WhatsApp
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch
import java.util.Locale

class AddMemberActivity : BaseActivity() {

    companion object { const val EXTRA_ID = "member_id" }

    private lateinit var b: ActivityAddMemberBinding
    private var editing: MemberEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddMemberBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.actCategory.setOptions(Constants.MEMBER_CATEGORIES, Constants.CAT_RESIDENT)
        b.actDesignation.setOptions(Constants.DESIGNATIONS, null)
        b.etFee.setText(prefs.defaultMonthlyFee)
        b.etFamily.setText("1")
        b.actCategory.setOnItemClickListener { _, _, _, _ -> onCategoryChanged() }
        b.btnSave.setOnClickListener { save() }
        updateDesignation()

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id > 0) {
            title = "Edit member"
            b.btnSave.text = "Save changes"
            lifecycleScope.launch {
                editing = db.memberDao().getById(id)
                if (savedInstanceState == null) editing?.let { fill(it) }
            }
        }
    }

    private fun fill(m: MemberEntity) {
        b.etName.setText(m.fullName)
        b.actCategory.setText(m.category, false)
        b.actDesignation.setText(m.designation ?: "", false)
        b.etMobile.setText(m.mobileNumber)
        b.etHouse.setText(m.houseNo ?: "")
        b.etFamily.setText(m.familyCount.toString())
        b.etFee.setText(Fmt.plain(m.monthlyFee))
        updateDesignation()
    }

    private fun updateDesignation() {
        b.tilDesignation.isVisible = b.actCategory.text.toString() == Constants.CAT_COMMITTEE
    }

    private fun onCategoryChanged() {
        updateDesignation()
        val fee = b.etFee.value()
        if (b.actCategory.text.toString() == Constants.CAT_EXTERNAL) b.etFee.setText("0")
        else if (fee.isEmpty() || fee == "0") b.etFee.setText(prefs.defaultMonthlyFee)
    }

    private fun save() {
        listOf(b.tilName, b.tilMobile, b.tilFee).forEach { it.error = null }
        val name = b.etName.value()
        val category = b.actCategory.text.toString()
        val designation = if (category == Constants.CAT_COMMITTEE) b.actDesignation.text.toString().ifBlank { null } else null
        val mobile = WhatsApp.localDigits(b.etMobile.value())
        val house = b.etHouse.value().ifBlank { null }
        val family = b.etFamily.value().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val fee = b.etFee.value().ifEmpty { "0" }.toDoubleOrNull()

        var ok = true
        if (name.isEmpty()) { b.tilName.error = "Enter the member's name"; ok = false }
        if (mobile.length != 10) { b.tilMobile.error = "Enter a 10-digit mobile number"; ok = false }
        if (fee == null || fee < 0) { b.tilFee.error = "Enter a fee of 0 or more"; ok = false }
        if (!ok || fee == null) return

        lifecycleScope.launch {
            val dao = db.memberDao()
            val write = suspend {
                val e = editing
                if (e == null) {
                    val record = "M-${Dates.year()}-" + String.format(Locale.US, "%03d", dao.maxId() + 1)
                    dao.insert(
                        MemberEntity(
                            recordId = record, fullName = name, category = category, designation = designation,
                            mobileNumber = mobile, houseNo = house, familyCount = family, monthlyFee = fee
                        )
                    )
                    toast("Member added · $record")
                } else {
                    dao.update(
                        e.copy(
                            fullName = name, category = category, designation = designation,
                            mobileNumber = mobile, houseNo = house, familyCount = family, monthlyFee = fee
                        )
                    )
                    toast("Member updated")
                }
                finish()
            }

            if (dao.countWithMobile(mobile, editing?.memberId ?: 0L) > 0) {
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
}
