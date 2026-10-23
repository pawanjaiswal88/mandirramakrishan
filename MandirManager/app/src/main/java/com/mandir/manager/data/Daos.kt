package com.mandir.manager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Insert suspend fun insert(member: MemberEntity): Long
    @Update suspend fun update(member: MemberEntity)
    @Delete suspend fun delete(member: MemberEntity)

    @Query("SELECT * FROM members ORDER BY fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY fullName COLLATE NOCASE")
    suspend fun getAll(): List<MemberEntity>

    @Query("SELECT * FROM members WHERE memberId = :id")
    suspend fun getById(id: Long): MemberEntity?

    @Query("SELECT COALESCE(MAX(memberId), 0) FROM members")
    suspend fun maxId(): Long

    @Query("SELECT COUNT(*) FROM members WHERE mobileNumber = :mobile AND memberId != :excludeId")
    suspend fun countWithMobile(mobile: String, excludeId: Long): Int

    /** Defaulter list: members with a monthly fee whose payments for [month] are below that fee. */
    @Query(
        """
        SELECT * FROM (
            SELECT m.*, (
                SELECT COALESCE(SUM(d.amount), 0) FROM donations d
                WHERE d.donorId = m.memberId AND d.donationType = :feeType AND d.feeMonth = :month
            ) AS paid
            FROM members m
            WHERE m.monthlyFee > 0
        )
        WHERE paid < monthlyFee
        ORDER BY houseNo COLLATE NOCASE, fullName COLLATE NOCASE
        """
    )
    suspend fun pendingFees(month: String, feeType: String): List<PendingFee>
}

@Dao
interface DonationDao {
    @Insert suspend fun insert(donation: DonationEntity): Long
    @Delete suspend fun delete(donation: DonationEntity)

    @Query("SELECT * FROM donations ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DonationEntity>>

    @Query("SELECT * FROM donations ORDER BY timestamp DESC")
    suspend fun getAll(): List<DonationEntity>

    @Query("SELECT COALESCE(MAX(donationId), 0) FROM donations")
    suspend fun maxId(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM donations WHERE timestamp BETWEEN :fromMs AND :toMs")
    suspend fun totalBetween(fromMs: Long, toMs: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM donations WHERE paymentMode = :mode AND timestamp BETWEEN :fromMs AND :toMs")
    suspend fun totalByModeBetween(mode: String, fromMs: Long, toMs: Long): Double

    /** Daily / monthly Cash vs UPI vs Bank summary. */
    @Query("SELECT paymentMode AS label, SUM(amount) AS total FROM donations WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY paymentMode ORDER BY total DESC")
    suspend fun modeTotals(fromMs: Long, toMs: Long): List<LabelTotal>

    @Query("SELECT donationType AS label, SUM(amount) AS total FROM donations WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY donationType ORDER BY total DESC")
    suspend fun typeTotals(fromMs: Long, toMs: Long): List<LabelTotal>

    /** Festival-wise collection report (all festivals). */
    @Query("SELECT festivalName AS label, SUM(amount) AS total FROM donations WHERE festivalName IS NOT NULL AND festivalName != '' GROUP BY festivalName COLLATE NOCASE ORDER BY total DESC")
    suspend fun festivalTotals(): List<LabelTotal>

    @Query("SELECT DISTINCT festivalName FROM donations WHERE festivalName IS NOT NULL AND festivalName != '' ORDER BY festivalName COLLATE NOCASE")
    suspend fun festivalNames(): List<String>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM donations WHERE donorId = :memberId AND feeMonth = :month AND donationType = :feeType")
    suspend fun feePaidFor(memberId: Long, month: String, feeType: String): Double
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(expense: ExpenseEntity): Long
    @Delete suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT COALESCE(MAX(expenseId), 0) FROM expenses")
    suspend fun maxId(): Long

    @Query("SELECT COALESCE(SUM(amountPaid), 0) FROM expenses WHERE timestamp BETWEEN :fromMs AND :toMs")
    suspend fun totalBetween(fromMs: Long, toMs: Long): Double

    @Query("SELECT COALESCE(SUM(amountPaid), 0) FROM expenses WHERE paymentMode = :mode AND timestamp BETWEEN :fromMs AND :toMs")
    suspend fun totalByModeBetween(mode: String, fromMs: Long, toMs: Long): Double

    @Query("SELECT category AS label, SUM(amountPaid) AS total FROM expenses WHERE timestamp BETWEEN :fromMs AND :toMs GROUP BY category ORDER BY total DESC")
    suspend fun categoryTotals(fromMs: Long, toMs: Long): List<LabelTotal>

    @Query("SELECT festivalName AS label, SUM(amountPaid) AS total FROM expenses WHERE festivalName IS NOT NULL AND festivalName != '' GROUP BY festivalName COLLATE NOCASE")
    suspend fun festivalTotals(): List<LabelTotal>

    @Query("SELECT DISTINCT festivalName FROM expenses WHERE festivalName IS NOT NULL AND festivalName != ''")
    suspend fun festivalNames(): List<String>
}
