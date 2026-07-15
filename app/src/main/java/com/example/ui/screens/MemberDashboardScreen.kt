package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.LemonGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun MemberDashboardScreen(
    memberId: String,
    profile: MemberProfile?,
    rule: SavingsRule,
    payments: List<SavingsPayment>,
    allPayments: List<SavingsPayment> = emptyList(),
    profilesList: List<MemberProfile> = emptyList(),
    allLoans: List<LoanApplication> = emptyList(),
    allExpenses: List<SaccoExpense> = emptyList(),
    loans: List<LoanApplication>,
    notifications: List<SaccoNotification>,
    savingsPlan: SavingsPlan?,
    referrals: List<MemberReferral>,
    onUpdateSavingsPlan: (SavingsPlan) -> Unit,
    onQuickAction: (String) -> Unit,
    onUpdateProfileAndName: (String, MemberProfile) -> Unit = { _, _ -> },
    onResetPassword: suspend (String, String) -> Pair<Boolean, String> = { _, _ -> Pair(false, "") },
    isOnline: Boolean = true,
    isSyncing: Boolean = false
) {
    val scrollState = rememberScrollState()

    var showEditProfileDialog by remember { mutableStateOf(false) }

    var editName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
    var editNationalId by remember(profile) { mutableStateOf(profile?.nationalId ?: "") }
    var editPhone by remember(profile) { mutableStateOf(profile?.phoneNumber ?: "") }
    var editEmail by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var editAddress by remember(profile) { mutableStateOf(profile?.physicalAddress ?: "") }
    var editOccupation by remember(profile) { mutableStateOf(profile?.occupation ?: "") }
    var editEmployer by remember(profile) { mutableStateOf(profile?.employer ?: "") }
    var editEmergencyContact by remember(profile) { mutableStateOf(profile?.emergencyContact ?: "") }
    var editBankAccount by remember(profile) { mutableStateOf(profile?.bankAccount ?: "") }
    var editMobileMoneyNumber by remember(profile) { mutableStateOf(profile?.mobileMoneyNumber ?: "") }
    var editNextOfKin by remember(profile) { mutableStateOf(profile?.nextOfKin ?: "") }

    var editNewPin by remember { mutableStateOf("") }
    var editConfirmPin by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Calculate figures
    val approvedPayments = payments.filter { it.status == VerificationStatus.APPROVED }
    val totalSavings = approvedPayments.sumOf { it.amountPaid }
    
    val currentCalendar = remember { java.util.Calendar.getInstance() }
    val currentYear = remember { currentCalendar.get(java.util.Calendar.YEAR) }
    val currentMonthIndex = remember { currentCalendar.get(java.util.Calendar.MONTH) + 1 }
    val currentMonthName = remember { 
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[currentMonthIndex - 1] 
    }
    
    // Check current month savings contribution
    val currentMonthSavings = payments.find { it.cycleMonthIndex == currentMonthIndex && it.cycleYear == currentYear }
    val currentMonthPaid = currentMonthSavings?.amountPaid ?: 0.0
    val targetAmount = rule.monthlyAmount
    val savingsProgress = if (targetAmount > 0) (currentMonthPaid / targetAmount).toFloat().coerceIn(0f, 1f) else 0f

    // Loans Calculations
    val activeLoans = loans.filter { it.status == LoanStatus.DISBURSED }
    val pendingLoans = loans.filter { it.status == LoanStatus.PENDING }
    val completedLoans = loans.filter { it.status == LoanStatus.COMPLETED }
    val outstandingBalance = activeLoans.sumOf { it.outstandingBalance }
    val eligibleLoanMultiplier = 1.5
    val eligibleLoanAmount = totalSavings * eligibleLoanMultiplier

    // Current month penalty check
    val unpaidPenalties = payments.filter { it.status == VerificationStatus.APPROVED }.sumOf { it.penaltyAmountCharged }

    val dividendMetricsMap = remember(profilesList, allPayments, allLoans, allExpenses, rule) {
        DividendEngine.computeMetrics(
            profiles = profilesList,
            allPayments = allPayments,
            allLoans = allLoans,
            allExpenses = allExpenses,
            rule = rule
        )
    }

    val myMetrics = dividendMetricsMap[memberId] ?: MemberDividendMetrics(
        memberId = memberId,
        fullName = profile?.fullName ?: "Member",
        rawWeightedScore = 0.0,
        monthlyCompliance = emptyMap(),
        averageCompliance = 0.0,
        attendance = 0.0,
        consistencyIndex = 0.0,
        consistencyAdjustedScore = 0.0,
        ownershipRatio = 0.0,
        projectedDividend = 0.0,
        forecastedDividendWithNextMonthSavings = 0.0
    )

    val averageComplianceScore = myMetrics.averageCompliance
    val memberWeightedSavingScore = myMetrics.consistencyAdjustedScore
    val memberEstimatedDividend = myMetrics.projectedDividend
    val finalAnnualRewardSharePercent = myMetrics.ownershipRatio * 100.0

    val monthlyRequiredAmount = rule.monthlyAmount.coerceAtLeast(1.0)
    val memberCompliancePerMonth = myMetrics.monthlyCompliance

    var showTimeWeightedBreakdown by remember { mutableStateOf(false) }

    // Format currency helper (Ugandan Shilling UGX)
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // 0. Hybrid Edge Caching & Cloud Sync Health Status Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) LemonGreen else Color(0xFFFF9800))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline) "Edge Connected" else "Edge Buffer Offline",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF9800)
                        )
                    )
                }

                if (isSyncing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Syncing with Spanner...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                } else {
                    Text(
                        text = "Ledger Reconciled",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = LemonGreen
                        )
                    )
                }
            }
        }

        // 1. Welcome Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Initial Letter Avatar Placeholder
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile?.fullName?.firstOrNull()?.toString() ?: "U",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hello, ${profile?.fullName ?: "Member"}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Membership No: ${profile?.membershipNumber ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "Status: ${profile?.status ?: MemberStatus.ACTIVE}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (profile?.status == MemberStatus.ACTIVE) Color(0xFF4ADE80) else Color(0xFFFBBF24)
                    )
                }

                IconButton(
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.testTag("edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile & PIN",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // 2. Main Stat Cards Row (Horizontal / Scrollable or Grid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Savings",
                value = formatter.format(totalSavings),
                icon = Icons.Default.Savings,
                backgroundColor = Color(0xFF386641),
                iconColor = Color(0xFFBCDFB1),
                textColor = Color.White,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Outstanding Loan",
                value = formatter.format(outstandingBalance),
                icon = Icons.Default.AccountBalanceWallet,
                backgroundColor = Color.White,
                iconColor = if (outstandingBalance > 0) Color(0xFFBC4749) else Color(0xFF7A7E74),
                textColor = Color(0xFF1D1B1E),
                borderColor = Color(0xFFDDE6D5),
                modifier = Modifier.weight(1f)
            )
        }

        // Three Independent Metrics Board (Compliance, Reward Points, Estimated Dividend)
        Text(
            text = "Your SACCO Performance Metrics",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().testTag("four_metrics_board_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Compliance Score
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("On-Time", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(String.format("%.0f%%", averageComplianceScore), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }

                // Metric 2: Weighted Score
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Reward Pts", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(String.format("%,.0f", memberWeightedSavingScore), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }

                // Metric 3: Estimated Dividend
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Est. Dividend", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatter.format(memberEstimatedDividend), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), maxLines = 1)
                    }
                }
            }
        }

        // 3. Savings Target Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE6D5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$currentMonthName Savings Contribution",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF1D1B1E))
                    )
                    Text(
                        text = "${(savingsProgress * 100).toInt()}% Paid",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF386641)
                    )
                }

                LinearProgressIndicator(
                    progress = { savingsProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFF386641),
                    trackColor = Color(0xFFDDE6D5)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Saved: ${formatter.format(currentMonthPaid)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1D1B1E).copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Target: ${formatter.format(targetAmount)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF386641)
                    )
                }
            }
        }

        // Time-Weighted Monthly performance Board & Annual Reward Share
        Card(
            modifier = Modifier.fillMaxWidth().testTag("time_weighted_reward_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Annual Dividend Share",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("%.0f%% Share", finalAnnualRewardSharePercent),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
 
                Text(
                    text = "Earn more dividend bonus by saving consistently and making deposits early in the year!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
 
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("On-Time Compliance", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%.0f%%", averageComplianceScore), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Required target met", fontSize = 9.sp, color = Color.Gray)
                        }
                    }
 
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("My Reward Points", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(String.format("%,.0f pts", memberWeightedSavingScore), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            Text("Based on deposit timing", fontSize = 9.sp, color = Color.Gray)
                        }
                    }
                }
 
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimeWeightedBreakdown = !showTimeWeightedBreakdown }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showTimeWeightedBreakdown) "Hide Monthly Details" else "View Monthly Details",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    )
                    Icon(
                        imageVector = if (showTimeWeightedBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
 
                if (showTimeWeightedBreakdown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Month", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                            Text("Savings", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
                            Text("On-Time", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                            Text("Multiplier", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            Text("Share Points", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                        }
 
                        val monthsShortList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        for (i in 0..11) {
                            val cycleMonth = i + 1
                            val monthSavings = approvedPayments.filter { it.cycleMonthIndex == cycleMonth }.sumOf { it.amountPaid }
                            val monthCompliance = memberCompliancePerMonth[cycleMonth] ?: 0.0
                            val monthWeight = 13 - cycleMonth
                            
                            val monthWeighted = approvedPayments.filter { it.cycleMonthIndex == cycleMonth }.fold(0.0) { acc, p -> 
                                val actualMonth = DividendEngine.getActualMonthIndex(p.datePaid, p.cycleMonthIndex)
                                acc + p.amountPaid * (13 - actualMonth)
                            }
 
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(monthsShortList[i], fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
                                Text(formatter.format(monthSavings), fontSize = 10.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End)
                                Text(String.format("%.0f%%", monthCompliance), fontSize = 10.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = if (monthCompliance >= 100.0) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
                                Text("${monthWeight}x", fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color.Gray)
                                Text(String.format("%,.0f pts", monthWeighted), fontSize = 10.sp, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            }
                            if (i < 11) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            }
                        }
                    }
                }
            }
        }

        // 4. Secondary Stat Row: Eligibility and Penalties
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Max Loan Eligibility",
                value = formatter.format(eligibleLoanAmount),
                icon = Icons.Default.TrendingUp,
                backgroundColor = Color.White,
                iconColor = Color(0xFF386641),
                textColor = Color(0xFF1D1B1E),
                borderColor = Color(0xFFDDE6D5),
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Total Penalties",
                value = formatter.format(unpaidPenalties),
                icon = Icons.Default.Gavel,
                backgroundColor = Color.White,
                iconColor = if (unpaidPenalties > 0) Color(0xFFBC4749) else Color(0xFF7A7E74),
                textColor = Color(0xFF1D1B1E),
                borderColor = Color(0xFFDDE6D5),
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Quick Actions Section
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                label = "Save Money",
                icon = Icons.Default.UploadFile,
                containerColor = Color(0xFFDDE6D5),
                contentColor = Color(0xFF386641),
                onClick = { onQuickAction("SAVINGS") },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                label = "Apply Loan",
                icon = Icons.Default.PostAdd,
                containerColor = Color(0xFFF2E8CF),
                contentColor = Color(0xFFBC4749),
                onClick = { onQuickAction("LOANS") },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                label = "Guarantors",
                icon = Icons.Default.VerifiedUser,
                containerColor = Color(0xFFF7E1D7),
                contentColor = Color(0xFF6A4C41),
                onClick = { onQuickAction("GUARANTOR") },
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                label = "AI Coach",
                icon = Icons.Default.Psychology,
                containerColor = Color(0xFFE0E2EC),
                contentColor = Color(0xFF1B1B1F),
                onClick = { onQuickAction("AI_COACH") },
                modifier = Modifier.weight(1f)
            )
        }

        // 6. In-App Notifications / Announcements Panel
        if (notifications.isNotEmpty()) {
            Text(
                text = "Alerts & Announcements",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notifications.take(3).forEach { notif ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (notif.type) {
                                    "SAVINGS" -> Icons.Default.Savings
                                    "LOAN" -> Icons.Default.AccountBalanceWallet
                                    "PENALTY" -> Icons.Default.Gavel
                                    else -> Icons.Default.Campaign
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(notif.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                Text(notif.content, style = MaterialTheme.typography.labelSmall)
                                Text(notif.timestamp, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // 7. Recent Transactions List
        Text(
            text = "Recent Transactions Activity",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        if (approvedPayments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No savings transactions recorded yet.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = LemonGreen
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    approvedPayments.take(4).forEach { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text("Savings Contribution", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(p.datePaid.take(16), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            Text(
                                text = "+${formatter.format(p.amountPaid)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ----------------- SAVINGS PLANNER & REMINDERS -----------------
        Text(
            text = "Savings Planner & Reminders",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp).testTag("savings_planner_header")
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentPlan = savingsPlan ?: SavingsPlan(
                    memberId = memberId,
                    planFrequency = "Monthly",
                    targetAmount = 100000.0,
                    nextDueDate = "2026-07-15",
                    enableEmail = true,
                    enableSms = true,
                    enableInApp = true,
                    reminderDaysBefore = 3
                )

                var frequency by remember(savingsPlan) { mutableStateOf(currentPlan.planFrequency) }
                var targetAmt by remember(savingsPlan) { mutableStateOf(currentPlan.targetAmount.toInt().toString()) }
                var nextDue by remember(savingsPlan) { mutableStateOf(currentPlan.nextDueDate) }
                var emailNotif by remember(savingsPlan) { mutableStateOf(currentPlan.enableEmail) }
                var smsNotif by remember(savingsPlan) { mutableStateOf(currentPlan.enableSms) }
                var inAppNotif by remember(savingsPlan) { mutableStateOf(currentPlan.enableInApp) }
                var daysBefore by remember(savingsPlan) { mutableStateOf(currentPlan.reminderDaysBefore.toString()) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Frequency", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Weekly", "Monthly").forEach { freq ->
                                FilterChip(
                                    selected = frequency == freq,
                                    onClick = { frequency = freq },
                                    label = { Text(freq, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).testTag("reminder_freq_$freq")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Target (UGX)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = targetAmt,
                            onValueChange = { targetAmt = it.filter { char -> char.isDigit() } },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder_target_input")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Next Due Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = nextDue,
                            onValueChange = { nextDue = it },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder_due_input")
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Days Before", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = daysBefore,
                            onValueChange = { daysBefore = it.filter { char -> char.isDigit() } },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder_days_input")
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text("Channels Preference", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Text("Email Alerts", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = emailNotif,
                        onCheckedChange = { emailNotif = it },
                        modifier = Modifier.testTag("email_alert_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Text("SMS Notifications", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = smsNotif,
                        onCheckedChange = { smsNotif = it },
                        modifier = Modifier.testTag("sms_alert_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Text("In-App Reminders", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = inAppNotif,
                        onCheckedChange = { inAppNotif = it },
                        modifier = Modifier.testTag("inapp_alert_switch")
                    )
                }

                val localContext = androidx.compose.ui.platform.LocalContext.current
                Button(
                    onClick = {
                        val updated = currentPlan.copy(
                            planFrequency = frequency,
                            targetAmount = targetAmt.toDoubleOrNull() ?: 100000.0,
                            nextDueDate = nextDue,
                            enableEmail = emailNotif,
                            enableSms = smsNotif,
                            enableInApp = inAppNotif,
                            reminderDaysBefore = daysBefore.toIntOrNull() ?: 3
                        )
                        onUpdateSavingsPlan(updated)
                        android.widget.Toast.makeText(localContext, "Preferences Saved!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_plan_preferences_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Plan & Preferences")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ----------------- REFERRAL PROGRAM -----------------
        Text(
            text = "Member Referral Program",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp).testTag("referral_program_header")
        )

        val refCode = profile?.membershipNumber?.replace("SACCO-", "")?.let { "$it-REF" } ?: "SACCO-REF"
        val inviteLink = "https://sacco.org/join?ref=$refCode"
        val totalReferralsEarned = referrals.filter { it.status != "PENDING" }.sumOf { it.rewardAmount }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Your Referral Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(refCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val localContext = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(inviteLink))
                            android.widget.Toast.makeText(localContext, "Invite link copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("copy_referral_link_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Link", fontSize = 12.sp)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Bonus Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(formatter.format(totalReferralsEarned), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF386641))
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDDE6D5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF386641))
                        }
                    }
                }

                Text(
                    text = "Referrals Tracker (${referrals.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                if (referrals.isEmpty()) {
                    Text(
                        text = "No successful referrals yet. Share your invite link to earn UGX 15,000 for each activated signup!",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = LemonGreen,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        referrals.forEach { ref ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Member ID: ${ref.refereeId.substringBefore("@")}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Referred: ${ref.dateReferred.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }

                                val isCompleted = ref.status != "PENDING"
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCompleted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isCompleted) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                        )
                                        Text(
                                            text = if (isCompleted) "Bonus Awarded" else "Pending Activation",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCompleted) Color(0xFF2E7D32) else Color(0xFFE65100),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Update Profile & Security", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Personal Information", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                    
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = editNationalId,
                        onValueChange = { editNationalId = it },
                        label = { Text("National ID / Passport Number") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("Physical Address") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editOccupation,
                        onValueChange = { editOccupation = it },
                        label = { Text("Occupation") },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editEmployer,
                        onValueChange = { editEmployer = it },
                        label = { Text("Employer Name") },
                        leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Emergency & Financial Information", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

                    OutlinedTextField(
                        value = editNextOfKin,
                        onValueChange = { editNextOfKin = it },
                        label = { Text("Next of Kin") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editEmergencyContact,
                        onValueChange = { editEmergencyContact = it },
                        label = { Text("Emergency Contact") },
                        leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editBankAccount,
                        onValueChange = { editBankAccount = it },
                        label = { Text("Bank Account Details") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editMobileMoneyNumber,
                        onValueChange = { editMobileMoneyNumber = it },
                        label = { Text("Mobile Money Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Security (Password / PIN Reset)", style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))

                    OutlinedTextField(
                        value = editNewPin,
                        onValueChange = { editNewPin = it },
                        label = { Text("New Password / PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editConfirmPin,
                        onValueChange = { editConfirmPin = it },
                        label = { Text("Confirm New Password / PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (saveMessage.isNotEmpty()) {
                        Text(
                            text = saveMessage,
                            color = if (saveMessage.startsWith("Success")) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isEmpty() || editPhone.isEmpty() || editEmail.isEmpty()) {
                            saveMessage = "Name, phone, and email are required fields."
                        } else if (editNewPin.isNotEmpty() && editNewPin != editConfirmPin) {
                            saveMessage = "Passwords/PINs do not match."
                        } else {
                            coroutineScope.launch {
                                isSaving = true
                                saveMessage = "Saving changes..."
                                
                                // Update profile
                                if (profile != null) {
                                    val updatedProfile = profile.copy(
                                        fullName = editName,
                                        nationalId = editNationalId,
                                        phoneNumber = editPhone,
                                        email = editEmail,
                                        physicalAddress = editAddress,
                                        occupation = editOccupation,
                                        employer = editEmployer,
                                        emergencyContact = editEmergencyContact,
                                        bankAccount = editBankAccount,
                                        mobileMoneyNumber = editMobileMoneyNumber,
                                        nextOfKin = editNextOfKin
                                    )
                                    onUpdateProfileAndName(editName, updatedProfile)
                                }

                                // Reset password if specified
                                var pinSuccess = true
                                if (editNewPin.isNotEmpty()) {
                                    val (success, msg) = onResetPassword(memberId, editNewPin)
                                    if (!success) {
                                        pinSuccess = false
                                        saveMessage = "Error: $msg"
                                    }
                                }

                                isSaving = false
                                if (pinSuccess) {
                                    saveMessage = "Success: Profile updated successfully!"
                                    kotlinx.coroutines.delay(1000)
                                    showEditProfileDialog = false
                                    saveMessage = ""
                                    editNewPin = ""
                                    editConfirmPin = ""
                                }
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditProfileDialog = false
                        saveMessage = ""
                        editNewPin = ""
                        editConfirmPin = ""
                    },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }
  }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(24.dp),
        border = borderColor?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = textColor.copy(alpha = 0.7f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
        }
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = contentColor),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
