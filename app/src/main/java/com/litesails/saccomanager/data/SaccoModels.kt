package com.litesails.saccomanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.squareup.moshi.Json
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
    @Json(name = "membership_number") val membershipNumber: String = "",
    // Clerk Auth owns all credentials — password is no longer stored locally.
    // REQ-5: clerkUserId links this local record to the Clerk Auth account.
    @Json(name = "clerk_user_id") val clerkUserId: String = ""
) : java.io.Serializable

@Entity(tableName = "member_profiles")
data class MemberProfile(
    @PrimaryKey @Json(name = "member_id") val memberId: String, // same as SaccoUser id
    @Json(name = "membership_number") val membershipNumber: String,
    @Json(name = "national_id") val nationalId: String,
    @Json(name = "full_name") val fullName: String,
    val gender: String,
    @Json(name = "date_of_birth") val dateOfBirth: String,
    @Json(name = "phone_number") val phoneNumber: String,
    val email: String,
    @Json(name = "physical_address") val physicalAddress: String,
    val occupation: String,
    val employer: String,
    @Json(name = "emergency_contact") val emergencyContact: String,
    @Json(name = "bank_account") val bankAccount: String,
    @Json(name = "mobile_money_number") val mobileMoneyNumber: String,
    @Json(name = "date_joined") val dateJoined: String,
    val status: MemberStatus = MemberStatus.PENDING,
    @Json(name = "next_of_kin") val nextOfKin: String,
    @Json(name = "profile_photo_url") val profilePhotoUrl: String = "",
    @Json(name = "signature_url") val signatureUrl: String = "",
    @Json(name = "max_guarantee_exposure") val maxGuaranteeExposure: Double = 5000000.0, // UGX
    @Json(name = "referred_by_code") val referredByCode: String = "" // Added to support referral program
) : java.io.Serializable

@Entity(tableName = "savings_rules")
data class SavingsRule(
    @PrimaryKey val id: Int = 1, // singleton config
    @Json(name = "monthly_amount") val monthlyAmount: Double = 100000.0, // Default UGX 100,000
    @Json(name = "grace_period_days") val gracePeriodDays: Int = 5,
    @Json(name = "penalty_fixed_amount") val penaltyFixedAmount: Double = 10000.0,
    @Json(name = "penalty_percentage") val penaltyPercentage: Double = 5.0, // 5% late fee
    @Json(name = "is_penalty_percentage") val isPenaltyPercentage: Boolean = false, // false = fixed, true = percentage
    @Json(name = "max_advance_payment_months") val maxAdvancePaymentMonths: Int = 12,
    @Json(name = "min_saving_amount") val minSavingAmount: Double = 10000.0,
    @Json(name = "max_saving_amount") val maxSavingAmount: Double = 5000000.0,
    @Json(name = "savings_interest_rate") val savingsInterestRate: Double = 8.0 // Default 8% annual savings interest rate
) : java.io.Serializable

@Entity(tableName = "savings_payments", indices = [Index(value = ["memberId"])])
data class SavingsPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "member_id") val memberId: String,
    @Json(name = "member_name") val memberName: String,
    @Json(name = "cycle_month_index") val cycleMonthIndex: Int, // 1 for Jan, 2 for Feb, ..., 12 for Dec
    @Json(name = "cycle_year") val cycleYear: Int = 2026,
    @Json(name = "amount_paid") val amountPaid: Double,
    @Json(name = "remaining_balance") val remainingBalance: Double,
    @Json(name = "date_paid") val datePaid: String,
    @Json(name = "verified_by") val verifiedBy: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    @Json(name = "receipt_number") val receiptNumber: String,
    @Json(name = "receipt_image_url") val receiptImageUrl: String = "",
    val branch: String = "",
    @Json(name = "bank_name") val bankName: String = "",
    @Json(name = "transaction_id") val transactionId: String = "",
    val notes: String = "",
    @Json(name = "penalty_amount_charged") val penaltyAmountCharged: Double = 0.0,
    @Json(name = "iotec_request_id") val iotecRequestId: String = "",
    @Json(name = "iotec_status") val iotecStatus: String = "",
    @Json(name = "contribution_type") val contributionType: String = "SAVINGS",
    @Json(name = "iotec_polling_attempts") val iotecPollingAttempts: Int = 0,
    @Json(name = "iotec_polling_started_at") val iotecPollingStartedAt: String? = null,
    @Json(name = "iotec_polling_completed_at") val iotecPollingCompletedAt: String? = null
) : java.io.Serializable

@Entity(tableName = "loan_products")
data class LoanProduct(
    @PrimaryKey val id: String, // unique code (e.g., EMERGENCY, DEVELOPMENT)
    val name: String,
    @Json(name = "interest_rate_for_members") val interestRateForMembers: Double, // e.g., 5.0 (percent)
    @Json(name = "interest_rate_for_non_members") val interestRateForNonMembers: Double, // e.g., 15.0
    @Json(name = "repayment_period_months") val repaymentPeriodMonths: Int,
    @Json(name = "max_loan_multiplier") val maxLoanMultiplier: Double, // e.g., 1.5, 2.0, 3.0
    @Json(name = "interest_type") val interestType: InterestType = InterestType.FLAT_RATE
) : java.io.Serializable

@Entity(tableName = "loan_applications", indices = [Index(value = ["memberId"]), Index(value = ["guarantorId"])])
data class LoanApplication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "member_id") val memberId: String,
    @Json(name = "applicant_name") val applicantName: String,
    val purpose: String,
    @Json(name = "amount_requested") val amountRequested: Double,
    @Json(name = "repayment_period_months") val repaymentPeriodMonths: Int,
    @Json(name = "repayment_frequency") val repaymentFrequency: String = "Monthly",
    val comments: String = "",
    @Json(name = "guarantor_id") val guarantorId: String = "", // another member
    @Json(name = "guarantor_approved") val guarantorApproved: Boolean = false,
    val status: LoanStatus = LoanStatus.PENDING,
    @Json(name = "interest_rate") val interestRate: Double = 5.0,
    @Json(name = "approval_notes") val approvalNotes: String = "",
    @Json(name = "date_applied") val dateApplied: String,
    @Json(name = "original_savings_balance") val originalSavingsBalance: Double = 0.0,
    @Json(name = "loan_score") val loanScore: String = "N/A", // AI Score (e.g., "A - Safe")
    @Json(name = "loan_score_analysis") val loanScoreAnalysis: String = "", // Detailed AI output
    @Json(name = "principal_paid") val principalPaid: Double = 0.0,
    @Json(name = "interest_paid") val interestPaid: Double = 0.0,
    @Json(name = "outstanding_balance") val outstandingBalance: Double = 0.0,
    @Json(name = "last_repayment_date") val lastRepaymentDate: String = ""
) : java.io.Serializable

@Entity(tableName = "loan_repayments", indices = [Index(value = ["loanId"]), Index(value = ["memberId"])])
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "loan_id") val loanId: Int,
    @Json(name = "member_id") val memberId: String,
    @Json(name = "installment_number") val installmentNumber: Int,
    @Json(name = "amount_paid") val amountPaid: Double,
    @Json(name = "date_paid") val datePaid: String,
    @Json(name = "principal_paid") val principalPaid: Double,
    @Json(name = "interest_paid") val interestPaid: Double,
    @Json(name = "late_fee_paid") val lateFeePaid: Double,
    @Json(name = "receipt_number") val receiptNumber: String,
    val status: VerificationStatus = VerificationStatus.APPROVED
) : java.io.Serializable

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "operator_name") val operatorName: String,
    @Json(name = "operator_role") val operatorRole: String,
    val action: String,
    val details: String,
    val timestamp: String,
    @Json(name = "ip_address") val ipAddress: String = "" // REQ-15: replaced hardcoded placeholder IP with empty string
) : java.io.Serializable

@Entity(tableName = "sacco_notifications", indices = [Index(value = ["recipientId"])])
data class SaccoNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "recipient_id") val recipientId: String, // "ALL" or specific memberId
    val title: String,
    val content: String,
    val timestamp: String,
    @Json(name = "is_read") val isRead: Boolean = false,
    val type: String = "INFO" // SAVINGS, LOAN, PENALTY, ANNOUNCEMENT
) : java.io.Serializable

@Entity(tableName = "sacco_expenses")
data class SaccoExpense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String, // "Rent", "Salaries", "Utilities", "Office Supplies", "Marketing", "Software", "Other"
    val date: String,
    val description: String,
    @Json(name = "paid_by") val paidBy: String
) : java.io.Serializable

@Entity(tableName = "savings_plans")
data class SavingsPlan(
    @PrimaryKey @Json(name = "member_id") val memberId: String,
    @Json(name = "plan_frequency") val planFrequency: String = "Monthly", // "Weekly", "Bi-weekly", "Monthly"
    @Json(name = "target_amount") val targetAmount: Double = 100000.0,
    @Json(name = "next_due_date") val nextDueDate: String = "2026-07-15",
    @Json(name = "enable_email") val enableEmail: Boolean = true,
    @Json(name = "enable_sms") val enableSms: Boolean = true,
    @Json(name = "enable_in_app") val enableInApp: Boolean = true,
    @Json(name = "reminder_days_before") val reminderDaysBefore: Int = 3
) : java.io.Serializable

@Entity(tableName = "member_referrals", indices = [Index(value = ["referrerId"]), Index(value = ["refereeId"])])
data class MemberReferral(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "referrer_id") val referrerId: String, // member who referred
    @Json(name = "referee_id") val refereeId: String,  // new member referred
    @Json(name = "referral_code_used") val referralCodeUsed: String,
    val status: String = "PENDING", // "PENDING", "COMPLETED_ACTIVATION", "COMPLETED_DEPOSIT", "REWARDED"
    @Json(name = "reward_amount") val rewardAmount: Double = 15000.0, // UGX 15,000 bonus
    @Json(name = "date_referred") val dateReferred: String,
    @Json(name = "date_completed") val dateCompleted: String = ""
) : java.io.Serializable

@Entity(tableName = "sync_queue", indices = [Index(value = ["status"])])
data class SyncQueueEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @Json(name = "action_type") val actionType: String,      // "SAVINGS_PAYMENT", "LOAN_REPAYMENT", "LOAN_APPLICATION", "USER_REGISTER"
    @Json(name = "payload_json") val payloadJson: String,     // JSON string representing data to sync to Cloud Spanner
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "SYNCED", "FAILED"
    val retries: Int = 0,
    @Json(name = "last_error") val lastError: String = ""
) : java.io.Serializable

@Entity(tableName = "declared_dividends")
data class DeclaredDividend(
    @PrimaryKey val year: Int,
    @Json(name = "is_locked") val isLocked: Boolean,
    @Json(name = "declared_profit_pool") val declaredProfitPool: Double,
    @Json(name = "declaration_date") val declarationDate: String,
    @Json(name = "algorithm_version") val algorithmVersion: String
) : java.io.Serializable

@Entity(tableName = "dividend_audit_records", indices = [Index(value = ["year", "memberId"])])
data class DividendAuditRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val year: Int,
    @Json(name = "member_id") val memberId: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "raw_weighted_score") val rawWeightedScore: Double,
    val attendance: Double,
    @Json(name = "average_compliance") val averageCompliance: Double,
    @Json(name = "consistency_index") val consistencyIndex: Double,
    @Json(name = "eligibility_status") val eligibilityStatus: String,
    @Json(name = "ownership_ratio") val ownershipRatio: Double,
    val profitPool: Double,
    @Json(name = "allocated_dividend") val allocatedDividend: Double,
    @Json(name = "calculation_timestamp") val calculationTimestamp: String,
    @Json(name = "algorithm_version") val algorithmVersion: String
) : java.io.Serializable

