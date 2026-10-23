package com.mandir.manager.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.mandir.manager.databinding.ActivitySettingsBinding
import com.mandir.manager.util.DataExport
import com.mandir.manager.util.Share
import com.mandir.manager.util.toast
import com.mandir.manager.util.value
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        enableUp()

        if (savedInstanceState == null) {
            b.etMandirName.setText(prefs.mandirName)
            b.etGreeting.setText(prefs.greeting)
            b.etSignatory.setText(prefs.signatory)
            b.etUpiId.setText(prefs.upiId)
            b.etCollector.setText(prefs.collectorName)
            b.etDefaultFee.setText(prefs.defaultMonthlyFee)
        }

        b.btnSave.setOnClickListener {
            b.etMandirName.value().takeIf { it.isNotEmpty() }?.let { prefs.mandirName = it }
            b.etGreeting.value().takeIf { it.isNotEmpty() }?.let { prefs.greeting = it }
            b.etSignatory.value().takeIf { it.isNotEmpty() }?.let { prefs.signatory = it }
            prefs.upiId = b.etUpiId.value()
            prefs.collectorName = b.etCollector.value()
            prefs.defaultMonthlyFee = b.etDefaultFee.value().ifEmpty { "0" }
            toast("Settings saved")
            finish()
        }

        b.btnBackup.setOnClickListener {
            lifecycleScope.launch {
                val file = DataExport.backup(this@SettingsActivity)
                Share.files(this@SettingsActivity, listOf(file), "application/octet-stream", "Mandir Manager backup")
            }
        }
    }
}
