package com.mandir.manager.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "members", indices = [Index(value = ["recordId"], unique = true)])
data class MemberEntity(
    @PrimaryKey(autoGenerate = true) val memberId: Long = 0,
    val recordId: String,              // e.g. M-2026-001
    val fullName: String,
    val category: String,              // Committee Member / Colony Resident / External Devotee
    val designation: String?,          // President, Treasurer, ...
    val mobileNumber: String,          // 10 digits, no country code
    val houseNo: String?,
    val familyCount: Int,
    val monthlyFee: Double,            // 0 = does not pay a monthly fee
    val createdAt: Long = System.currentTimeMillis()
) {
    // Used by the donor search dropdown: lets you match by name, mobile or house number.
    override fun toString(): String =
        listOfNotNull(fullName, mobileNumber, houseNo?.takeIf { it.isNotBlank() }?.let { "House $it" })
            .joinToString(" · ")
}

@Entity(
    tableName = "donations",
    indices = [Index(value = ["receiptNumber"], unique = true), Index("donorId"), Index("timestamp")]
)
data class DonationEntity(
    @PrimaryKey(autoGenerate = true) val donationId: Long = 0,
    val receiptNumber: String,         // e.g. REC-2026-00012
    val timestamp: Long,               // epoch millis
    val donorId: Long?,                // null for walk-in donors / hundi
    val donorName: String,
    val donorMobile: String?,
    val donationType: String,          // General Donation / Monthly Fee / Festival Seva / Hundi
    val festivalName: String?,
    val feeMonth: String?,             // yyyy-MM, only for Monthly Fee
    val amount: Double,
    val paymentMode: String,           // Cash / UPI / Bank Transfer / Cheque
    val transactionId: String?,
    val collectedBy: String
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["voucherNumber"], unique = true), Index("timestamp")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val expenseId: Long = 0,
    val voucherNumber: String,         // e.g. V-2026-0004
    val timestamp: Long,
    val category: String,              // Samagri, Tent, Electricity, Pandit Ji ...
    val description: String,
    val amountPaid: Double,
    val paidTo: String,
    val paymentMode: String,
    val festivalName: String?,         // lets you see festival-wise profit / loss
    val billImagePath: String?
)

/** Generic "label + sum" row for report queries. */
data class LabelTotal(val label: String?, val total: Double)

/** A member whose monthly fee for a given month is not (fully) received. */
data class PendingFee(
    @Embedded val member: MemberEntity,
    val paid: Double
) {
    val due: Double get() = (member.monthlyFee - paid).coerceAtLeast(0.0)
}
