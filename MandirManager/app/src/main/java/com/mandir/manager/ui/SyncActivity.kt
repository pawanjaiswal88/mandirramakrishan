package com.mandir.manager.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mandir.manager.databinding.ActivitySyncBinding
import com.mandir.manager.util.Backup
import com.mandir.manager.util.CsvExport
import com.mandir.manager.util.DriveAuthorizer
import com.mandir.manager.util.Fmt
import com.mandir.manager.util.PhoneBackup
import com.mandir.manager.util.PhoneMerge
import com.mandir.manager.util.Session
import com.mandir.manager.util.Share
import com.mandir.manager.util.SyncManager
import com.mandir.manager.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SyncActivity : BaseActivity() {

    private lateinit var b: ActivitySyncBinding

    private val drive = DriveAuthorizer(this) { token -> runSync(token) }
    private val drivePick = DriveAuthorizer(this) { token -> choosePhone(token) }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) export(uri)
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) import(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!guard()) return
        b = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        b.swAuto.isChecked = prefs.autoSync
        b.swAuto.setOnCheckedChangeListener { _, on -> prefs.autoSync = on }
        b.btnSync.setOnClickListener { drive.start() }
        b.btnExport.setOnClickListener { exportFile.launch("mandir-backup-${Fmt.fileStamp()}.mbk") }
        b.btnImport.isVisible = Session.isAdmin
        b.btnMergePhone.isVisible = Session.isAdmin
        b.btnMergePhone.setOnClickListener { drivePick.start() }
        b.btnImport.setOnClickListener { importFile.launch(arrayOf("*/*")) }
        b.btnCsv.setOnClickListener {
            lifecycleScope.launch {
                Share.files(this@SyncActivity, CsvExport.create(this@SyncActivity), "text/csv", "${prefs.mandirName} accounts")
            }
        }
        showStatus()
    }

    private fun showStatus() {
        b.tvStatus.text = prefs.lastSyncMessage
        b.tvDevice.text = "This phone's code: ${prefs.deviceCode}"
    }

    private fun busy(on: Boolean) {
        b.progress.isVisible = on
        b.btnSync.isEnabled = !on
        b.btnImport.isEnabled = !on
    }

    private fun runSync(token: String) {
        busy(true)
        lifecycleScope.launch {
            val result = runCatching { SyncManager.sync(applicationContext, token) }
            busy(false)
            result.onSuccess { toast("Sync complete") }.onFailure { toast("Sync failed: ${it.message}") }
            showStatus()
        }
    }

    /** Admin picks one phone's Drive copy; only that copy is merged into this phone. */
    private fun choosePhone(token: String) {
        busy(true)
        lifecycleScope.launch {
            val phones = runCatching { PhoneMerge.list(applicationContext, token) }
            busy(false)
            val list = phones.getOrElse { toast("Could not read Google Drive: ${it.message}"); return@launch }
            if (list.isEmpty()) { toast("No other phone has uploaded to Drive yet"); return@launch }
            val labels = list.map { "Phone ${it.deviceCode} · updated ${it.modified} UTC · ${it.sizeKb} KB" }.toTypedArray()
            MaterialAlertDialogBuilder(this@SyncActivity)
                .setTitle("Merge which phone?")
                .setItems(labels) { _, which -> previewAndMerge(token, list[which]) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun previewAndMerge(token: String, phone: PhoneBackup) {
        busy(true)
        lifecycleScope.launch {
            val snap = runCatching { PhoneMerge.download(token, phone.fileId) }
            busy(false)
            val s = snap.getOrElse { toast("Download failed: ${it.message}"); return@launch }
            MaterialAlertDialogBuilder(this@SyncActivity)
                .setTitle("Merge phone ${s.deviceCode}?")
                .setMessage(
                    "Backed up ${Fmt.dateTime(s.exportedAt)}\n\n" +
                        "${s.donations.size} donations · ${s.expenses.size} expenses · ${s.members.size} members · " +
                        "${s.users.size} users · ${s.festivals.size} festivals · ${s.deletionLog.size} deletion history entries\n\n" +
                        "New records are added. A record that exists on both phones is updated only if that phone's copy is newer. " +
                        "Nothing on this phone is deleted, except donations that were deleted through the 3-approval process."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Merge") { _, _ ->
                    busy(true)
                    lifecycleScope.launch {
                        val r = runCatching { Backup.merge(applicationContext, s) }
                        busy(false)
                        r.onSuccess { toast("Merged phone ${s.deviceCode} · $it update(s)") }
                            .onFailure { toast("Merge failed: ${it.message}") }
                    }
                }
                .show()
        }
    }

    private fun export(uri: Uri) {
        lifecycleScope.launch {
            val ok = runCatching {
                val bytes = Backup.encode(Backup.snapshot(applicationContext))
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot write")
                }
            }.isSuccess
            toast(if (ok) "Backup saved" else "Could not save the backup")
        }
    }

    private fun import(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Import this backup?")
            .setMessage("Its entries are merged into this phone. Nothing newer is overwritten and deletion history is kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                busy(true)
                lifecycleScope.launch {
                    val result = runCatching {
                        val bytes = withContext(Dispatchers.IO) {
                            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read file")
                        }
                        Backup.merge(applicationContext, Backup.decode(bytes))
                    }
                    busy(false)
                    result.onSuccess { toast("Imported · $it update(s)") }
                        .onFailure { toast("This file is not a Mandir Manager backup") }
                }
            }
            .show()
    }
}
