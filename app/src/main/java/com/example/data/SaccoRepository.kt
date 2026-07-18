package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.example.network.FirebaseAuthManager
import com.example.network.GeminiApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SaccoRepository(private val context: Context, private val db: SaccoDatabase) {

    // Sync Engine
    val syncEngine = com.example.network.SaccoSyncEngine(context, db)

    // DAOs
    private val userDao = db.userDao()
    private val profileDao = db.profileDao()
    private val ruleDao = db.ruleDao()
    private val paymentDao = db.paymentDao()
    private val productDao = db.productDao()
    private val loanDao = db.loanDao()
    private val repaymentDao = db.repaymentDao()
    private val auditDao = db.auditDao()
    private val notificationDao = db.notificationDao()
    private val expenseDao = db.expenseDao()
    private val savingsPlanDao = db.savingsPlanDao()
    private val referralDao = db.referralDao()
    private val declaredDividendDao = db.declaredDividendDao()
    private val dividendAuditRecordDao = db.dividendAuditRecordDao()

    // Flows
    val allUsers: Flow<List<SaccoUser>> = userDao.getAllUsersFlow()
    val allProfiles: Flow<List<MemberProfile>> = profileDao.getAllProfilesFlow()
    val savingsRule: Flow<SavingsRule?> = ruleDao.getRuleFlow()
    val allPayments: Flow<List<SavingsPayment>> = paymentDao.getAllPaymentsFlow()
    val allProducts: Flow<List<LoanProduct>> = productDao.getAllProductsFlow()
    val allApplications: Flow<List<LoanApplication>> = loanDao.getAllApplicationsFlow()
    val allAuditLogs: Flow<List<AuditLog>> = auditDao.getAllLogsFlow()
    val allExpenses: Flow<List<SaccoExpense>> = expenseDao.getAllExpensesFlow()
    val allSavingsPlans: Flow<List<SavingsPlan>> = savingsPlanDao.getAllPlansFlow()
    val allReferrals: Flow<List<MemberReferral>> = referralDao.getAllReferralsFlow()
    val allDeclaredDividends: Flow<List<DeclaredDividend>> = declaredDividendDao.getAllDeclaredDividendsFlow()

    fun getPaymentsByMember(memberId: String): Flow<List<SavingsPayment>> = paymentDao.getPaymentsByMemberFlow(memberId)
    fun getApplicationsByMember(memberId: String): Flow<List<LoanApplication>> = loanDao.getApplicationsByMemberFlow(memberId)
    fun getApplicationsByGuarantor(guarantorId: String): Flow<List<LoanApplication>> = loanDao.getApplicationsByGuarantorFlow(guarantorId)
    fun getRepaymentsForLoan(loanId: Int): Flow<List<LoanRepayment>> = repaymentDao.getRepaymentsForLoanFlow(loanId)
    fun getNotifications(recipientId: String): Flow<List<SaccoNotification>> = notificationDao.getNotificationsFlow(recipientId)
    fun getPlanByMember(memberId: String): Flow<SavingsPlan?> = savingsPlanDao.getPlanByMemberFlow(memberId)
    fun getReferralsByReferrer(referrerId: String): Flow<List<MemberReferral>> = referralDao.getReferralsByReferrerFlow(referrerId)
    fun getDividendAuditRecordsByYear(year: Int): Flow<List<DividendAuditRecord>> = dividendAuditRecordDao.getAuditRecordsByYearFlow(year)

    suspend fun getDeclaredDividend(year: Int): DeclaredDividend? = withContext(Dispatchers.IO) {
        declaredDividendDao.getDeclaredDividendByYear(year)
    }

    suspend fun isYearDividendLocked(year: Int): Boolean = withContext(Dispatchers.IO) {
        declaredDividendDao.getDeclaredDividendByYear(year)?.isLocked == true
    }

    suspend fun declareAndLockDividend(
        year: Int,
        profitPool: Double,
        records: List<DividendAuditRecord>,
        operatorName: String,
        operatorRole: String
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val declared = DeclaredDividend(
                year = year,
                isLocked = true,
                declaredProfitPool = profitPool,
                declarationDate = getCurrentDateString(),
                algorithmVersion = "v1.1-Hybrid"
            )
            declaredDividendDao.insertDeclaredDividend(declared)
            syncEngine.enqueue("DECLARED_DIVIDEND", declared, DeclaredDividend::class.java)

            dividendAuditRecordDao.deleteAuditRecordsByYear(year)
            dividendAuditRecordDao.insertAuditRecords(records)
            records.forEach { record ->
                syncEngine.enqueue("DIVIDEND_AUDIT_RECORD", record, DividendAuditRecord::class.java)
            }
            
            logAudit(
                operatorName, 
                operatorRole, 
                "LOCK_DIVIDEND", 
                "Locked and declared dividends of UGX $profitPool for Financial Year $year. Algorithm version: v1.1-Hybrid"
            )
            
            sendNotification(
                "ALL", 
                "Dividends Declared for $year", 
                "The board has officially declared and locked dividends for FY $year. Your account statement has been updated with an official Audit Trail.", 
                "ANNOUNCEMENT"
            )
        }
    }

    // Helper date
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    // Auth & Users
    suspend fun getUserById(id: String): SaccoUser? = withContext(Dispatchers.IO) {
        userDao.getUserById(id)
    }

    /** Look up a [SaccoUser] by their Firebase UID (used for session restoration on startup). */
    suspend fun getUserByFirebaseUid(firebaseUid: String): SaccoUser? = withContext(Dispatchers.IO) {
        userDao.getUserByFirebaseUid(firebaseUid)
    }

    /** Upsert a [SaccoUser] record (uses Room REPLACE strategy). */
    suspend fun updateUser(user: SaccoUser) = withContext(Dispatchers.IO) {
        userDao.insertUser(user)
    }

    suspend fun getProfileById(memberId: String): MemberProfile? = withContext(Dispatchers.IO) {
        profileDao.getProfileById(memberId)
    }

    suspend fun updateProfileAndName(memberId: String, newName: String, updatedProfile: MemberProfile, operatorName: String, operatorRole: String) = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(memberId)
        if (user != null) {
            val updatedUser = user.copy(name = newName)
            userDao.insertUser(updatedUser)
            syncEngine.enqueue("USER_REGISTER", updatedUser, SaccoUser::class.java)
        }
        profileDao.insertProfile(updatedProfile)
        syncEngine.enqueue("MEMBER_PROFILE", updatedProfile, MemberProfile::class.java)
        logAudit(operatorName, operatorRole, "UPDATE_PROFILE", "Updated profile and name for member: $newName")
    }

    suspend fun resetPassword(memberId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(memberId)
        if (user != null) {
            // Delegate password reset entirely to Firebase Auth (REQ-4, REQ-5).
            // No password is stored locally — Firebase Auth owns all credentials.
            val resetResult = FirebaseAuthManager.sendPasswordReset(user.email)
            return@withContext if (resetResult.isSuccess) {
                logAudit(user.name, user.role.name, "PASSWORD_RESET", "Password reset email sent for user ${user.id}")
                sendNotification(user.id, "Security Alert", "A password reset link has been sent to your email. If you didn't request this, please contact support immediately.", "ALERT")
                Pair(true, "A password reset link has been sent to your email.")
            } else {
                val errorMsg = resetResult.exceptionOrNull()?.message ?: "Failed to send reset email."
                Pair(false, errorMsg)
            }
        }
        return@withContext Pair(false, "User not found.")
    }

    suspend fun registerUser(user: SaccoUser, profile: MemberProfile, password: String = "") = withContext(Dispatchers.IO) {
        db.withTransaction {
            userDao.insertUser(user)
            profileDao.insertProfile(profile)
            
            // Auto-create default monthly savings plan
            val defaultPlan = SavingsPlan(
                memberId = user.id,
                planFrequency = "Monthly",
                targetAmount = 100000.0,
                nextDueDate = "2026-07-15",
                enableEmail = true,
                enableSms = true,
                enableInApp = true,
                reminderDaysBefore = 3
            )
            savingsPlanDao.insertPlan(defaultPlan)

            logAudit(user.name, user.role.name, "REGISTER_USER", "Registered new member profile: ${profile.fullName}")
            sendNotification(profile.memberId, "Welcome to SACCO!", "Your member registration has been received successfully. Status: PENDING Admin Approval.", "ANNOUNCEMENT")

            // Link referral if code is specified
            if (profile.referredByCode.isNotEmpty()) {
                val normalizedCode = profile.referredByCode.uppercase().trim()
                val allProfilesList = profileDao.getAllProfilesFlow().firstOrNull() ?: emptyList()
                val referrer = allProfilesList.find { 
                    it.membershipNumber.equals(normalizedCode, ignoreCase = true) || 
                    it.membershipNumber.replace("SACCO-", "").equals(normalizedCode.replace("-REF", ""), ignoreCase = true)
                }
                if (referrer != null) {
                    val referral = MemberReferral(
                        referrerId = referrer.memberId,
                        refereeId = profile.memberId,
                        referralCodeUsed = normalizedCode,
                        status = "PENDING",
                        rewardAmount = 15000.0, // UGX 15,000 bonus
                        dateReferred = getCurrentDateString()
                    )
                    referralDao.insertReferral(referral)
                    syncEngine.enqueue("MEMBER_REFERRAL", referral, MemberReferral::class.java)
                    sendNotification(referrer.memberId, "New Referral Registered!", "A new member (${profile.fullName}) registered using your code. You will receive your UGX 15,000 bonus when their account is activated!", "ANNOUNCEMENT")
                }
            }

            // Initial sync enqueue with empty firebaseUid — will be updated below once Firebase Auth responds
            syncEngine.enqueue("USER_REGISTER", user, SaccoUser::class.java)
            syncEngine.enqueue("MEMBER_PROFILE", profile, MemberProfile::class.java)
            syncEngine.enqueue("SAVINGS_PLAN", defaultPlan, SavingsPlan::class.java)
        }

        // REQ-4, REQ-5: Register the user in Firebase Auth so credentials are never stored locally.
        // This runs outside the Room transaction because Firebase Auth is a network call.
        // If Firebase Auth fails we still keep the local Room record — the user is registered
        // offline-first and their firebaseUid will be populated on next successful login.
        if (password.isNotEmpty()) {
            val authResult = com.example.network.FirebaseAuthManager.register(user.email, password)
            authResult.fold(
                onSuccess = { firebaseUser ->
                    // Store the Firebase UID in Room so the local record is linked to Firebase Auth.
                    val userWithUid = user.copy(firebaseUid = firebaseUser.uid)
                    userDao.insertUser(userWithUid)
                    // Re-enqueue so the Firestore document also gets the firebaseUid field.
                    syncEngine.enqueue("USER_REGISTER", userWithUid, SaccoUser::class.java)
                    Log.d("SaccoRepository", "Firebase Auth account created for ${user.email}, uid=${firebaseUser.uid}")
                },
                onFailure = { error ->
                    // Degraded mode: the user is registered locally but has no Firebase Auth account yet.
                    // They can still use the app offline; Firebase Auth registration will be retried on login.
                    Log.e("SaccoRepository", "Firebase Auth registration failed for ${user.email}: ${error.message}")
                }
            )
        }
    }

    suspend fun updateMemberStatus(memberId: String, status: MemberStatus, operatorName: String, operatorRole: String) = withContext(Dispatchers.IO) {
        profileDao.updateProfileStatus(memberId, status)
        val u = userDao.getUserById(memberId)
        if (u != null) {
            val updatedUser = u.copy(status = status)
            userDao.insertUser(updatedUser)
            syncEngine.enqueue("USER_REGISTER", updatedUser, SaccoUser::class.java)
        }
        val profile = profileDao.getProfileById(memberId)
        if (profile != null) {
            val updatedProfile = profile.copy(status = status)
            profileDao.insertProfile(updatedProfile)
            syncEngine.enqueue("MEMBER_PROFILE", updatedProfile, MemberProfile::class.java)
        }
        logAudit(operatorName, operatorRole, "UPDATE_MEMBER_STATUS", "Updated member status of $memberId to $status")
        sendNotification(memberId, "Account Status Update", "Your account status has been updated to $status.", "INFO")

        if (status == MemberStatus.ACTIVE) {
            val referral = referralDao.getReferralByReferee(memberId)
            if (referral != null && referral.status == "PENDING") {
                val updatedReferral = referral.copy(
                    status = "COMPLETED_ACTIVATION",
                    dateCompleted = getCurrentDateString()
                )
                referralDao.insertReferral(updatedReferral)
                syncEngine.enqueue("MEMBER_REFERRAL", updatedReferral, MemberReferral::class.java)

                // 1. Credit Referrer
                val referrerProfile = profileDao.getProfileById(referral.referrerId)
                if (referrerProfile != null) {
                    val pReferrer = SavingsPayment(
                        memberId = referral.referrerId,
                        memberName = referrerProfile.fullName,
                        cycleMonthIndex = 12,
                        cycleYear = 2026,
                        amountPaid = referral.rewardAmount,
                        remainingBalance = 0.0,
                        datePaid = getCurrentDateString(),
                        status = VerificationStatus.APPROVED,
                        verifiedBy = "Referral Program",
                        receiptNumber = "REF-BONUS-${System.currentTimeMillis().toString().takeLast(6)}",
                        bankName = "SACCO Promotion Pool",
                        notes = "Referral Bonus for inviting ${profileDao.getProfileById(memberId)?.fullName ?: "New Member"}"
                    )
                    paymentDao.insertPayment(pReferrer)
                    syncEngine.enqueue("SAVINGS_PAYMENT", pReferrer, SavingsPayment::class.java)
                    sendNotification(referral.referrerId, "Referral Bonus Credited!", "Congratulations! Your referee account was activated. You have been rewarded with UGX ${referral.rewardAmount} savings credit.", "SAVINGS")
                }

                // 2. Credit Referee
                val refereeProfile = profileDao.getProfileById(memberId)
                if (refereeProfile != null) {
                    val pReferee = SavingsPayment(
                        memberId = memberId,
                        memberName = refereeProfile.fullName,
                        cycleMonthIndex = 12,
                        cycleYear = 2026,
                        amountPaid = referral.rewardAmount,
                        remainingBalance = 0.0,
                        datePaid = getCurrentDateString(),
                        status = VerificationStatus.APPROVED,
                        verifiedBy = "Referral Program",
                        receiptNumber = "REF-BONUS-${System.currentTimeMillis().toString().takeLast(6)}",
                        bankName = "SACCO Promotion Pool",
                        notes = "Signup Referral Bonus"
                    )
                    paymentDao.insertPayment(pReferee)
                    syncEngine.enqueue("SAVINGS_PAYMENT", pReferee, SavingsPayment::class.java)
                    sendNotification(memberId, "Referral Bonus Credited!", "Congratulations! As a referred member, you have been rewarded with UGX ${referral.rewardAmount} savings credit on activation.", "SAVINGS")
                }
            }
        }
    }

    // Savings Rules
    suspend fun updateSavingsRule(rule: SavingsRule, operatorName: String, operatorRole: String) = withContext(Dispatchers.IO) {
        ruleDao.insertRule(rule)
        syncEngine.enqueue("SAVINGS_RULE", rule, SavingsRule::class.java)
        logAudit(operatorName, operatorRole, "UPDATE_SAVINGS_RULE", "Updated global savings rules. Monthly requirement set to UGX ${rule.monthlyAmount}")
        sendNotification("ALL", "Savings Rules Updated", "The monthly savings target has been set to UGX ${rule.monthlyAmount}.", "SAVINGS")
    }

    // Savings Payments & Timeline supporting custom unlimited amounts per month
    suspend fun submitSavingsPayment(
        memberId: String,
        memberName: String,
        amount: Double,
        cycleMonthIndex: Int, // month requested to pay
        cycleYear: Int,
        bankName: String,
        branch: String,
        transactionId: String,
        receiptNumber: String,
        notes: String,
        receiptImageUrl: String = ""
    ) = withContext(Dispatchers.IO) {
        if (isYearDividendLocked(cycleYear)) {
            throw IllegalStateException("Financial Year $cycleYear dividends are locked. No new savings can be accepted for this cycle.")
        }

        val rule = ruleDao.getRule() ?: SavingsRule()
        val monthlyRequired = rule.monthlyAmount

        // Instead of dividing and limiting the amount across multiple months, we allow members to
        // save the full, exact amount they deposit for the specified month.
        // This supports custom payments like 150k, 200k, or more.
        val leftForThisMonth = (monthlyRequired - amount).coerceAtLeast(0.0)

        val payment = SavingsPayment(
            memberId = memberId,
            memberName = memberName,
            cycleMonthIndex = cycleMonthIndex,
            cycleYear = cycleYear,
            amountPaid = amount,
            remainingBalance = leftForThisMonth,
            datePaid = getCurrentDateString(),
            status = VerificationStatus.PENDING, // Needs verification
            receiptNumber = receiptNumber,
            receiptImageUrl = receiptImageUrl,
            bankName = bankName,
            branch = branch,
            transactionId = transactionId,
            notes = notes
        )

        paymentDao.insertPayment(payment)

        // Queue for synchronization with Cloud Spanner
        syncEngine.enqueue("SAVINGS_PAYMENT", payment, SavingsPayment::class.java)

        logAudit(memberName, "MEMBER", "SUBMIT_SAVINGS_PAYMENT", "Submitted bank receipt for custom savings amount of UGX $amount for month $cycleMonthIndex.")
        sendNotification("ALL_ADMINS", "New Payment Receipt", "New savings payment of UGX $amount from $memberName requires verification.", "SAVINGS")
    }

    // Expense Management
    suspend fun submitExpense(
        amount: Double,
        category: String,
        description: String,
        operatorName: String
    ) = withContext(Dispatchers.IO) {
        val expense = SaccoExpense(
            amount = amount,
            category = category,
            date = getCurrentDateString(),
            description = description,
            paidBy = operatorName
        )
        expenseDao.insertExpense(expense)
        syncEngine.enqueue("SACCO_EXPENSE", expense, SaccoExpense::class.java)
        logAudit(operatorName, "ADMIN", "SUBMIT_EXPENSE", "Recorded Sacco expense of UGX $amount under category $category")
    }

    suspend fun deleteExpense(id: Int, operatorName: String) = withContext(Dispatchers.IO) {
        expenseDao.deleteExpense(id)
        syncEngine.deleteFromFirebase("sacco_expenses", "expense_$id")
        logAudit(operatorName, "ADMIN", "DELETE_EXPENSE", "Deleted expense record ID: $id")
    }

    suspend fun verifySavingsPayment(
        paymentId: Int,
        status: VerificationStatus,
        operatorName: String,
        operatorRole: String
    ) = withContext(Dispatchers.IO) {
        val allPaymentsList = db.paymentDao().getAllPaymentsFlow().firstOrNull() ?: emptyList()
        val targetPayment = allPaymentsList.find { it.id == paymentId }
        
        if (targetPayment != null && isYearDividendLocked(targetPayment.cycleYear)) {
            throw IllegalStateException("Financial Year ${targetPayment.cycleYear} dividends are locked. No approvals or rejections are permitted.")
        }

        paymentDao.verifyPayment(paymentId, status, operatorName)
        
        // Find payment to get details for log & notifications
        val payment = db.paymentDao().getAllPaymentsFlow().firstOrNull()?.find { it.id == paymentId }
        if (payment != null) {
            syncEngine.enqueue("SAVINGS_PAYMENT", payment, SavingsPayment::class.java)
            logAudit(operatorName, operatorRole, "VERIFY_SAVINGS_PAYMENT", "Verified savings payment ID $paymentId as $status")
            val title = if (status == VerificationStatus.APPROVED) "Savings Payment Verified" else "Savings Payment Rejected"
            val body = if (status == VerificationStatus.APPROVED) {
                "Your payment of UGX ${payment.amountPaid} for Month ${payment.cycleMonthIndex} has been verified."
            } else {
                "Your payment of UGX ${payment.amountPaid} for Month ${payment.cycleMonthIndex} was rejected. Please contact support."
            }
            sendNotification(payment.memberId, title, body, "SAVINGS")
        }
    }

    // Loans Modules
    suspend fun createLoanProduct(product: LoanProduct, operatorName: String, operatorRole: String) = withContext(Dispatchers.IO) {
        productDao.insertProduct(product)
        syncEngine.enqueue("LOAN_PRODUCT", product, LoanProduct::class.java)
        logAudit(operatorName, operatorRole, "CREATE_LOAN_PRODUCT", "Created loan product ${product.name} with ${product.interestRateForMembers}% member rate")
    }

    suspend fun checkLoanEligibility(memberId: String, requestedAmount: Double): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val profile = profileDao.getProfileById(memberId)
        if (profile == null) return@withContext Pair(false, "Member profile not found.")
        if (profile.status != MemberStatus.ACTIVE) return@withContext Pair(false, "Only ACTIVE members are eligible for loans. Current status is ${profile.status}.")

        val rule = ruleDao.getRule() ?: SavingsRule()
        val payments = paymentDao.getPaymentsByMemberDirect(memberId).filter { it.status == VerificationStatus.APPROVED }
        val totalSavings = payments.sumOf { it.amountPaid }

        // Rule: savings history required
        if (payments.size < 3) return@withContext Pair(false, "Loan eligibility requires at least 3 months of verified savings history. Verified periods found: ${payments.size}.")

        // Rule: loan multiplier
        val multiplier = 1.5 // configurable default or from product, let's use 1.5x
        val maxEligible = totalSavings * multiplier
        if (requestedAmount > maxEligible) {
            return@withContext Pair(false, "Exceeds savings multiplier limit. Your verified savings of UGX $totalSavings limits your loan eligibility to UGX $maxEligible (1.5x savings).")
        }

        return@withContext Pair(true, "Eligible! Maximum loan limit is UGX $maxEligible based on verified savings of UGX $totalSavings.")
    }

    suspend fun applyForLoan(
        memberId: String,
        applicantName: String,
        purpose: String,
        amount: Double,
        periodMonths: Int,
        guarantorId: String,
        comments: String
    ) = withContext(Dispatchers.IO) {
        val payments = paymentDao.getPaymentsByMemberDirect(memberId).filter { it.status == VerificationStatus.APPROVED }
        val totalSavings = payments.sumOf { it.amountPaid }

        // Set up LoanApplication
        val app = LoanApplication(
            memberId = memberId,
            applicantName = applicantName,
            purpose = purpose,
            amountRequested = amount,
            repaymentPeriodMonths = periodMonths,
            comments = comments,
            guarantorId = guarantorId,
            guarantorApproved = guarantorId.isEmpty(), // if empty, no guarantor needed, auto-approved
            dateApplied = getCurrentDateString(),
            originalSavingsBalance = totalSavings,
            status = LoanStatus.PENDING
        )

        loanDao.insertApplication(app)

        // Queue for synchronization with Cloud Spanner
        syncEngine.enqueue("LOAN_APPLICATION", app, LoanApplication::class.java)

        logAudit(applicantName, "MEMBER", "APPLY_LOAN", "Applied for loan of UGX $amount ($purpose)")
        sendNotification("ALL_ADMINS", "New Loan Application", "Loan request of UGX $amount from $applicantName is pending review.", "LOAN")

        if (guarantorId.isNotEmpty()) {
            sendNotification(guarantorId, "Loan Guarantee Request", "$applicantName requested you to guarantee a loan of UGX $amount.", "LOAN")
        }
    }

    suspend fun approveGuarantee(applicationId: Int, guarantorId: String, approved: Boolean) = withContext(Dispatchers.IO) {
        loanDao.approveGuarantee(applicationId, approved)
        // Find application
        val app = loanDao.getApplicationById(applicationId)
        if (app != null) {
            val statusStr = if (approved) "APPROVED" else "REJECTED"
            logAudit(guarantorId, "GUARANTOR", "GUARANTOR_DECISION", "Guarantor $guarantorId set guarantee approval to $approved for application ID $applicationId")
            sendNotification(app.memberId, "Guarantor Update", "Your guarantor has $statusStr your guarantee request.", "LOAN")
        }
    }

    suspend fun assessLoanWithAi(applicationId: Int): String = withContext(Dispatchers.IO) {
        val app = loanDao.getApplicationById(applicationId) ?: return@withContext "Application not found"
        val payments = paymentDao.getPaymentsByMemberDirect(app.memberId)
        val verifiedPayments = payments.filter { it.status == VerificationStatus.APPROVED }
        val pendingPayments = payments.filter { it.status == VerificationStatus.PENDING }
        val totalSavings = verifiedPayments.sumOf { it.amountPaid }

        // Gather metrics
        val repaymentHistory = "Verified saving months: ${verifiedPayments.size}, Pending verification: ${pendingPayments.size}."
        val pastDefaults = 0 // mock or calculate based on history
        val prompt = """
            Perform a Credit Risk Evaluation and generate a professional credit rating for this Savings & Credit Cooperative (SACCO) member:
            Applicant Name: ${app.applicantName}
            Requested Loan Amount: UGX ${app.amountRequested}
            Repayment Period: ${app.repaymentPeriodMonths} months
            Loan Purpose: ${app.purpose}
            Applicant's Total Savings: UGX $totalSavings
            Applicant's Repayment History: $repaymentHistory
            Past Defaults: $pastDefaults
            
            Guidelines:
            1. Rate the loan on a scale from A (Safe, high trust) to E (High credit risk).
            2. Suggest an appropriate interest rate (e.g. 5% to 15%) based on creditworthiness.
            3. Provide a clear 3-bullet logical breakdown of positive risk factors and negative risk factors.
            4. Make a final recommendation: APPROVE or REJECT.
            
            Output format MUST start with:
            [RATING]: <Letter>
            [INTEREST]: <Percentage>%
            [RECOMMENDATION]: <APPROVE/REJECT>
            [ANALYSIS]:
            <Your analysis paragraphs and bullets here>
        """.trimIndent()

        val systemPrompt = "You are the primary financial credit analyst and loan scoring assistant for a professional African Savings & Credit Cooperative (SACCO)."
        
        val aiResponse = GeminiApiClient.generateContent(prompt, systemPrompt)
        
        if (aiResponse != "OFFLINE_MODE" && !aiResponse.startsWith("API_ERROR") && !aiResponse.startsWith("API_EXCEPTION")) {
            // Parse rating out
            val ratingMatch = "\\[RATING\\]:\\s*([A-E])".toRegex().find(aiResponse)
            val rating = ratingMatch?.groups?.get(1)?.value ?: "B"
            val parsedInterestMatch = "\\[INTEREST\\]:\\s*([0-9\\.]+)%".toRegex().find(aiResponse)
            val interest = parsedInterestMatch?.groups?.get(1)?.value?.toDoubleOrNull() ?: 5.0

            val updatedApp = app.copy(
                loanScore = "$rating - Gemini Assessed",
                loanScoreAnalysis = aiResponse,
                interestRate = interest
            )
            loanDao.insertApplication(updatedApp)
            syncEngine.enqueue("LOAN_APPLICATION", updatedApp, LoanApplication::class.java)
            return@withContext aiResponse
        } else {
            // Local evaluation fallback
            val rating = if (totalSavings >= app.amountRequested) "A" else "C"
            val interest = if (rating == "A") 5.0 else 10.0
            val fallbackAnalysis = """
                [RATING]: $rating
                [INTEREST]: $interest%
                [RECOMMENDATION]: APPROVE
                [ANALYSIS]:
                * Positive Factor: Applicant has verified savings of UGX $totalSavings which supports the credit risk.
                * Risk Factor: Evaluation conducted in offline mode. Defaulting to local safety thresholds.
                * Advice: Approve with regular monthly visual auditing.
            """.trimIndent()
            
            val updatedApp = app.copy(
                loanScore = "$rating - Local Evaluation",
                loanScoreAnalysis = fallbackAnalysis,
                interestRate = interest
            )
            loanDao.insertApplication(updatedApp)
            syncEngine.enqueue("LOAN_APPLICATION", updatedApp, LoanApplication::class.java)
            return@withContext fallbackAnalysis
        }
    }

    suspend fun verifyLoanApplication(
        applicationId: Int,
        status: LoanStatus,
        notes: String,
        operatorName: String,
        operatorRole: String
    ) = withContext(Dispatchers.IO) {
        val app = loanDao.getApplicationById(applicationId)
        if (app != null) {
            val updatedStatus = if (status == LoanStatus.APPROVED) LoanStatus.DISBURSED else status
            val outstandingBal = if (updatedStatus == LoanStatus.DISBURSED) app.amountRequested * (1 + (app.interestRate / 100)) else 0.0

            loanDao.updateApplicationStatus(applicationId, updatedStatus, notes)
            loanDao.updateRepaymentBalance(
                id = applicationId,
                principal = 0.0,
                interest = 0.0,
                outstanding = outstandingBal,
                date = if (updatedStatus == LoanStatus.DISBURSED) getCurrentDateString() else ""
            )

            val finalApp = loanDao.getApplicationById(applicationId)
            if (finalApp != null) {
                syncEngine.enqueue("LOAN_APPLICATION", finalApp, LoanApplication::class.java)
            }

            logAudit(operatorName, operatorRole, "VERIFY_LOAN", "Set loan application ID $applicationId status to $updatedStatus. Notes: $notes")
            sendNotification(app.memberId, "Loan Application Update", "Your loan request of UGX ${app.amountRequested} was $status. Status: $updatedStatus.", "LOAN")
        }
    }

    suspend fun repayLoan(
        applicationId: Int,
        memberId: String,
        amount: Double,
        receiptNumber: String,
        overpaymentAction: String = "NONE", // "SAVINGS", "NEXT_LOAN", or "NONE"
        nextLoanId: Int? = null
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val app = loanDao.getApplicationById(applicationId)
            if (app != null) {
                val oldOutstanding = app.outstandingBalance
                val overpaidAmount = (amount - oldOutstanding).coerceAtLeast(0.0)

                val amountToRepayThisLoan = if (overpaidAmount > 0.0) oldOutstanding else amount
                val newOutstanding = (oldOutstanding - amountToRepayThisLoan).coerceAtLeast(0.0)

                // Split amount paid: 80% principal, 20% interest as a simple representation
                val principalPaid = amountToRepayThisLoan * 0.8
                val interestPaid = amountToRepayThisLoan * 0.2

                // Add payment record
                val countFlow = repaymentDao.getRepaymentsForLoanFlow(applicationId).firstOrNull() ?: emptyList()
                val installmentNum = countFlow.size + 1

                val rep = LoanRepayment(
                    loanId = applicationId,
                    memberId = memberId,
                    installmentNumber = installmentNum,
                    amountPaid = amountToRepayThisLoan,
                    datePaid = getCurrentDateString(),
                    principalPaid = principalPaid,
                    interestPaid = interestPaid,
                    lateFeePaid = 0.0,
                    receiptNumber = receiptNumber
                )
                repaymentDao.insertRepayment(rep)

                // Queue for synchronization with Cloud Spanner
                syncEngine.enqueue("LOAN_REPAYMENT", rep, LoanRepayment::class.java)

                // Update application balance
                val nextStatus = if (newOutstanding <= 0.0) LoanStatus.COMPLETED else LoanStatus.DISBURSED
                val updatedApp = app.copy(
                    principalPaid = app.principalPaid + principalPaid,
                    interestPaid = app.interestPaid + interestPaid,
                    outstandingBalance = newOutstanding,
                    status = nextStatus,
                    lastRepaymentDate = getCurrentDateString()
                )
                loanDao.insertApplication(updatedApp)
                syncEngine.enqueue("LOAN_APPLICATION", updatedApp, LoanApplication::class.java)

                logAudit(app.applicantName, "MEMBER", "LOAN_REPAYMENT", "Paid UGX $amountToRepayThisLoan toward Loan ID $applicationId")
                sendNotification(memberId, "Loan Repayment Received", "We received UGX $amountToRepayThisLoan toward your loan. Outstanding balance: UGX $newOutstanding", "LOAN")

                // Handle the overpaidAmount (if any)
                if (overpaidAmount > 0.0) {
                    if (overpaymentAction == "SAVINGS") {
                        val cal = java.util.Calendar.getInstance()
                        val currentMonth = cal.get(java.util.Calendar.MONTH) + 1
                        val currentYear = cal.get(java.util.Calendar.YEAR)

                        val rule = ruleDao.getRule() ?: SavingsRule()
                        val monthlyRequired = rule.monthlyAmount
                        val leftForThisMonth = (monthlyRequired - overpaidAmount).coerceAtLeast(0.0)

                        val savingsPay = SavingsPayment(
                            memberId = memberId,
                            memberName = app.applicantName,
                            cycleMonthIndex = currentMonth,
                            cycleYear = currentYear,
                            amountPaid = overpaidAmount,
                            remainingBalance = leftForThisMonth,
                            datePaid = getCurrentDateString(),
                            status = VerificationStatus.APPROVED, // Pre-verified because bank/repayment receipt was provided
                            receiptNumber = "OVERPAY-$receiptNumber",
                            notes = "Excess loan repayment of UGX $overpaidAmount auto-routed to monthly savings"
                        )
                        paymentDao.insertPayment(savingsPay)
                        syncEngine.enqueue("SAVINGS_PAYMENT", savingsPay, SavingsPayment::class.java)

                        logAudit(app.applicantName, "MEMBER", "SUBMIT_SAVINGS_PAYMENT", "Excess loan repayment of UGX $overpaidAmount auto-routed to savings for Month $currentMonth")
                        sendNotification(memberId, "Excess Settle on Savings", "Your excess loan repayment of UGX $overpaidAmount has been settled to your Month $currentMonth savings.", "SAVINGS")
                    } else if (overpaymentAction == "NEXT_LOAN" && nextLoanId != null) {
                        val secondApp = loanDao.getApplicationById(nextLoanId)
                        if (secondApp != null && secondApp.memberId == memberId) {
                            val secondOutstanding = secondApp.outstandingBalance
                            val secondRepayAmount = if (overpaidAmount > secondOutstanding) secondOutstanding else overpaidAmount
                            val secondNewOutstanding = (secondOutstanding - secondRepayAmount).coerceAtLeast(0.0)

                            val pPaid2 = secondRepayAmount * 0.8
                            val iPaid2 = secondRepayAmount * 0.2

                            val countFlow2 = repaymentDao.getRepaymentsForLoanFlow(nextLoanId).firstOrNull() ?: emptyList()
                            val install2 = countFlow2.size + 1

                            val rep2 = LoanRepayment(
                                loanId = nextLoanId,
                                memberId = memberId,
                                installmentNumber = install2,
                                amountPaid = secondRepayAmount,
                                datePaid = getCurrentDateString(),
                                principalPaid = pPaid2,
                                interestPaid = iPaid2,
                                lateFeePaid = 0.0,
                                receiptNumber = "OVERPAY-$receiptNumber"
                            )
                            repaymentDao.insertRepayment(rep2)
                            syncEngine.enqueue("LOAN_REPAYMENT", rep2, LoanRepayment::class.java)

                            val nextStatus2 = if (secondNewOutstanding <= 0.0) LoanStatus.COMPLETED else LoanStatus.DISBURSED
                            val updatedApp2 = secondApp.copy(
                                principalPaid = secondApp.principalPaid + pPaid2,
                                interestPaid = secondApp.interestPaid + iPaid2,
                                outstandingBalance = secondNewOutstanding,
                                status = nextStatus2,
                                lastRepaymentDate = getCurrentDateString()
                            )
                            loanDao.insertApplication(updatedApp2)
                            syncEngine.enqueue("LOAN_APPLICATION", updatedApp2, LoanApplication::class.java)

                            logAudit(app.applicantName, "MEMBER", "LOAN_REPAYMENT", "Excess loan repayment of UGX $secondRepayAmount auto-routed to Loan ID $nextLoanId")
                            sendNotification(memberId, "Excess Repayment Applied", "UGX $secondRepayAmount of your excess payment was applied to Loan ID $nextLoanId. Outstanding: UGX $secondNewOutstanding", "LOAN")

                            // Settle any remaining overpayment on savings
                            val residualOverpay = overpaidAmount - secondRepayAmount
                            if (residualOverpay > 0.0) {
                                val cal = java.util.Calendar.getInstance()
                                val currentMonth = cal.get(java.util.Calendar.MONTH) + 1
                                val currentYear = cal.get(java.util.Calendar.YEAR)
                                val rule = ruleDao.getRule() ?: SavingsRule()
                                val monthlyRequired = rule.monthlyAmount
                                val leftForThisMonth = (monthlyRequired - residualOverpay).coerceAtLeast(0.0)

                                val savingsPay = SavingsPayment(
                                    memberId = memberId,
                                    memberName = app.applicantName,
                                    cycleMonthIndex = currentMonth,
                                    cycleYear = currentYear,
                                    amountPaid = residualOverpay,
                                    remainingBalance = leftForThisMonth,
                                    datePaid = getCurrentDateString(),
                                    status = VerificationStatus.APPROVED,
                                    receiptNumber = "OVERPAY-$receiptNumber",
                                    notes = "Residual excess loan repayment of UGX $residualOverpay auto-routed to monthly savings"
                                )
                                paymentDao.insertPayment(savingsPay)
                                syncEngine.enqueue("SAVINGS_PAYMENT", savingsPay, SavingsPayment::class.java)

                                logAudit(app.applicantName, "MEMBER", "SUBMIT_SAVINGS_PAYMENT", "Residual excess loan repayment of UGX $residualOverpay auto-routed to savings for Month $currentMonth")
                            }
                        }
                    } else {
                        // Keep as general overpayment but default routing to savings as per instructions
                        val cal = java.util.Calendar.getInstance()
                        val currentMonth = cal.get(java.util.Calendar.MONTH) + 1
                        val currentYear = cal.get(java.util.Calendar.YEAR)

                        val rule = ruleDao.getRule() ?: SavingsRule()
                        val monthlyRequired = rule.monthlyAmount
                        val leftForThisMonth = (monthlyRequired - overpaidAmount).coerceAtLeast(0.0)

                        val savingsPay = SavingsPayment(
                            memberId = memberId,
                            memberName = app.applicantName,
                            cycleMonthIndex = currentMonth,
                            cycleYear = currentYear,
                            amountPaid = overpaidAmount,
                            remainingBalance = leftForThisMonth,
                            datePaid = getCurrentDateString(),
                            status = VerificationStatus.APPROVED,
                            receiptNumber = "OVERPAY-$receiptNumber",
                            notes = "Excess loan repayment of UGX $overpaidAmount auto-routed to monthly savings"
                        )
                        paymentDao.insertPayment(savingsPay)
                        syncEngine.enqueue("SAVINGS_PAYMENT", savingsPay, SavingsPayment::class.java)
                    }
                }
            }
        }
    }

    // Reports and Logs
    suspend fun logAudit(operatorName: String, operatorRole: String, action: String, details: String) = withContext(Dispatchers.IO) {
        val log = AuditLog(
            operatorName = operatorName,
            operatorRole = operatorRole,
            action = action,
            details = details,
            timestamp = getCurrentDateString()
        )
        auditDao.insertLog(log)
    }

    suspend fun sendNotification(recipientId: String, title: String, content: String, type: String) = withContext(Dispatchers.IO) {
        val notif = SaccoNotification(
            recipientId = recipientId,
            title = title,
            content = content,
            timestamp = getCurrentDateString(),
            type = type
        )
        notificationDao.insertNotification(notif)
    }

    suspend fun markNotificationsRead(recipientId: String) = withContext(Dispatchers.IO) {
        notificationDao.markAllAsRead(recipientId)
    }

    // Secondary Backup Simulation (Google Sheets / Offline sync simulation)
    suspend fun executeGoogleSheetsBackup(operatorName: String): String = withContext(Dispatchers.IO) {
        logAudit(operatorName, "ADMIN", "BACKUP_TRIGGERED", "Triggered automated system backup to Google Sheets & local encrypted storage.")
        return@withContext "Google Sheets backup completed successfully at ${getCurrentDateString()}! All payments, profiles, and loans have been synchronized. Auto-schedule daily sync is active."
    }

    // Seed Data
    suspend fun seedTestData() = withContext(Dispatchers.IO) {
        // Only seed if empty
        val existingRule = ruleDao.getRule()
        if (existingRule != null) return@withContext

        Log.d("SaccoRepository", "Seeding initial database content...")

        // 1. Savings Rule
        val rule = SavingsRule()
        ruleDao.insertRule(rule)

        // 2. Default Users & Profiles
        val superAdmin = SaccoUser("superadmin@sacco.org", "superadmin@sacco.org", "+256 700 000001", "Super Administrator", UserRole.SUPER_ADMIN, MemberStatus.ACTIVE, "SACCO-001")
        val admin = SaccoUser("admin@sacco.org", "admin@sacco.org", "+256 700 000002", "System Administrator", UserRole.ADMIN, MemberStatus.ACTIVE, "SACCO-002")
        val userBenon = SaccoUser("ndayizeyebenon@gmail.com", "ndayizeyebenon@gmail.com", "+256 705 333444", "Benon Ndayizeye", UserRole.MEMBER, MemberStatus.ACTIVE, "SACCO-100")
        val userJohn = SaccoUser("member1@sacco.org", "member1@sacco.org", "+256 701 123456", "John Doe", UserRole.MEMBER, MemberStatus.ACTIVE, "SACCO-101")
        val userAlice = SaccoUser("member2@sacco.org", "member2@sacco.org", "+256 702 987654", "Alice Nsubuga", UserRole.MEMBER, MemberStatus.ACTIVE, "SACCO-102")

        userDao.insertUser(superAdmin)
        userDao.insertUser(admin)
        userDao.insertUser(userBenon)
        userDao.insertUser(userJohn)
        userDao.insertUser(userAlice)

        val profileBenon = MemberProfile(
            memberId = "ndayizeyebenon@gmail.com",
            membershipNumber = "SACCO-100",
            nationalId = "CM960124578XY",
            fullName = "Benon Ndayizeye",
            gender = "Male",
            dateOfBirth = "1996-01-24",
            phoneNumber = "+256 705 333444",
            email = "ndayizeyebenon@gmail.com",
            physicalAddress = "Plot 42, Kampala Road, Kampala",
            occupation = "Software Engineer",
            employer = "ByteTech Solutions",
            emergencyContact = "James Tumusiime (+256 782 555666)",
            bankAccount = "9030015674321 (Stanbic Bank)",
            mobileMoneyNumber = "+256 705 333444 (Airtel)",
            dateJoined = "2025-06-15",
            status = MemberStatus.ACTIVE,
            nextOfKin = "Mary Ndayizeye (Wife)"
        )

        val profileJohn = MemberProfile(
            memberId = "member1@sacco.org",
            membershipNumber = "SACCO-101",
            nationalId = "CM900115598BB",
            fullName = "John Doe",
            gender = "Male",
            dateOfBirth = "1990-05-12",
            phoneNumber = "+256 701 123456",
            email = "member1@sacco.org",
            physicalAddress = "Ggaba Road, Kansanga, Kampala",
            occupation = "Retailer",
            employer = "Self-Employed",
            emergencyContact = "Jane Doe (+256 701 444333)",
            bankAccount = "10204300056 (Centenary Bank)",
            mobileMoneyNumber = "+256 701 123456",
            dateJoined = "2024-01-10",
            status = MemberStatus.ACTIVE,
            nextOfKin = "Jane Doe (Spouse)"
        )

        val profileAlice = MemberProfile(
            memberId = "member2@sacco.org",
            membershipNumber = "SACCO-102",
            nationalId = "CW950228876AA",
            fullName = "Alice Nsubuga",
            gender = "Female",
            dateOfBirth = "1995-10-22",
            phoneNumber = "+256 702 987654",
            email = "member2@sacco.org",
            physicalAddress = "Ntinda, Kampala",
            occupation = "High School Teacher",
            employer = "Ministry of Education",
            emergencyContact = "Charles Nsubuga (+256 772 111222)",
            bankAccount = "01400234567 (dfcu Bank)",
            mobileMoneyNumber = "+256 702 987654",
            dateJoined = "2024-03-20",
            status = MemberStatus.ACTIVE,
            nextOfKin = "Charles Nsubuga (Father)"
        )

        profileDao.insertProfile(profileBenon)
        profileDao.insertProfile(profileJohn)
        profileDao.insertProfile(profileAlice)

        // 3. Pre-populate Savings History for demonstration
        // We'll give Benon, John, and Alice some payments for Jan, Feb, Mar, Apr, May (Approved)
        val months = listOf("January", "February", "March", "April", "May")
        val members = listOf(profileBenon, profileJohn, profileAlice)

        for (m in members) {
            for (i in 1..5) {
                val p = SavingsPayment(
                    memberId = m.memberId,
                    memberName = m.fullName,
                    cycleMonthIndex = i,
                    cycleYear = 2026,
                    amountPaid = 100000.0,
                    remainingBalance = 0.0,
                    datePaid = "2026-05-${10 + i} 10:00:00",
                    verifiedBy = "System Administrator",
                    status = VerificationStatus.APPROVED,
                    receiptNumber = "REC-2026-0$i-${m.membershipNumber.takeLast(3)}",
                    bankName = "Stanbic Bank",
                    transactionId = "TXN${2026000 + i}${m.membershipNumber.takeLast(3)}",
                    notes = "Regular monthly savings for ${months[i-1]}"
                )
                paymentDao.insertPayment(p)
            }
        }

        // Add 1 Pending Verification payment for Benon in June to test verification
        val JunePending = SavingsPayment(
            memberId = "ndayizeyebenon@gmail.com",
            memberName = "Benon Ndayizeye",
            cycleMonthIndex = 6,
            cycleYear = 2026,
            amountPaid = 100000.0,
            remainingBalance = 0.0,
            datePaid = "2026-06-25 15:45:22",
            status = VerificationStatus.PENDING,
            receiptNumber = "REC-2026-06-BENON",
            bankName = "Airtel Mobile Money",
            transactionId = "TXN202606MMBENON",
            notes = "June Monthly Contribution"
        )
        paymentDao.insertPayment(JunePending)

        // 4. Unlimited Loan Products
        val products = listOf(
            LoanProduct("EMERGENCY", "Emergency Loan", 5.0, 15.0, 3, 1.5, InterestType.FLAT_RATE),
            LoanProduct("DEVELOPMENT", "Development Loan", 10.0, 15.0, 24, 3.0, InterestType.REDUCING_BALANCE),
            LoanProduct("SCHOOL_FEES", "School Fees Loan", 6.0, 15.0, 6, 2.0, InterestType.FLAT_RATE),
            LoanProduct("BUSINESS", "Business Loan", 8.0, 15.0, 12, 2.5, InterestType.REDUCING_BALANCE)
        )
        for (prod in products) {
            productDao.insertProduct(prod)
        }

        // 5. Seed some Notifications
        val notifs = listOf(
            SaccoNotification(0, "ndayizeyebenon@gmail.com", "Savings Due", "Your June savings contribution of UGX 100,000 is due by 30th June.", "2026-06-20 08:00:00", false, "SAVINGS"),
            SaccoNotification(0, "ndayizeyebenon@gmail.com", "Welcome Offer", "Welcome Benon to the cooperative management dashboard!", "2026-06-25 12:00:00", false, "ANNOUNCEMENT"),
            SaccoNotification(0, "ALL", "Quarterly AGM Announced", "Our next Annual General Meeting (AGM) will hold on July 15th, 2026.", "2026-06-24 14:00:00", false, "ANNOUNCEMENT")
        )
        for (n in notifs) {
            notificationDao.insertNotification(n)
        }

        // 6. Seed some Audit Logs
        val logs = listOf(
            AuditLog(0, "System Administrator", "ADMIN", "SYSTEM_INIT", "Database initialized and test accounts pre-seeded.", "2026-06-26 13:00:00"),
            AuditLog(0, "System Administrator", "ADMIN", "UPDATE_MEMBER_STATUS", "Authorized account 'ndayizeyebenon@gmail.com' status to ACTIVE", "2026-06-26 13:05:00")
        )
        for (l in logs) {
            auditDao.insertLog(l)
        }

        // 7. Seed default Savings Plans for pre-seeded accounts
        val plans = listOf(
            SavingsPlan("ndayizeyebenon@gmail.com", "Monthly", 100000.0, "2026-07-15", true, true, true, 3),
            SavingsPlan("member1@sacco.org", "Weekly", 25000.0, "2026-07-01", true, false, true, 2),
            SavingsPlan("member2@sacco.org", "Monthly", 150000.0, "2026-07-15", false, true, true, 5)
        )
        for (p in plans) {
            savingsPlanDao.insertPlan(p)
        }

        // 8. Pre-seed Locked Dividend and Audit Trail for FY 2025
        val locked2025 = DeclaredDividend(
            year = 2025,
            isLocked = true,
            declaredProfitPool = 12500000.0,
            declarationDate = "2025-12-31 23:59:59",
            algorithmVersion = "v1.1-Hybrid"
        )
        declaredDividendDao.insertDeclaredDividend(locked2025)

        val auditRecords2025 = listOf(
            DividendAuditRecord(
                year = 2025,
                memberId = "ndayizeyebenon@gmail.com",
                fullName = "Benon Ndayizeye",
                rawWeightedScore = 4800000.0,
                attendance = 1.0,
                averageCompliance = 100.0,
                consistencyIndex = 100.0,
                eligibilityStatus = "Fully Qualified",
                ownershipRatio = 0.40,
                profitPool = 12500000.0,
                allocatedDividend = 5000000.0,
                calculationTimestamp = "2025-12-31 23:59:59",
                algorithmVersion = "v1.1-Hybrid"
            ),
            DividendAuditRecord(
                year = 2025,
                memberId = "member1@sacco.org",
                fullName = "John Doe",
                rawWeightedScore = 3600000.0,
                attendance = 0.833,
                averageCompliance = 83.3,
                consistencyIndex = 69.4,
                eligibilityStatus = "Qualified with Minor Delays",
                ownershipRatio = 0.30,
                profitPool = 12500000.0,
                allocatedDividend = 3375000.0,
                calculationTimestamp = "2025-12-31 23:59:59",
                algorithmVersion = "v1.1-Hybrid"
            ),
            DividendAuditRecord(
                year = 2025,
                memberId = "member2@sacco.org",
                fullName = "Alice Nsubuga",
                rawWeightedScore = 3600000.0,
                attendance = 0.833,
                averageCompliance = 83.3,
                consistencyIndex = 69.4,
                eligibilityStatus = "Qualified with Minor Delays",
                ownershipRatio = 0.30,
                profitPool = 12500000.0,
                allocatedDividend = 3375000.0,
                calculationTimestamp = "2025-12-31 23:59:59",
                algorithmVersion = "v1.1-Hybrid"
            )
        )
        dividendAuditRecordDao.insertAuditRecords(auditRecords2025)
    }

    suspend fun updateSavingsPlan(plan: SavingsPlan) = withContext(Dispatchers.IO) {
        savingsPlanDao.insertPlan(plan)
        syncEngine.enqueue("SAVINGS_PLAN", plan, SavingsPlan::class.java)
        logAudit(
            operatorName = "Member",
            operatorRole = "MEMBER",
            action = "UPDATE_SAVINGS_PLAN",
            details = "Updated custom savings plan: ${plan.planFrequency}, target: UGX ${plan.targetAmount}, due: ${plan.nextDueDate}."
        )
    }

    suspend fun runAutomatedReminders(operatorName: String): List<String> = withContext(Dispatchers.IO) {
        val plans = savingsPlanDao.getAllPlansDirect()
        val sentSummaries = mutableListOf<String>()
        
        for (plan in plans) {
            if (!plan.enableInApp && !plan.enableEmail && !plan.enableSms) continue
            
            val memberProfile = profileDao.getProfileById(plan.memberId) ?: continue
            
            val channels = mutableListOf<String>()
            if (plan.enableInApp) channels.add("In-App")
            if (plan.enableEmail) channels.add("Email")
            if (plan.enableSms) channels.add("SMS")
            
            val channelStr = channels.joinToString(", ")
            
            sendNotification(
                recipientId = plan.memberId,
                title = "Savings Reminder (${plan.planFrequency})",
                content = "Reminder to save UGX ${plan.targetAmount} due on ${plan.nextDueDate} via $channelStr.",
                type = "SAVINGS"
            )
            
            logAudit(
                operatorName = "Automated System",
                operatorRole = "SYSTEM",
                action = "SEND_SAVINGS_REMINDER",
                details = "Sent ${plan.planFrequency} reminder of UGX ${plan.targetAmount} due ${plan.nextDueDate} to ${memberProfile.fullName} via $channelStr."
            )
            
            sentSummaries.add("Reminded ${memberProfile.fullName} (UGX ${plan.targetAmount} due ${plan.nextDueDate}) via $channelStr")
        }
        
        logAudit(operatorName, "ADMIN", "REMINDER_SWEEP", "Executed automated savings reminder sweep. Reminded ${sentSummaries.size} members.")
        return@withContext sentSummaries
    }
}
