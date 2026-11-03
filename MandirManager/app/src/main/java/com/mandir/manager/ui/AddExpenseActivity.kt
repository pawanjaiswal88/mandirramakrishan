package com.mandir.manager.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.util.Dates
import com.mandir.manager.data.ExpenseEntity
import com.mandir.manager.databinding.ActivityAddExpenseBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Numbering
import com.mandir.manager.util.Session
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.Images
import com.mandir.manager.util.Share
import com.mandir.manager.util.setOptions
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AddExpenseActivity : BaseActivity() {

    private lateinit var b: ActivityAddExpenseBinding
    private var time = System.currentTimeMillis()
    private var billPath: String? = null
    private var cameraFile: File? = null
    private val salaryMonths = Dates.recentMonths(back = 12, ahead = 1)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) importBill(uri)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val f = cameraFile
        if (saved && f != null && f.length() > 0) setBill(f) else f?.delete()
        cameraFile = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        savedInstanceState?.let { s ->
            time = s.getLong("time", time)
            s.getString("camera")?.let { cameraFile = File(it) }
            s.getString("bill")?.let { setBill(File(it)) }
        }

        b.actCategory.setOptions(Constants.EXPENSE_CATEGORIES, Constants.EXPENSE_CATEGORIES.first())
        b.actMode.setOptions(Constants.PAYMENT_MODES, Constants.MODE_CASH)
        b.actSalaryMonth.setOptions(salaryMonths.map { Fmt.monthLabel(it) }, Fmt.monthLabel(Fmt.monthKey()))
        b.actCategory.setOnItemClickListener { _, _, _, _ -> updateCategoryUi() }
        updateCategoryUi()
        b.etDate.setText(Fmt.date(time))
        b.etDate.setOnClickListener { pickDate() }
        b.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        b.btnCamera.setOnClickListener { openCamera() }
        b.btnRemoveBill.setOnClickListener { clearBill() }
        b.btnSave.setOnClickListener { save() }

        lifecycleScope.launch {
            val festivals = db.festivalDao().activeNames()
            b.etFestival.setAdapter(ArrayAdapter(this@AddExpenseActivity, android.R.layout.simple_dropdown_item_1line, festivals))
            b.etFestival.setOnClickListener { if (festivals.isNotEmpty()) b.etFestival.showDropDown() }
            val payees = (listOf("Pandit Ji") + db.expenseDao().payees(Constants.EXP_SALARY)).distinctBy { it.lowercase() }
            b.etPaidTo.setAdapter(ArrayAdapter(this@AddExpenseActivity, android.R.layout.simple_dropdown_item_1line, payees))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("time", time)
        outState.putString("camera", cameraFile?.absolutePath)
        outState.putString("bill", billPath)
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Expense date")
            .setSelection(Dates.toPicker(time))
            .build()
        picker.addOnPositiveButtonClickListener { utc ->
            time = Dates.pickedDate(utc)
            b.etDate.setText(Fmt.date(time))
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun isSalary() = b.actCategory.text.toString() == Constants.EXP_SALARY

    /** Monthly salary: pick the month it is for, and who is paid (Pandit Ji or any staff). */
    private fun updateCategoryUi() {
        val salary = isSalary()
        b.tilSalaryMonth.isVisible = salary
        b.tilPaidTo.hint = if (salary) "Salary paid to (e.g. Pandit Ji, Safai staff)" else "Paid to (shop / person)"
        b.tilDescription.hint = if (salary) "Note (optional)" else "What was it for?"
    }

    private fun salaryMonth(): String? =
        if (isSalary()) salaryMonths.firstOrNull { Fmt.monthLabel(it) == b.actSalaryMonth.text.toString() } else null

    private fun newBillFile(): File =
        File(File(filesDir, "bills").apply { mkdirs() }, "bill_${System.currentTimeMillis()}.jpg")

    private fun openCamera() {
        val f = newBillFile()
        cameraFile = f
        try {
            takePhoto.launch(Share.uri(this, f))
        } catch (e: ActivityNotFoundException) {
            cameraFile = null
            toast("No camera app found. Use From gallery instead.")
        }
    }

    private fun importBill(uri: Uri) {
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                val out = newBillFile()
                contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                out
            }
            if (f.length() > 0) setBill(f) else { f.delete(); toast("Could not read that image") }
        }
    }

    private fun setBill(f: File) {
        billPath?.takeIf { it != f.absolutePath }?.let { File(it).delete() }
        billPath = f.absolutePath
        b.ivBill.setImageBitmap(Images.thumb(f.absolutePath))
        b.ivBill.isVisible = true
        b.btnRemoveBill.isVisible = true
    }

    private fun clearBill() {
        billPath?.let { File(it).delete() }
        billPath = null
        b.ivBill.setImageDrawable(null)
        b.ivBill.isVisible = false
        b.btnRemoveBill.isVisible = false
    }

    private fun save() {
        listOf(b.tilDescription, b.tilAmount, b.tilPaidTo).forEach { it.error = null }
        val category = b.actCategory.text.toString()
        val month = salaryMonth()
        val description = b.etDescription.value().ifEmpty {
            if (month != null) "Salary for ${Fmt.monthLabel(month)}" else category
        }
        val amount = b.etAmount.value().toDoubleOrNull()
        val paidTo = b.etPaidTo.value()

        var ok = true
        if (amount == null || amount <= 0) { b.tilAmount.error = "Enter an amount greater than zero"; ok = false }
        if (paidTo.isEmpty()) { b.tilPaidTo.error = "Enter who was paid"; ok = false }
        if (!ok || amount == null) return

        lifecycleScope.launch {
            if (month != null) {
                val already = db.expenseDao().salaryPaid(Constants.EXP_SALARY, month, paidTo)
                if (already > 0) {
                    MaterialAlertDialogBuilder(this@AddExpenseActivity)
                        .setTitle("Salary already paid")
                        .setMessage("${Fmt.money(already)} was already paid to $paidTo for ${Fmt.monthLabel(month)}. Save this as an extra payment?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save anyway") { _, _ -> persist(category, description, amount, paidTo, month) }
                        .show()
                    return@launch
                }
            }
            persist(category, description, amount, paidTo, month)
        }
    }

    private fun persist(category: String, description: String, amount: Double, paidTo: String, month: String?) {
        b.btnSave.isEnabled = false
        lifecycleScope.launch {
            val voucher = Numbering.nextVoucher(db, prefs, Dates.yearOf(time))
            db.expenseDao().upsert(
                ExpenseEntity(
                    uid = UUID.randomUUID().toString(),
                    voucherNumber = voucher,
                    timestamp = time,
                    category = category,
                    description = description,
                    amountPaid = amount,
                    paidTo = paidTo,
                    paymentMode = b.actMode.text.toString(),
                    festivalName = b.etFestival.value().ifBlank { null },
                    billImagePath = billPath,
                    enteredBy = Session.username,
                    updatedAt = System.currentTimeMillis(),
                    salaryMonth = month
                )
            )
            billPath = null // now owned by the saved record
            toast("Expense saved · $voucher")
            finish()
        }
    }
}
