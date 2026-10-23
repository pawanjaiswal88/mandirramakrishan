package com.mandir.manager.util

import android.content.Context

/** Committee details used on receipts and messages. Editable from Settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mandir_prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    private fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    var mandirName: String
        get() = str("mandir_name", "Colony Mandir Committee")
        set(v) = put("mandir_name", v)

    var greeting: String
        get() = str("greeting", "Jai Shree Krishna")
        set(v) = put("greeting", v)

    var signatory: String
        get() = str("signatory", "Mandir Committee")
        set(v) = put("signatory", v)

    var collectorName: String
        get() = str("collector", "")
        set(v) = put("collector", v)

    var defaultMonthlyFee: String
        get() = str("default_fee", "101")
        set(v) = put("default_fee", v)

    var upiId: String
        get() = str("upi_id", "")
        set(v) = put("upi_id", v)
}
