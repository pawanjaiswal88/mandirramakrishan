package com.mandir.manager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class, MemberEntity::class, DonationEntity::class, ExpenseEntity::class,
        DeletionRequestEntity::class, DeletionLogEntity::class,
        FestivalEntity::class, MemberFeeRateEntity::class, CommitteeTermEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun memberDao(): MemberDao
    abstract fun donationDao(): DonationDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun deletionDao(): DeletionDao
    abstract fun festivalDao(): FestivalDao
    abstract fun feeRateDao(): FeeRateDao
    abstract fun committeeDao(): CommitteeDao

    companion object {
        const val NAME = "mandir.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    // v1 was the prototype. From v2 on every change ships a Migration, so the
                    // deletion history and all records survive app updates.
                    .fallbackToDestructiveMigrationFrom(1)
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = protectHistory(db)
                    })
                    .build()
                    .also { instance = it }
            }

        /**
         * v2 → v3: donation note + entry time, salary month, registration fee, festivals,
         * fee history and committee history. Existing data is converted, nothing is dropped.
         * Generated ids are deterministic so two phones that upgrade separately don't create duplicates.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE donations ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE donations ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE donations SET createdAt = timestamp")
                db.execSQL("ALTER TABLE expenses ADD COLUMN salaryMonth TEXT")
                db.execSQL("ALTER TABLE members ADD COLUMN registrationFee REAL NOT NULL DEFAULT 0")

                db.execSQL("CREATE TABLE IF NOT EXISTS `festivals` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER, `endDate` INTEGER, `notes` TEXT, `active` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `member_fee_rates` (`uid` TEXT NOT NULL, `memberUid` TEXT NOT NULL, `plan` TEXT NOT NULL, `amount` REAL NOT NULL, `effectiveFrom` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_member_fee_rates_memberUid` ON `member_fee_rates` (`memberUid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `committee_terms` (`uid` TEXT NOT NULL, `memberUid` TEXT NOT NULL, `memberName` TEXT NOT NULL, `designation` TEXT NOT NULL, `fromMonth` TEXT NOT NULL, `toMonth` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_committee_terms_memberUid` ON `committee_terms` (`memberUid`)")

                // Current fee of every member becomes the first entry of their fee history.
                db.execSQL(
                    "INSERT OR IGNORE INTO member_fee_rates (uid, memberUid, plan, amount, effectiveFrom, createdAt, updatedAt) " +
                        "SELECT 'init-' || uid, uid, feePlan, feeAmount, " +
                        "CASE WHEN feePlan = 'Yearly' THEN strftime('%Y', createdAt / 1000, 'unixepoch', 'localtime') || '-01' " +
                        "ELSE strftime('%Y-%m', createdAt / 1000, 'unixepoch', 'localtime') END, createdAt, 0 FROM members"
                )
                // Current committee members get an open term starting the month they were added.
                db.execSQL(
                    "INSERT OR IGNORE INTO committee_terms (uid, memberUid, memberName, designation, fromMonth, toMonth, updatedAt) " +
                        "SELECT 'init-' || uid, uid, fullName, COALESCE(designation, 'Member'), " +
                        "strftime('%Y-%m', createdAt / 1000, 'unixepoch', 'localtime'), NULL, 0 " +
                        "FROM members WHERE category = 'Committee Member' AND deleted = 0"
                )
                // Festival names already typed into donations become festival records.
                db.execSQL(
                    "INSERT OR IGNORE INTO festivals (uid, name, startDate, endDate, notes, active, updatedAt) " +
                        "SELECT 'f-' || lower(festivalName), festivalName, NULL, NULL, NULL, 1, 0 FROM " +
                        "(SELECT festivalName FROM donations WHERE festivalName IS NOT NULL AND festivalName != '' " +
                        "UNION SELECT festivalName FROM expenses WHERE festivalName IS NOT NULL AND festivalName != '') " +
                        "GROUP BY lower(festivalName)"
                )
            }
        }

        /** SQLite itself rejects any attempt to edit or delete deletion history. */
        private fun protectHistory(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS deletion_log_no_delete BEFORE DELETE ON deletion_log " +
                    "BEGIN SELECT RAISE(ABORT, 'Deletion history is permanent'); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS deletion_log_no_update BEFORE UPDATE ON deletion_log " +
                    "BEGIN SELECT RAISE(ABORT, 'Deletion history is permanent'); END"
            )
        }
    }
}
