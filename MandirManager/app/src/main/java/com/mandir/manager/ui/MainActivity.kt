package com.mandir.manager.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.mandir.manager.databinding.ActivityMainBinding
import com.mandir.manager.util.Constants
import com.mandir.manager.util.Dates
import com.mandir.manager.util.Fmt
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnNewDonation.setOnClickListener { open(AddDonationActivity::class.java) }
        b.btnAddExpense.setOnClickListener { open(AddExpenseActivity::class.java) }
        b.btnMembers.setOnClickListener { open(MembersActivity::class.java) }
        b.btnDonations.setOnClickListener { open(DonationsActivity::class.java) }
        b.btnExpenses.setOnClickListener { open(ExpensesActivity::class.java) }
        b.btnReports.setOnClickListener { open(ReportsActivity::class.java) }
        b.btnSettings.setOnClickListener { open(SettingsActivity::class.java) }
        b.cardPending.setOnClickListener { open(ReportsActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun refresh() {
        b.tvMandirName.text = prefs.mandirName
        b.tvToday.text = Fmt.date(System.currentTimeMillis())
        lifecycleScope.launch {
            val donations = db.donationDao()
            val expenses = db.expenseDao()
            val (dayStart, dayEnd) = Dates.dayRange()
            val month = Fmt.monthKey()
            val (monthStart, monthEnd) = Dates.monthRange(month)

            b.tvTodayTotal.text = Fmt.money(donations.totalBetween(dayStart, dayEnd))
            val modes = donations.modeTotals(dayStart, dayEnd)
            b.tvTodayModes.text = if (modes.isEmpty()) "No entries yet today"
            else modes.joinToString("    ") { "${it.label}: ${Fmt.money(it.total)}" }

            b.tvMonthIncome.text = Fmt.money(donations.totalBetween(monthStart, monthEnd))
            b.tvMonthExpense.text = Fmt.money(expenses.totalBetween(monthStart, monthEnd))

            val all = 0L to Long.MAX_VALUE
            val balance = donations.totalBetween(all.first, all.second) - expenses.totalBetween(all.first, all.second)
            val cash = donations.totalByModeBetween(Constants.MODE_CASH, all.first, all.second) -
                expenses.totalByModeBetween(Constants.MODE_CASH, all.first, all.second)
            b.tvBalance.text = Fmt.money(balance)
            b.tvCashInHand.text = "Cash in hand: ${Fmt.money(cash)}   ·   In bank / UPI: ${Fmt.money(balance - cash)}"

            val pending = db.memberDao().pendingFees(month, Constants.TYPE_MONTHLY)
            b.tvPending.text = if (pending.isEmpty()) "All fees for ${Fmt.monthLabel(month)} received 🙏"
            else "${pending.size} pending for ${Fmt.monthLabel(month)} · ${Fmt.money(pending.sumOf { it.due })} due"
        }
    }
}
