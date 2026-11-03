package com.mandir.manager.util

import com.mandir.manager.data.DonationEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun money(v: Double): String = inr.format(v).removeSuffix(".00")

    /** Amount without currency symbol, e.g. "501" or "250.50" (for edit fields). */
    fun plain(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    fun dateTime(ms: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(ms))
    fun date(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(ms))
    fun fileStamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    fun monthKey(ms: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(ms))

    /** "October 2026" for a month key, "Year 2026" for a year key. */
    fun periodLabel(period: String?): String = when {
        period.isNullOrBlank() -> ""
        period.length == 4 -> "Year $period"
        else -> monthLabel(period)
    }

    fun monthLabel(key: String): String {
        val d = runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(key) }.getOrNull() ?: return key
        return SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(d)
    }

    /** "Rupees One Thousand One Only" — Indian numbering (lakh / crore). */
    fun amountInWords(v: Double): String {
        val totalPaise = Math.round(v * 100)
        val rupees = totalPaise / 100
        val paise = (totalPaise % 100).toInt()
        val sb = StringBuilder("Rupees ").append(words(rupees))
        if (paise > 0) sb.append(" and ").append(two(paise)).append(" Paise")
        return sb.append(" Only").toString()
    }

    private val ones = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    )
    private val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    private fun two(n: Int): String =
        if (n < 20) ones[n] else tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""

    private fun three(n: Int): String = listOfNotNull(
        if (n / 100 > 0) ones[n / 100] + " Hundred" else null,
        if (n % 100 > 0) two(n % 100) else null
    ).joinToString(" ")

    private fun words(n: Long): String {
        if (n == 0L) return "Zero"
        var num = n
        val crore = num / 10_000_000; num %= 10_000_000
        val lakh = (num / 100_000).toInt(); num %= 100_000
        val thousand = (num / 1000).toInt(); num %= 1000
        val rest = num.toInt()
        val parts = mutableListOf<String>()
        if (crore > 0) parts += words(crore) + " Crore"
        if (lakh > 0) parts += two(lakh) + " Lakh"
        if (thousand > 0) parts += two(thousand) + " Thousand"
        if (rest > 0) parts += three(rest)
        return parts.joinToString(" ")
    }
}

object Dates {
    fun year(): Int = Calendar.getInstance().get(Calendar.YEAR)
    fun yearOf(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)

    /** Key used to look up which fee rate applied: the month itself, or January for a yearly fee. */
    fun rateKey(period: String): String = if (period.length == 4) "$period-01" else period

    /** Keeps the time of day of [keepTimeOf] but moves it to the local date picked in a MaterialDatePicker (UTC midnight). */
    fun pickedDate(utcMidnight: Long, keepTimeOf: Long = System.currentTimeMillis()): Long {
        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            timeInMillis = keepTimeOf
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    /** Local date → UTC midnight, for preselecting a MaterialDatePicker. */
    fun toPicker(ms: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = ms }
        return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun startOfDay(c: Calendar) = c.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    fun dayRange(ms: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val c = startOfDay(Calendar.getInstance().apply { timeInMillis = ms })
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        return start to c.timeInMillis - 1
    }

    private fun monthStart(key: String): Calendar {
        val d = runCatching { SimpleDateFormat("yyyy-MM", Locale.US).parse(key) }.getOrNull() ?: Date()
        return startOfDay(Calendar.getInstance().apply { time = d; set(Calendar.DAY_OF_MONTH, 1) })
    }

    fun monthRange(key: String): Pair<Long, Long> {
        val c = monthStart(key)
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start to c.timeInMillis - 1
    }

    fun shiftMonth(key: String, delta: Int): String {
        val c = monthStart(key)
        c.add(Calendar.MONTH, delta)
        return Fmt.monthKey(c.timeInMillis)
    }

    /** Year keys offered for yearly fees, newest first. */
    fun yearOptions(): List<String> = (year() + 1 downTo year() - 3).map { it.toString() }

    /** Newest first, e.g. back = 11, ahead = 2 → 14 month keys. */
    fun recentMonths(back: Int, ahead: Int): List<String> {
        val current = Fmt.monthKey()
        return (ahead downTo -back).map { shiftMonth(current, it) }
    }
}

/** Human-readable purpose line used in lists, WhatsApp and PDF. */
fun DonationEntity.purpose(): String = when (donationType) {
    Constants.TYPE_MONTHLY -> "Monthly Fee – " + Fmt.periodLabel(feePeriod)
    Constants.TYPE_YEARLY -> "Yearly Fee – " + (feePeriod ?: "")
    Constants.TYPE_REGISTRATION -> "One-time Registration Fee"
    Constants.TYPE_FESTIVAL -> "Festival Seva – " + (festivalName ?: "")
    else -> festivalName?.takeIf { it.isNotBlank() }?.let { "$donationType – $it" } ?: donationType
}
