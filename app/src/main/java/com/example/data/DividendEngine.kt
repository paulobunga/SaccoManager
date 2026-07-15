package com.example.data

// Holds Sacco-wide Income & Expenses for profit projection
data class SaccoProfitPool(
    val loanInterest: Double,
    val registrationFees: Double,
    val penalties: Double,
    val investmentIncome: Double,
    val expenses: Double
) {
    val totalIncome: Double get() = loanInterest + registrationFees + penalties + investmentIncome
    val netProfit: Double get() = (totalIncome - expenses).coerceAtLeast(0.0)
}

// Holds a single member's 5-stage metrics
data class MemberDividendMetrics(
    val memberId: String,
    val fullName: String,
    // Stage 1: Weighted Score
    val rawWeightedScore: Double,
    // Stage 2: Compliance per month & average compliance
    val monthlyCompliance: Map<Int, Double>,
    val averageCompliance: Double,
    // Stage 3: Consistency Index (Average Compliance * Attendance)
    val attendance: Double, // active months / 12
    val consistencyIndex: Double, // averageCompliance * attendance
    // Stage 4: Adjusted Score & Ownership Ratio
    val consistencyAdjustedScore: Double, // Now keeps raw weighted score points to preserve economic value of money
    val ownershipRatio: Double, // rawWeightedScore / totalSaccoRawScore
    // Stage 5: Projected Dividend
    val projectedDividend: Double,
    // Forecast metric
    val forecastedDividendWithNextMonthSavings: Double = 0.0,
    
    // New Audit and Governance Fields
    val eligibilityStatus: String = "Fully Qualified",
    val eligibilityMultiplier: Double = 1.0,
    val maxEligibleDividend: Double = 0.0
)

object DividendEngine {

    // Helper to get actual payment month (or fallback to cycle month)
    fun getActualMonthIndex(datePaid: String, cycleMonthIndex: Int): Int {
        return try {
            if (datePaid.length >= 7 && datePaid[4] == '-') {
                val monthPart = datePaid.substring(5, 7).toIntOrNull()
                if (monthPart != null && monthPart in 1..12) {
                    monthPart
                } else {
                    cycleMonthIndex
                }
            } else {
                cycleMonthIndex
            }
        } catch (e: Exception) {
            cycleMonthIndex
        }
    }

    // Helper to determine eligibility tier based on consistency index
    fun getEligibility(consistencyIndex: Double): Pair<String, Double> {
        return when {
            consistencyIndex >= 90.0 -> Pair("Fully Qualified (100% Payout)", 1.0)
            consistencyIndex >= 75.0 -> Pair("Highly Compliant (90% Payout)", 0.90)
            consistencyIndex >= 50.0 -> Pair("Prorated (75% Payout)", 0.75)
            else -> Pair("Basic (50% Payout)", 0.50)
        }
    }

    // Main 5-Stage computation
    fun computeMetrics(
        profiles: List<MemberProfile>,
        allPayments: List<SavingsPayment>,
        allLoans: List<LoanApplication>,
        allExpenses: List<SaccoExpense>,
        rule: SavingsRule,
        // Simulation parameter overrides (useful for administrators)
        simRegistrationFeePerMember: Double = 50000.0,
        simInvestmentIncome: Double = 2000000.0
    ): Map<String, MemberDividendMetrics> {
        val approvedPayments = allPayments.filter { it.status == VerificationStatus.APPROVED }
        
        // Income components (Note: late penalties and fee income are explicit components of total profit)
        val loanInterest = allLoans.sumOf { it.interestPaid }
        val registrationFees = profiles.size * simRegistrationFeePerMember
        val penalties = approvedPayments.sumOf { it.penaltyAmountCharged }
        val investmentIncome = simInvestmentIncome
        val totalExpenses = allExpenses.sumOf { it.amount }
        
        val pool = SaccoProfitPool(
            loanInterest = loanInterest,
            registrationFees = registrationFees,
            penalties = penalties,
            investmentIncome = investmentIncome,
            expenses = totalExpenses
        )
        
        val netProfit = pool.netProfit
        val monthlyRequiredAmount = rule.monthlyAmount.coerceAtLeast(1.0)
        
        // 1. First, calculate raw weighted score, compliance, attendance, and consistency index for each unique member
        val uniqueMemberIds = (profiles.map { m -> m.memberId } + allPayments.map { p -> p.memberId }).distinct()
        
        // Temporary holder to store intermediate values
        data class TempMemberMetrics(
            val memberId: String,
            val fullName: String,
            val rawWeightedScore: Double,
            val monthlyCompliance: Map<Int, Double>,
            val averageCompliance: Double,
            val attendance: Double,
            val consistencyIndex: Double,
            val consistencyAdjustedScore: Double
        )
        
        val tempMetricsList = uniqueMemberIds.map { mId ->
            val memberProfile = profiles.find { it.memberId == mId }
            val fullName = memberProfile?.fullName ?: "Member $mId"
            val mPayments = approvedPayments.filter { it.memberId == mId }
            
            // Stage 1: Weighted Score Points = Sum(Amount * (13 - actualMonth)) (Cumulative, pure economic value)
            val rawWeightedScore = mPayments.sumOf { p ->
                val actualMonth = getActualMonthIndex(p.datePaid, p.cycleMonthIndex)
                val monthsRemaining = 13 - actualMonth
                p.amountPaid * monthsRemaining
            }
            
            // Stage 2: Compliance % per month
            val mSavingsByCycleMonth = (1..12).associateWith { month ->
                mPayments.filter { it.cycleMonthIndex == month }.sumOf { it.amountPaid }
            }
            val monthlyCompliance = (1..12).associateWith { month ->
                val saved = mSavingsByCycleMonth[month] ?: 0.0
                ((saved / monthlyRequiredAmount) * 100.0).coerceAtMost(100.0)
            }
            val averageCompliance = if (monthlyCompliance.isNotEmpty()) monthlyCompliance.values.average() else 0.0
            
            // Stage 3: Consistency Index
            // Attendance = Active Months Saved (months with approved payment > 0) / 12
            val activeMonthsCount = (1..12).count { m ->
                mPayments.any { it.cycleMonthIndex == m && it.amountPaid > 0.0 }
            }
            val attendance = activeMonthsCount / 12.0
            val consistencyIndex = averageCompliance * attendance // e.g. 100% * 0.5 = 50%
            
            // Adjusted Score is mapped to raw weighted score to determine ownership pool share purely based on savings
            val consistencyAdjustedScore = rawWeightedScore
            
            TempMemberMetrics(
                memberId = mId,
                fullName = fullName,
                rawWeightedScore = rawWeightedScore,
                monthlyCompliance = monthlyCompliance,
                averageCompliance = averageCompliance,
                attendance = attendance,
                consistencyIndex = consistencyIndex,
                consistencyAdjustedScore = consistencyAdjustedScore
            )
        }
        
        // Sum of all raw weighted scores determines the total SACCO pool size
        val totalSaccoRawScoreSum = tempMetricsList.sumOf { it.rawWeightedScore }.coerceAtLeast(0.0001)
        
        // 2. Compute Stage 4 Ownership Ratio & Stage 5 Dividend
        val finalMetricsMap = tempMetricsList.associate { temp ->
            // Ownership ratio determines ownership of the profit pool
            val ownershipRatio = temp.rawWeightedScore / totalSaccoRawScoreSum
            val maxEligibleDividend = ownershipRatio * netProfit
            
            // Compliance/Consistency influences eligibility and payout percentage (governance multiplier)
            val (eligibilityStatus, eligibilityMultiplier) = getEligibility(temp.consistencyIndex)
            val projectedDividend = maxEligibleDividend * eligibilityMultiplier
            
            // 3. Simulating Forecast: what if the member saves monthlyRequiredAmount in the first unsaved/incomplete month?
            // Let's find first month with compliance < 100%
            val firstUnsavedMonth = (1..12).find { m -> (temp.monthlyCompliance[m] ?: 0.0) < 100.0 } ?: 1
            
            // Build hypothetical payments
            val hypotheticalPayment = SavingsPayment(
                memberId = temp.memberId,
                memberName = temp.fullName,
                cycleMonthIndex = firstUnsavedMonth,
                amountPaid = monthlyRequiredAmount,
                remainingBalance = 0.0,
                datePaid = "2026-${String.format("%02d", firstUnsavedMonth)}-15", // mid month
                status = VerificationStatus.APPROVED,
                receiptNumber = "FCST-TEMP"
            )
            
            val hypPayments = approvedPayments.filter { it.memberId == temp.memberId } + listOf(hypotheticalPayment)
            
            // Recalculate hyp weighted score
            val hypRawWeightedScore = hypPayments.sumOf { p ->
                val actualMonth = getActualMonthIndex(p.datePaid, p.cycleMonthIndex)
                val monthsRemaining = 13 - actualMonth
                p.amountPaid * monthsRemaining
            }
            
            // Recalculate hyp compliance
            val hypSavingsByCycleMonth = (1..12).associateWith { month ->
                hypPayments.filter { it.cycleMonthIndex == month }.sumOf { it.amountPaid }
            }
            val hypMonthlyCompliance = (1..12).associateWith { month ->
                val saved = hypSavingsByCycleMonth[month] ?: 0.0
                ((saved / monthlyRequiredAmount) * 100.0).coerceAtMost(100.0)
            }
            val hypAverageCompliance = hypMonthlyCompliance.values.average()
            val hypActiveMonthsCount = (1..12).count { m ->
                hypPayments.any { m2 -> m2.cycleMonthIndex == m && m2.amountPaid > 0.0 }
            }
            val hypAttendance = hypActiveMonthsCount / 12.0
            val hypConsistencyIndex = hypAverageCompliance * hypAttendance
            
            val (_, hypEligibilityMultiplier) = getEligibility(hypConsistencyIndex)
            
            // The total Sacco raw score sum would increase by the difference:
            val scoreDiff = hypRawWeightedScore - temp.rawWeightedScore
            val hypSaccoRawScoreSum = totalSaccoRawScoreSum + scoreDiff
            
            val hypOwnershipRatio = hypRawWeightedScore / hypSaccoRawScoreSum
            val hypMaxEligible = hypOwnershipRatio * netProfit
            val forecastedDividend = hypMaxEligible * hypEligibilityMultiplier
            
            temp.memberId to MemberDividendMetrics(
                memberId = temp.memberId,
                fullName = temp.fullName,
                rawWeightedScore = temp.rawWeightedScore,
                monthlyCompliance = temp.monthlyCompliance,
                averageCompliance = temp.averageCompliance,
                attendance = temp.attendance,
                consistencyIndex = temp.consistencyIndex,
                consistencyAdjustedScore = temp.rawWeightedScore,
                ownershipRatio = ownershipRatio,
                projectedDividend = projectedDividend,
                forecastedDividendWithNextMonthSavings = forecastedDividend,
                eligibilityStatus = eligibilityStatus,
                eligibilityMultiplier = eligibilityMultiplier,
                maxEligibleDividend = maxEligibleDividend
            )
        }
        
        return finalMetricsMap
    }
}
