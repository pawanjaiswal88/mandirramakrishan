package com.mandir.manager.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Every record uses a random UUID as its key so that records created on different
 * phones never clash when they are merged through Google Drive or a backup file.
 * updatedAt decides which copy wins when the same record was edited on two phones.
 */

@Serializable
@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey val uid: String,
    val username: String,          // stored lowercase
    val fullName: String,
    val passwordHash: String,      // PBKDF2-SHA256, never the plain password
    val salt: String,
    val role: String,              // Admin / Collector
    val isApprover: Boolean,       // may approve donation deletions (max 5)
    val active: Boolean = true,    // false = removed (kept so history still shows the name)
    val updatedAt: Long
)

@Serializable
@Entity(tableName = "members", indices = [Index(value = ["recordId"], unique = true)])
data class MemberEntity(
    @PrimaryKey val uid: String,
    val recordId: String,          // e.g. M-A-001
    val fullName: String,
    val category: String,          // Committee Member / Colony Resident / External Devotee
    val designation: String?,
    val mobileNumber: String,
    val houseNo: String?,
    val familyCount: Int,
    val feePlan: String,           // current plan: No fee / Monthly / Yearly
    val feeAmount: Double,         // current amount; history with effective dates is in member_fee_rates
    val registrationFee: Double = 0.0, // one-time committee registration fee (never repeats)
    val deleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun toString(): String =
        listOfNotNull(fullName, mobileNumber, houseNo?.takeIf { it.isNotBlank() }?.let { "House $it" })
            .joinToString(" · ")
}

@Serializable
@Entity(
    tableName = "donations",
    indices = [Index(value = ["receiptNumber"], unique = true), Index("donorUid"), Index("timestamp")]
)
data class DonationEntity(
    @PrimaryKey val uid: String,
    val receiptNumber: String,     // REC-2026-A-0001 (restarts every year)
    val timestamp: Long,
    val donorUid: String?,
    val donorName: String,
    val donorMobile: String?,
    val donationType: String,
    val festivalName: String?,
    val feePeriod: String?,        // "2026-10" for a monthly fee, "2026" for a yearly fee
    val amount: Double,
    val paymentMode: String,
    val transactionId: String?,
    val collectedBy: String,
    val enteredBy: String,         // username of the logged-in user
    val updatedAt: Long,
    val note: String? = null,      // extra note for this donation
    val createdAt: Long = 0L       // when it was actually typed in (differs from timestamp for back-dated entries)
)

@Serializable
@Entity(tableName = "expenses", indices = [Index(value = ["voucherNumber"], unique = true), Index("timestamp")])
data class ExpenseEntity(
    @PrimaryKey val uid: String,
    val voucherNumber: String,     // V-2026-A-0001
    val timestamp: Long,
    val category: String,
    val description: String,
    val amountPaid: Double,
    val paidTo: String,
    val paymentMode: String,
    val festivalName: String?,
    val billImagePath: String?,    // photo stays on the phone that captured it
    val enteredBy: String,
    val deleted: Boolean = false,
    val updatedAt: Long,
    val salaryMonth: String? = null // yyyy-MM, only for monthly salary payments
)

@Serializable
data class Approval(
    val userUid: String,
    val name: String,
    val at: Long,
    val deviceCode: String,
    val method: String
)

@Serializable
@Entity(tableName = "deletion_requests", indices = [Index("donationUid")])
data class DeletionRequestEntity(
    @PrimaryKey val uid: String,
    val donationUid: String,
    val receiptNumber: String,
    val donorName: String,
    val amount: Double,
    val donationJson: String,      // full copy of the donation at request time
    val reason: String,
    val requestedBy: String,
    val requestedByUid: String,
    val requestedAt: Long,
    val approvalsJson: String,     // JSON list of Approval
    val status: String,            // Pending / Deleted / Cancelled
    val updatedAt: Long
)

/** Permanent history. The database refuses UPDATE and DELETE on this table (see AppDatabase). */
@Serializable
@Entity(tableName = "deletion_log")
data class DeletionLogEntity(
    @PrimaryKey val uid: String,   // "del-<donationUid>" so the same deletion never appears twice
    val donationUid: String,
    val receiptNumber: String,
    val donorName: String,
    val amount: Double,
    val donationJson: String,
    val reason: String,
    val requestedBy: String,
    val requestedAt: Long,
    val approvalsJson: String,
    val deletedAt: Long,
    val deletedOnDevice: String
)

@Serializable
@Entity(tableName = "festivals")
data class FestivalEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val updatedAt: Long
)

/**
 * Fee history. A change applies from [effectiveFrom] onward and never rewrites earlier periods.
 * Monthly rates start on a month (yyyy-MM); yearly rates always start in January (yyyy-01).
 */
@Serializable
@Entity(tableName = "member_fee_rates", indices = [Index("memberUid")])
data class MemberFeeRateEntity(
    @PrimaryKey val uid: String,
    val memberUid: String,
    val plan: String,
    val amount: Double,
    val effectiveFrom: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** One committee post held by a member from [fromMonth] to [toMonth] (null = still holds it). */
@Serializable
@Entity(tableName = "committee_terms", indices = [Index("memberUid")])
data class CommitteeTermEntity(
    @PrimaryKey val uid: String,
    val memberUid: String,
    val memberName: String,
    val designation: String,
    val fromMonth: String,
    val toMonth: String? = null,
    val updatedAt: Long
)

data class LabelTotal(val label: String?, val total: Double)

/** A fee still owed: [dueAmount] is the rate that applied in that period, [duePlan] its plan. */
data class PendingFee(
    @Embedded val member: MemberEntity,
    val paid: Double,
    val dueAmount: Double,
    val duePlan: String
) {
    val due: Double get() = (dueAmount - paid).coerceAtLeast(0.0)
}
