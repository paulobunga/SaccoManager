package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

class SaccoConverters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromMemberStatus(value: MemberStatus): String = value.name

    @TypeConverter
    fun toMemberStatus(value: String): MemberStatus = MemberStatus.valueOf(value)

    @TypeConverter
    fun fromVerificationStatus(value: VerificationStatus): String = value.name

    @TypeConverter
    fun toVerificationStatus(value: String): VerificationStatus = VerificationStatus.valueOf(value)

    @TypeConverter
    fun fromLoanStatus(value: LoanStatus): String = value.name

    @TypeConverter
    fun toLoanStatus(value: String): LoanStatus = LoanStatus.valueOf(value)

    @TypeConverter
    fun fromInterestType(value: InterestType): String = value.name

    @TypeConverter
    fun toInterestType(value: String): InterestType = InterestType.valueOf(value)
}

@Dao
interface SaccoUserDao {
    @Query("SELECT * FROM sacco_users WHERE id = :id")
    suspend fun getUserById(id: String): SaccoUser?

    @Query("SELECT * FROM sacco_users")
    fun getAllUsersFlow(): Flow<List<SaccoUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: SaccoUser)

    @Query("DELETE FROM sacco_users WHERE id = :id")
    suspend fun deleteUser(id: String)
}

@Dao
interface MemberProfileDao {
    @Query("SELECT * FROM member_profiles WHERE memberId = :memberId")
    suspend fun getProfileById(memberId: String): MemberProfile?

    @Query("SELECT * FROM member_profiles")
    fun getAllProfilesFlow(): Flow<List<MemberProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: MemberProfile)

    @Query("UPDATE member_profiles SET status = :status WHERE memberId = :memberId")
    suspend fun updateProfileStatus(memberId: String, status: MemberStatus)
}

@Dao
interface SavingsRuleDao {
    @Query("SELECT * FROM savings_rules WHERE id = 1")
    suspend fun getRule(): SavingsRule?

    @Query("SELECT * FROM savings_rules WHERE id = 1")
    fun getRuleFlow(): Flow<SavingsRule?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SavingsRule)
}

@Dao
interface SavingsPaymentDao {
    @Query("SELECT * FROM savings_payments ORDER BY datePaid DESC")
    fun getAllPaymentsFlow(): Flow<List<SavingsPayment>>

    @Query("SELECT * FROM savings_payments WHERE memberId = :memberId ORDER BY cycleMonthIndex ASC")
    fun getPaymentsByMemberFlow(memberId: String): Flow<List<SavingsPayment>>

    @Query("SELECT * FROM savings_payments WHERE memberId = :memberId")
    suspend fun getPaymentsByMemberDirect(memberId: String): List<SavingsPayment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: SavingsPayment)

    @Query("UPDATE savings_payments SET status = :status, verifiedBy = :verifiedBy WHERE id = :id")
    suspend fun verifyPayment(id: Int, status: VerificationStatus, verifiedBy: String)
}

@Dao
interface LoanProductDao {
    @Query("SELECT * FROM loan_products")
    fun getAllProductsFlow(): Flow<List<LoanProduct>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: LoanProduct)
}

@Dao
interface LoanApplicationDao {
    @Query("SELECT * FROM loan_applications ORDER BY dateApplied DESC")
    fun getAllApplicationsFlow(): Flow<List<LoanApplication>>

    @Query("SELECT * FROM loan_applications WHERE memberId = :memberId ORDER BY dateApplied DESC")
    fun getApplicationsByMemberFlow(memberId: String): Flow<List<LoanApplication>>

    @Query("SELECT * FROM loan_applications WHERE guarantorId = :guarantorId ORDER BY dateApplied DESC")
    fun getApplicationsByGuarantorFlow(guarantorId: String): Flow<List<LoanApplication>>

    @Query("SELECT * FROM loan_applications WHERE id = :id")
    suspend fun getApplicationById(id: Int): LoanApplication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: LoanApplication)

    @Query("UPDATE loan_applications SET status = :status, approvalNotes = :notes WHERE id = :id")
    suspend fun updateApplicationStatus(id: Int, status: LoanStatus, notes: String)

    @Query("UPDATE loan_applications SET guarantorApproved = :approved WHERE id = :id")
    suspend fun approveGuarantee(id: Int, approved: Boolean)

    @Query("UPDATE loan_applications SET principalPaid = :principal, interestPaid = :interest, outstandingBalance = :outstanding, lastRepaymentDate = :date WHERE id = :id")
    suspend fun updateRepaymentBalance(id: Int, principal: Double, interest: Double, outstanding: Double, date: String)
}

@Dao
interface LoanRepaymentDao {
    @Query("SELECT * FROM loan_repayments WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    fun getRepaymentsForLoanFlow(loanId: Int): Flow<List<LoanRepayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: LoanRepayment)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog)
}

@Dao
interface SaccoNotificationDao {
    @Query("SELECT * FROM sacco_notifications WHERE recipientId = :recipientId OR recipientId = 'ALL' ORDER BY timestamp DESC")
    fun getNotificationsFlow(recipientId: String): Flow<List<SaccoNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: SaccoNotification)

    @Query("UPDATE sacco_notifications SET isRead = 1 WHERE recipientId = :recipientId")
    suspend fun markAllAsRead(recipientId: String)
}

@Dao
interface SaccoExpenseDao {
    @Query("SELECT * FROM sacco_expenses ORDER BY date DESC")
    fun getAllExpensesFlow(): Flow<List<SaccoExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: SaccoExpense)

    @Query("DELETE FROM sacco_expenses WHERE id = :id")
    suspend fun deleteExpense(id: Int)
}

@Dao
interface SavingsPlanDao {
    @Query("SELECT * FROM savings_plans WHERE memberId = :memberId")
    suspend fun getPlanByMember(memberId: String): SavingsPlan?

    @Query("SELECT * FROM savings_plans WHERE memberId = :memberId")
    fun getPlanByMemberFlow(memberId: String): Flow<SavingsPlan?>

    @Query("SELECT * FROM savings_plans")
    fun getAllPlansFlow(): Flow<List<SavingsPlan>>

    @Query("SELECT * FROM savings_plans")
    suspend fun getAllPlansDirect(): List<SavingsPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: SavingsPlan)
}

@Dao
interface MemberReferralDao {
    @Query("SELECT * FROM member_referrals WHERE referrerId = :referrerId")
    fun getReferralsByReferrerFlow(referrerId: String): Flow<List<MemberReferral>>

    @Query("SELECT * FROM member_referrals")
    fun getAllReferralsFlow(): Flow<List<MemberReferral>>

    @Query("SELECT * FROM member_referrals WHERE refereeId = :refereeId")
    suspend fun getReferralByReferee(refereeId: String): MemberReferral?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferral(referral: MemberReferral)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingEntries(): List<SyncQueueEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: SyncQueueEntry)

    @Query("UPDATE sync_queue SET status = :status, retries = retries + 1, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, error: String)

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' OR retries > 5")
    suspend fun pruneSyncQueue()
}

@Dao
interface DeclaredDividendDao {
    @Query("SELECT * FROM declared_dividends WHERE year = :year")
    suspend fun getDeclaredDividendByYear(year: Int): DeclaredDividend?

    @Query("SELECT * FROM declared_dividends")
    fun getAllDeclaredDividendsFlow(): Flow<List<DeclaredDividend>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeclaredDividend(declaredDividend: DeclaredDividend)
}

@Dao
interface DividendAuditRecordDao {
    @Query("SELECT * FROM dividend_audit_records WHERE year = :year ORDER BY fullName ASC")
    fun getAuditRecordsByYearFlow(year: Int): Flow<List<DividendAuditRecord>>

    @Query("SELECT * FROM dividend_audit_records WHERE year = :year")
    suspend fun getAuditRecordsByYearDirect(year: Int): List<DividendAuditRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditRecords(records: List<DividendAuditRecord>)

    @Query("DELETE FROM dividend_audit_records WHERE year = :year")
    suspend fun deleteAuditRecordsByYear(year: Int)
}

@Database(
    entities = [
        SaccoUser::class,
        MemberProfile::class,
        SavingsRule::class,
        SavingsPayment::class,
        LoanProduct::class,
        LoanApplication::class,
        LoanRepayment::class,
        AuditLog::class,
        SaccoNotification::class,
        SaccoExpense::class,
        SavingsPlan::class,
        MemberReferral::class,
        SyncQueueEntry::class,
        DeclaredDividend::class,
        DividendAuditRecord::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(SaccoConverters::class)
abstract class SaccoDatabase : RoomDatabase() {
    abstract fun userDao(): SaccoUserDao
    abstract fun profileDao(): MemberProfileDao
    abstract fun ruleDao(): SavingsRuleDao
    abstract fun paymentDao(): SavingsPaymentDao
    abstract fun productDao(): LoanProductDao
    abstract fun loanDao(): LoanApplicationDao
    abstract fun repaymentDao(): LoanRepaymentDao
    abstract fun auditDao(): AuditLogDao
    abstract fun notificationDao(): SaccoNotificationDao
    abstract fun expenseDao(): SaccoExpenseDao
    abstract fun savingsPlanDao(): SavingsPlanDao
    abstract fun referralDao(): MemberReferralDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun declaredDividendDao(): DeclaredDividendDao
    abstract fun dividendAuditRecordDao(): DividendAuditRecordDao
}
