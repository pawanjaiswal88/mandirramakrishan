package com.mandir.manager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mandir.manager.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Share {
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun files(context: Context, files: List<File>, mime: String, title: String) {
        val uris = ArrayList(files.map { uri(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = mime
        intent.putExtra(Intent.EXTRA_SUBJECT, title)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title))
    }
}

object DataExport {

    /** Writes donations.csv and expenses.csv (Excel-friendly, UTF-8 with BOM). */
    suspend fun csv(context: Context): List<File> = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = Fmt.fileStamp()

        val donations = File(dir, "donations-$stamp.csv")
        donations.bufferedWriter().use { w ->
            w.write("\uFEFF")
            w.appendLine("Receipt No,Date,Donor,Mobile,Type,Festival,Fee Month,Amount,Payment Mode,Reference,Collected By")
            db.donationDao().getAll().forEach { d ->
                w.appendLine(
                    listOf(
                        d.receiptNumber, Fmt.dateTime(d.timestamp), d.donorName, d.donorMobile, d.donationType,
                        d.festivalName, d.feeMonth, Fmt.plain(d.amount), d.paymentMode, d.transactionId, d.collectedBy
                    ).joinToString(",") { cell(it) }
                )
            }
        }

        val expenses = File(dir, "expenses-$stamp.csv")
        expenses.bufferedWriter().use { w ->
            w.write("\uFEFF")
            w.appendLine("Voucher No,Date,Category,Description,Amount,Paid To,Payment Mode,Festival,Bill Attached")
            db.expenseDao().getAll().forEach { e ->
                w.appendLine(
                    listOf(
                        e.voucherNumber, Fmt.date(e.timestamp), e.category, e.description, Fmt.plain(e.amountPaid),
                        e.paidTo, e.paymentMode, e.festivalName, if (e.billImagePath != null) "Yes" else "No"
                    ).joinToString(",") { cell(it) }
                )
            }
        }
        listOf(donations, expenses)
    }

    /** Copies the SQLite database to a shareable file (save it to Drive / WhatsApp yourself). */
    suspend fun backup(context: Context): File = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val src = context.getDatabasePath(AppDatabase.NAME)
        val dir = File(context.cacheDir, "backup").apply { mkdirs() }
        val out = File(dir, "mandir-backup-${Fmt.fileStamp()}.db")
        src.copyTo(out, overwrite = true)
        out
    }

    private fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
