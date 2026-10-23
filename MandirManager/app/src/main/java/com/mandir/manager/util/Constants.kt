package com.mandir.manager.util

object Constants {
    const val CAT_COMMITTEE = "Committee Member"
    const val CAT_RESIDENT = "Colony Resident"
    const val CAT_EXTERNAL = "External Devotee"
    val MEMBER_CATEGORIES = listOf(CAT_RESIDENT, CAT_COMMITTEE, CAT_EXTERNAL)

    val DESIGNATIONS = listOf("President", "Vice President", "Secretary", "Treasurer", "Joint Secretary", "Member")

    const val TYPE_GENERAL = "General Donation"
    const val TYPE_MONTHLY = "Monthly Fee"
    const val TYPE_FESTIVAL = "Festival Seva"
    const val TYPE_HUNDI = "Hundi / Daan Peti"
    val DONATION_TYPES = listOf(TYPE_GENERAL, TYPE_MONTHLY, TYPE_FESTIVAL, TYPE_HUNDI)

    const val MODE_CASH = "Cash"
    const val MODE_UPI = "UPI"
    const val MODE_BANK = "Bank Transfer"
    const val MODE_CHEQUE = "Cheque"
    val PAYMENT_MODES = listOf(MODE_CASH, MODE_UPI, MODE_BANK, MODE_CHEQUE)

    val EXPENSE_CATEGORIES = listOf(
        "Puja Samagri", "Prasad / Bhog", "Pandit Ji Dakshina", "Flowers & Garlands",
        "Tent & Decoration", "Sound & Lighting", "Electricity", "Water",
        "Cleaning / Staff", "Repair & Maintenance", "Printing & Banners", "Other"
    )
}
