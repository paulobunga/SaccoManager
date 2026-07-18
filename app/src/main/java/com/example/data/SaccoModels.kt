package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.io.Serializable

enum class UserRole {
    SUPER_ADMIN,
    ADMIN,
    MEMBER,
    GUARANTOR
}

enum class MemberStatus {
    PENDING,
    ACTIVE,
    FROZEN,
    SUSPENDED
}

enum class VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class LoanStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    DISBURSED,
    COMPLETED,
    DEFAULTED
}

enum class InterestType {
    FLAT_RATE,
    REDUCING_BALANCE,
    SIMPLE_INTEREST,
    COMPOUND_INTEREST
}

@Entity(tableName = "sacco_users")
data class SaccoUser(
    @PrimaryKey val id: String, // email or phone
    val email: String,
    val phone: String,
    val name: String,
    val role: UserRole,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val membershipNumber: String = "",
    // Firebase Auth owns all credentials — password is no longer stored locally.
    // REQ-5: firebaseUid links this local record to the Firebase Auth account.
    val firebaseUid: String = ""
) : java.io.Serializable

@Entity(tableName = "member_profiles")
data class MemberProfile(
    @PrimaryKey val memberId: String, // same as SaccoUser id
    val membershipNumber: String,
    val nationalId: String,
    val fullName: String,
    val gender: String,
    val dateOfBirth: String,
    val phoneNumber: String,
    val email: String,
    val physicalAddress: String,
    val occupation: String,
    val employer: String,
    val emergencyContact: String,
    val bankAccount: String,
    val mobileMoneyNumber: String,
    val dateJoined: String,
    val status: MemberStatus = MemberStatus.PENDING,
    val nextOfKin: String,
    val profilePhotoUrl: String = "",
    val signatureUrl: String = "",
    val maxGuaranteeExposure: Double = 5000000.0, // UGX
    val referredByCode: String = "" // Added to support referral program
) : java.io.Serializable

@Entity(tableName = "savings_rules")
data class SavingsRule(
    @PrimaryKey val id: Int = 1, // singleton config
    val monthlyAmount: Double = 100000.0, // Default UGX 100,000
    val gracePeriodDays: Int = 5,
    val penaltyFixedAmount: Double = 10000.0,
    val penaltyPercentage: Double = 5.0, // 5% late fee
    val isPenaltyPercentage: Boolean = false, // false = fixed, true = percentage
    val maxAdvancePaymentMonths: Int = 12,
    val minSavingAmount: Double = 10000.0,
    val maxSavingAmount: Double = 5000000.0,
    val savingsInterestRate: Double = 8.0 // Default 8% annual savings interest rate
) : java.io.Serializable

@Entity(tableName = "savings_payments", indices = [Index(value = ["memberId"])])
data class SavingsPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: String,
    val memberName: String,
    val cycleMonthIndex: Int, // 1 for Jan, 2 for Feb, ..., 12 for Dec
    val cycleYear: Int = 2026,
    val amountPaid: Double,
    val remainingBalance: Double,
    val datePaid: String,
    val verifiedBy: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    val receiptNumber: String,
    val receiptImageUrl: String = "",
    val branch: String = "",
    val bankName: String = "",
    val transactionId: String = "",
    val notes: String = "",
    val penaltyAmountCharged: Double = 0.0
) : java.io.Serializable

@Entity(tableName = "loan_products")
data class LoanProduct(
    @PrimaryKey val id: String, // unique code (e.g., EMERGENCY, DEVELOPMENT)
    val name: String,
    val interestRateForMembers: Double, // e.g., 5.0 (percent)
    val interestRateForNonMembers: Double, // e.g., 15.0
    val repaymentPeriodMonths: Int,
    val maxLoanMultiplier: Double, // e.g., 1.5, 2.0, 3.0
    val interestType: InterestType = InterestType.FLAT_RATE
) : java.io.Serializable

@Entity(tableName = "loan_applications", indices = [Index(value = ["memberId"]), Index(value = ["guarantorId"])])
data class LoanApplication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val memberId: String,
    val applicantName: String,
    val purpose: String,
    val amountRequested: Double,
    val repaymentPeriodMonths: Int,
    val repaymentFrequency: String = "Monthly",
    val comments: String = "",
    val guarantorId: String = "", // another member
    val guarantorApproved: Boolean = false,
    val status: LoanStatus = LoanStatus.PENDING,
    val interestRate: Double = 5.0,
    val approvalNotes: String = "",
    val dateApplied: String,
    val originalSavingsBalance: Double = 0.0,
    val loanScore: String = "N/A", // AI Score (e.g., "A - Safe")
    val loanScoreAnalysis: String = "", // Detailed AI output
    val principalPaid: Double = 0.0,
    val interestPaid: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    val lastRepaymentDate: String = ""
) : java.io.Serializable

@Entity(tableName = "loan_repayments", indices = [Index(value = ["loanId"]), Index(value = ["memberId"])])
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loanId: Int,
    val memberId: String,
    val installmentNumber: Int,
    val amountPaid: Double,
    val datePaid: String,
    val principalPaid: Double,
    val interestPaid: Double,
    val lateFeePaid: Double,
    val receiptNumber: String,
    val status: VerificationStatus = VerificationStatus.APPROVED
) : java.io.Serializable

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operatorName: String,
    val operatorRole: String,
    val action: String,
    val details: String,
    val timestamp: String,
    val ipAddress: String = "" // REQ-15: replaced hardcoded placeholder IP with empty string
) : java.io.Serializable

@Entity(tableName = "sacco_notifications", indices = [Index(value = ["recipientId"])])
data class SaccoNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recipientId: String, // "ALL" or specific memberId
    val title: String,
    val content: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val type: String = "INFO" // SAVINGS, LOAN, PENALTY, ANNOUNCEMENT
) : java.io.Serializable

@Entity(tableName = "sacco_expenses")
data class SaccoExpense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String, // "Rent", "Salaries", "Utilities", "Office Supplies", "Marketing", "Software", "Other"
    val date: String,
    val description: String,
    val paidBy: String
) : java.io.Serializable

@Entity(tableName = "savings_plans")
data class SavingsPlan(
    @PrimaryKey val memberId: String,
    val planFrequency: String = "Monthly", // "Weekly", "Bi-weekly", "Monthly"
    val targetAmount: Double = 100000.0,
    val nextDueDate: String = "2026-07-15",
    val enableEmail: Boolean = true,
    val enableSms: Boolean = true,
    val enableInApp: Boolean = true,
    val reminderDaysBefore: Int = 3
) : java.io.Serializable

@Entity(tableName = "member_referrals", indices = [Index(value = ["referrerId"]), Index(value = ["refereeId"])])
data class MemberReferral(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val referrerId: String, // member who referred
    val refereeId: String,  // new member referred
    val referralCodeUsed: String,
    val status: String = "PENDING", // "PENDING", "COMPLETED_ACTIVATION", "COMPLETED_DEPOSIT", "REWARDED"
    val rewardAmount: Double = 15000.0, // UGX 15,000 bonus
    val dateReferred: String,
    val dateCompleted: String = ""
) : java.io.Serializable

@Entity(tableName = "sync_queue", indices = [Index(value = ["status"])])
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String,      // "SAVINGS_PAYMENT", "LOAN_REPAYMENT", "LOAN_APPLICATION", "USER_REGISTER"
    val payloadJson: String,     // JSON string representing data to sync to Cloud Spanner
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val retries: Int = 0,
    val lastError: String = ""
) : java.io.Serializable

@Entity(tableName = "declared_dividends")
data class DeclaredDividend(
    @PrimaryKey val year: Int,
    val isLocked: Boolean,
    val declaredProfitPool: Double,
    val declarationDate: String,
    val algorithmVersion: String
) : java.io.Serializable

@Entity(tableName = "dividend_audit_records", indices = [Index(value = ["year", "memberId"])])
data class DividendAuditRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val year: Int,
    val memberId: String,
    val fullName: String,
    val rawWeightedScore: Double,
    val attendance: Double,
    val averageCompliance: Double,
    val consistencyIndex: Double,
    val eligibilityStatus: String,
    val ownershipRatio: Double,
    val profitPool: Double,
    val allocatedDividend: Double,
    val calculationTimestamp: String,
    val algorithmVersion: String
) : java.io.Serializable

