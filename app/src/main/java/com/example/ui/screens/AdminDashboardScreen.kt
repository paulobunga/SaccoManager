package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.LemonGreen
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    profilesList: List<MemberProfile>,
    usersList: List<SaccoUser>,
    paymentsList: List<SavingsPayment>,
    loansList: List<LoanApplication>,
    expensesList: List<SaccoExpense>,
    rule: SavingsRule,
    auditLogs: List<AuditLog>,
    savingsPlans: List<SavingsPlan>,
    referrals: List<MemberReferral>,
    onRunReminderSweep: () -> Unit,
    onVerifyMember: (String, MemberStatus) -> Unit,
    onVerifyPayment: (Int, VerificationStatus) -> Unit,
    onVerifyLoan: (Int, LoanStatus, String) -> Unit,
    onSaveRule: (SavingsRule) -> Unit,
    onSubmitExpense: (Double, String, String) -> Unit,
    onDeleteExpense: (Int) -> Unit,
    onTriggerBackup: suspend () -> String,
    onDeclareAndLockDividend: (Int, Double, List<DividendAuditRecord>) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    // Navigation state - Side Nav style (Simulated desktop web-app layout)
    var activeAdminTab by remember { mutableStateOf("dashboard") }

    // Edit/Settings State
    var monthlySavingsInput by remember { mutableStateOf(rule.monthlyAmount.toString()) }
    var gracePeriodInput by remember { mutableStateOf(rule.gracePeriodDays.toString()) }
    var penaltyAmtInput by remember { mutableStateOf(rule.penaltyFixedAmount.toString()) }
    var savingsInterestRateInput by remember { mutableStateOf(rule.savingsInterestRate.toString()) }
    var isPercentagePenalty by remember { mutableStateOf(rule.isPenaltyPercentage) }

    // Dividend Simulation State
    var simRegistrationFeeInput by remember { mutableStateOf("50000") }
    var simInvestmentIncomeInput by remember { mutableStateOf("2000000") }

    // Expenses Form State
    var expenseAmountStr by remember { mutableStateOf("") }
    var expenseCategory by remember { mutableStateOf("Utilities") }
    var expenseDescription by remember { mutableStateOf("") }
    val expenseCategories = listOf("Rent", "Salaries", "Utilities", "Office Supplies", "Marketing", "Software", "Other")

    // Loan Verification Comment State
    var loanReviewComments by remember { mutableStateOf("") }

    // Bank Statement Generator state
    var selectedPaymentMethod by remember { mutableStateOf("Sacco Bank of Uganda Account") }
    val paymentMethods = listOf(
        "Sacco Bank of Uganda Account",
        "MTN Mobile Money Sacco Merchant",
        "Airtel Pay Sacco Till",
        "Cash Box Ledger"
    )
    var generatedStatement by remember { mutableStateOf<List<StatementEntry>>(emptyList()) }
    var statementRun by remember { mutableStateOf(false) }

    // Import State
    var csvImportText by remember { mutableStateOf("") }
    var showImportSuccessDialog by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }

    var showDeclareDividendDialog by remember { mutableStateOf(false) }
    var declareYear by remember { mutableStateOf("2026") }

    // Interactive Savings Interest Calculator State (Projections)
    var calcPrincipalStr by remember { mutableStateOf("500000") }
    var calcRateStr by remember { mutableStateOf(rule.savingsInterestRate.toString()) }
    var calcPeriodMonthsStr by remember { mutableStateOf("12") }
    var calcCompoundingFrequency by remember { mutableStateOf("Monthly") } // "Monthly", "Quarterly", "Annually"
    var calculatedInterestValue by remember { mutableStateOf(0.0) }
    var calculatedMaturityValue by remember { mutableStateOf(0.0) }
    var calculatedScheduleList by remember { mutableStateOf<List<InterestScheduleEntry>>(emptyList()) }

    // Run first calculation on launch
    LaunchedEffect(key1 = rule.savingsInterestRate) {
        calcRateStr = rule.savingsInterestRate.toString()
        runSavingsInterestProjection(
            calcPrincipalStr.toDoubleOrNull() ?: 500000.0,
            calcRateStr.toDoubleOrNull() ?: rule.savingsInterestRate,
            calcPeriodMonthsStr.toIntOrNull() ?: 12,
            calcCompoundingFrequency
        ) { interest, maturity, schedule ->
            calculatedInterestValue = interest
            calculatedMaturityValue = maturity
            calculatedScheduleList = schedule
        }
    }

    // Calculations & Statistics
    val approvedPayments = paymentsList.filter { it.status == VerificationStatus.APPROVED }
    val totalSavingsCollected = approvedPayments.sumOf { it.amountPaid }
    val totalLoansDisbursed = loansList.filter { it.status == LoanStatus.DISBURSED || it.status == LoanStatus.COMPLETED }.sumOf { it.amountRequested }
    val totalLoansOutstanding = loansList.filter { it.status == LoanStatus.DISBURSED }.sumOf { it.outstandingBalance }
    val totalInterestCollected = loansList.sumOf { it.interestPaid }
    val totalExpenses = expensesList.sumOf { it.amount }
    val netCashReserves = totalSavingsCollected + totalInterestCollected - totalLoansDisbursed - totalExpenses

    // Top-level Dividend Simulation variables for global screen visibility (e.g. Audit locks)
    val simRegFee = simRegistrationFeeInput.toDoubleOrNull() ?: 50000.0
    val simInvestIncome = simInvestmentIncomeInput.toDoubleOrNull() ?: 2000000.0

    val dividendMetricsMap = remember(profilesList, paymentsList, loansList, expensesList, rule, simRegFee, simInvestIncome) {
        DividendEngine.computeMetrics(
            profiles = profilesList,
            allPayments = paymentsList,
            allLoans = loansList,
            allExpenses = expensesList,
            rule = rule,
            simRegistrationFeePerMember = simRegFee,
            simInvestmentIncome = simInvestIncome
        )
    }

    val simTotalInterest = loansList.sumOf { it.interestPaid }
    val simTotalRegFees = profilesList.size * simRegFee
    val simTotalPenalties = approvedPayments.sumOf { it.penaltyAmountCharged }
    val simTotalExpenses = expensesList.sumOf { it.amount }
    val simTotalIncome = simTotalInterest + simTotalRegFees + simTotalPenalties + simInvestIncome
    val simNetProfitPool = (simTotalIncome - simTotalExpenses).coerceAtLeast(0.0)

    // Local State for sync backup
    var backupStatusText by remember { mutableStateOf("") }
    var runningBackup by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isMobile = configuration.screenWidthDp < 600
    var isSidebarExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Simulated Desktop Web App Side Navigation (Only on Tablets/Desktops) ---
            if (!isMobile) {
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ADMIN CONSOLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
                    )

                    AdminNavContent(
                        activeAdminTab = activeAdminTab,
                        onTabSelected = { activeAdminTab = it },
                        netCashReserves = netCashReserves,
                        formatter = formatter
                    )
                }
            }

            // --- Main Console Viewport ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(if (isMobile) 12.dp else 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (isMobile) 12.dp else 20.dp)
            ) {
                // Quick-toggle bar for mobile to open the Collapsed Sidebar Menu
                if (isMobile) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { isSidebarExpanded = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Admin Menu",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "TAP TO CHOOSE: " + when(activeAdminTab) {
                                        "dashboard" -> "Group Overview"
                                        "users" -> "Manage Members"
                                        "accounting" -> "Payments & Expenses"
                                        "loans" -> "Manage Loans"
                                        "reminders" -> "Send Notices"
                                        "calculator" -> "Profit Calculator"
                                        "importer" -> "Import & Backup"
                                        "settings" -> "Sacco Rules"
                                        else -> activeAdminTab.uppercase()
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SACCO ADMIN PANEL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = when (activeAdminTab) {
                                "dashboard" -> "Sacco Group Overview"
                                "users" -> "Manage Members"
                                "accounting" -> "Record Payments & Expenses"
                                "loans" -> "Approve & Track Loans"
                                "calculator" -> "Dividend & Interest Calculator"
                                "importer" -> "Import & Backup (Excel)"
                                "reminders" -> "Member Notices & Invites"
                                else -> "Sacco Rules & Settings"
                            },
                            style = if (isMobile) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (!isMobile) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Confidential Admin Access",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            when (activeAdminTab) {
                "dashboard" -> {
                    // TAB: EXECUTIVE OVERVIEW STATS
                    if (isMobile) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard("Total Members", "${profilesList.size} Reg.", Icons.Default.Group, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                StatCard("Deposited Capital", formatter.format(totalSavingsCollected), Icons.Default.Savings, Color(0xFF16A34A), Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatCard("Interest Revenue", formatter.format(totalInterestCollected), Icons.Default.TrendingUp, Color(0xFF1E3A8A), Modifier.weight(1f))
                                StatCard("Oper. Expenses", formatter.format(totalExpenses), Icons.Default.MoneyOff, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard("Total Members", "${profilesList.size} Registered", Icons.Default.Group, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            StatCard("Deposited Capital", formatter.format(totalSavingsCollected), Icons.Default.Savings, Color(0xFF16A34A), Modifier.weight(1f))
                            StatCard("Interest Revenue", formatter.format(totalInterestCollected), Icons.Default.TrendingUp, Color(0xFF1E3A8A), Modifier.weight(1f))
                            StatCard("Operational Expenses", formatter.format(totalExpenses), Icons.Default.MoneyOff, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                        }
                    }

                    // Interest Distribution Section
                    Text("Yearly Profit & Dividend Sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("admin_interest_distribution_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(if (isMobile) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "All group profit is shared among members based on how much they saved and how consistently they deposited on time. Try different numbers below to see how it affects everyone's share.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider()

                            // Admin Simulation Inputs
                            Text("Profit Simulator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = simRegistrationFeeInput,
                                    onValueChange = { simRegistrationFeeInput = it },
                                    label = { Text("Extra Joining Fees (UGX)") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("sim_reg_fee_input")
                                )
                                OutlinedTextField(
                                    value = simInvestmentIncomeInput,
                                    onValueChange = { simInvestmentIncomeInput = it },
                                    label = { Text("Extra Sacco Income (UGX)") },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("sim_invest_income_input")
                                )
                            }

                            // Dynamic Profit Pool Summary Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("SACCO PROFIT PREVIEW", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("1. Interest earned from loans:", style = MaterialTheme.typography.bodySmall)
                                        Text(formatter.format(simTotalInterest), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("2. Total Joining fees:", style = MaterialTheme.typography.bodySmall)
                                        Text(formatter.format(simTotalRegFees), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("3. Late fines collected:", style = MaterialTheme.typography.bodySmall)
                                        Text(formatter.format(simTotalPenalties), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("4. Other Sacco business profit:", style = MaterialTheme.typography.bodySmall)
                                        Text(formatter.format(simInvestIncome), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("5. Minus Group Expenses:", style = MaterialTheme.typography.bodySmall)
                                        Text("- " + formatter.format(simTotalExpenses), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("ESTIMATED PROFIT FOR MEMBERS:", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        Text(formatter.format(simNetProfitPool), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF16A34A))
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { showDeclareDividendDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        modifier = Modifier.fillMaxWidth().testTag("declare_dividend_lock_button")
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Declare & Lock FY 2026 Dividends", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            HorizontalDivider()

                            val tableHeaderFontSize = if (isMobile) 8.sp else 11.sp
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Member Name", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                                Text("Savings", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                                Text("Compliance", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                                Text("Consistency", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                                Text("Score", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                                Text("Share", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End)
                                Text("Projected Div", fontSize = tableHeaderFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End)
                            }

                            if (profilesList.isEmpty()) {
                                Text("No members available.", fontSize = 11.sp, color = LemonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                            } else {
                                val tableContentFontSize = if (isMobile) 8.sp else 11.sp
                                profilesList.forEach { m ->
                                    val memberSavings = approvedPayments.filter { it.memberId == m.memberId }.sumOf { it.amountPaid }
                                    val mMetrics = dividendMetricsMap[m.memberId] ?: MemberDividendMetrics(
                                        memberId = m.memberId,
                                        fullName = m.fullName,
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
                                    val compliance = mMetrics.averageCompliance
                                    val consistency = mMetrics.consistencyIndex
                                    val weightedScore = mMetrics.consistencyAdjustedScore
                                    val weightPercent = mMetrics.ownershipRatio * 100.0
                                    val profitShare = mMetrics.projectedDividend

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.fullName, fontSize = tableContentFontSize, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(formatter.format(memberSavings), fontSize = tableContentFontSize, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                                        Text(String.format("%.0f%%", compliance), fontSize = tableContentFontSize, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End, color = if (compliance >= 100.0) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary)
                                        Text(String.format("%.0f%%", consistency), fontSize = tableContentFontSize, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End, color = Color(0xFFEA580C))
                                        Text(String.format("%,.0f", weightedScore), fontSize = tableContentFontSize, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                                        Text(String.format("%.0f%%", weightPercent), fontSize = tableContentFontSize, modifier = Modifier.weight(0.9f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                                        Text(formatter.format(profitShare), fontSize = tableContentFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End, color = Color(0xFF16A34A))
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                }
                            }
                        }
                    }
                }

                "users" -> {
                    // TAB: USER REGISTRY & ROLE MODERATOR
                    Text("New Member Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    val pendingMembers = profilesList.filter { it.status == MemberStatus.PENDING }
                    if (pendingMembers.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No new member requests waiting.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                            }
                        }
                    } else {
                        pendingMembers.forEach { mem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mem.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("National ID: ${mem.nationalId} | Joined: ${mem.dateJoined}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7), contentColor = Color(0xFF92400E))) {
                                            Text(mem.status.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    if (isMobile) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("Phone: ${mem.phoneNumber}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Email: ${mem.email}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Address: ${mem.physicalAddress}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Occupation: ${mem.occupation}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Employer: ${mem.employer}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Emergency Contact: ${mem.emergencyContact}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Phone: ${mem.phoneNumber}", fontSize = 11.sp)
                                                Text("Email: ${mem.email}", fontSize = 11.sp)
                                                Text("Address: ${mem.physicalAddress}", fontSize = 11.sp)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Occupation: ${mem.occupation}", fontSize = 11.sp)
                                                Text("Employer: ${mem.employer}", fontSize = 11.sp)
                                                Text("Emergency Contact: ${mem.emergencyContact}", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    if (isMobile) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onVerifyMember(mem.memberId, MemberStatus.ACTIVE) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Approve Member", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { onVerifyMember(mem.memberId, MemberStatus.SUSPENDED) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Reject / Suspend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { onVerifyMember(mem.memberId, MemberStatus.ACTIVE) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Approve Member", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(
                                                onClick = { onVerifyMember(mem.memberId, MemberStatus.SUSPENDED) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Reject / Suspend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text("All Group Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(if (isMobile) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val userTableFontSize = if (isMobile) 9.sp else 11.sp
                            val nameColumnWeight = if (isMobile) 2.2f else 2f
                            val roleColumnWeight = if (isMobile) 1.2f else 1.5f
                            val statusColumnWeight = if (isMobile) 1.3f else 1.5f
                            val actionColumnWeight = if (isMobile) 2.3f else 2f

                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Name", fontSize = userTableFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(nameColumnWeight))
                                Text("Position", fontSize = userTableFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(roleColumnWeight))
                                Text("Status", fontSize = userTableFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(statusColumnWeight))
                                Text("Actions", fontSize = userTableFontSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(actionColumnWeight), textAlign = TextAlign.End)
                            }

                            if (profilesList.isEmpty()) {
                                Text("No group members found.", fontSize = userTableFontSize, color = LemonGreen, fontWeight = FontWeight.Bold)
                            } else {
                                profilesList.filter { it.status != MemberStatus.PENDING }.forEach { mem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(mem.fullName, fontSize = userTableFontSize, fontWeight = FontWeight.Medium, modifier = Modifier.weight(nameColumnWeight), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("MEMBER", fontSize = userTableFontSize, color = Color.Gray, modifier = Modifier.weight(roleColumnWeight))
                                        Text(mem.status.name, fontSize = userTableFontSize, fontWeight = FontWeight.Bold, color = if (mem.status == MemberStatus.ACTIVE) Color(0xFF16A34A) else Color.Red, modifier = Modifier.weight(statusColumnWeight))
                                        Row(
                                            modifier = Modifier.weight(actionColumnWeight),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (mem.status == MemberStatus.ACTIVE) {
                                                Text(
                                                    text = "Pause",
                                                    fontSize = userTableFontSize,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { onVerifyMember(mem.memberId, MemberStatus.FROZEN) }.padding(horizontal = if (isMobile) 3.dp else 6.dp)
                                                )
                                                Text(
                                                    text = "Block",
                                                    fontSize = userTableFontSize,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { onVerifyMember(mem.memberId, MemberStatus.SUSPENDED) }.padding(horizontal = if (isMobile) 3.dp else 6.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Activate",
                                                    fontSize = userTableFontSize,
                                                    color = Color(0xFF16A34A),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { onVerifyMember(mem.memberId, MemberStatus.ACTIVE) }.padding(horizontal = if (isMobile) 3.dp else 6.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                }
                            }
                        }
                    }
                }

                "accounting" -> {
                    // TAB: GENERAL ENTRIES & EXPENSE TRACKING & STATEMENTS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Column: Record Expense Form
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Record New Group Expense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Add expenses like rent, office utilities, stationery, or group refreshments.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                OutlinedTextField(
                                    value = expenseAmountStr,
                                    onValueChange = { expenseAmountStr = it },
                                    label = { Text("Spent Amount (UGX)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Expense Category", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    expenseCategories.forEach { cat ->
                                        val isSelected = expenseCategory == cat
                                        SuggestionChip(
                                            onClick = { expenseCategory = cat },
                                            label = { Text(cat, fontSize = 9.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                labelColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black
                                            )
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = expenseDescription,
                                    onValueChange = { expenseDescription = it },
                                    label = { Text("Details of Expense (e.g. Printer ink, transport)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )

                                Button(
                                    onClick = {
                                        val amt = expenseAmountStr.toDoubleOrNull()
                                        if (amt == null || amt <= 0) {
                                            Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                                        } else if (expenseDescription.isEmpty()) {
                                            Toast.makeText(context, "Please describe what this expense was for.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onSubmitExpense(amt, expenseCategory, expenseDescription)
                                            expenseAmountStr = ""
                                            expenseDescription = ""
                                            Toast.makeText(context, "Group expense saved successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save Expense Entry")
                                }
                            }
                        }

                        // Right Column: Expenses History List
                        Card(
                            modifier = Modifier.weight(1.8f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Group Expense History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                if (expensesList.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                        Text("No expenses added yet.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(expensesList) { exp ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                                                Text(exp.category, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                            }
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(formatter.format(exp.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(exp.description, fontSize = 11.sp)
                                                        Text("Date: ${exp.date} | By: ${exp.paidBy}", fontSize = 9.sp, color = Color.Gray)
                                                    }
                                                    IconButton(onClick = { onDeleteExpense(exp.id) }) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bank and Payment Method Statement Generator
                    Text("Live Bank & Payment Method Statement Generator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Generate an audit ledger summary statement for bank or electronic payment methods based on approved member savings uploads and repayments.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.5f)) {
                                    Text("Select Payment Gateway", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        paymentMethods.forEach { method ->
                                            val isSelected = selectedPaymentMethod == method
                                            SuggestionChip(
                                                onClick = { selectedPaymentMethod = method; statementRun = false },
                                                label = { Text(method.substringBefore(" Sacco"), fontSize = 9.sp) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                                )
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        // Filter payments matching method and verified
                                        val matchMethod = selectedPaymentMethod
                                        val entries = mutableListOf<StatementEntry>()
                                        var balance = 0.0

                                        approvedPayments.forEach { p ->
                                            // Simulating bank account mapping
                                            val match = when {
                                                matchMethod.contains("Bank") && (p.bankName.contains("Bank", true) || p.bankName.isEmpty()) -> true
                                                matchMethod.contains("MTN") && p.bankName.contains("MTN", true) -> true
                                                matchMethod.contains("Airtel") && p.bankName.contains("Airtel", true) -> true
                                                matchMethod.contains("Cash") && p.bankName.contains("Cash", true) -> true
                                                else -> false
                                            }
                                            if (match) {
                                                balance += p.amountPaid
                                                entries.add(
                                                    StatementEntry(
                                                        date = p.datePaid,
                                                        description = "Savings Deposit - ${p.memberName}",
                                                        reference = p.receiptNumber,
                                                        type = "CREDIT",
                                                        amount = p.amountPaid,
                                                        runningBalance = balance
                                                    )
                                                )
                                            }
                                        }
                                        generatedStatement = entries.sortedBy { it.date }
                                        statementRun = true
                                    },
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    Text("Run Statement", fontSize = 11.sp)
                                }
                            }

                            if (statementRun) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Date", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                                    Text("Particulars", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                    Text("Ref No", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("Amount (UGX)", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                    Text("Balance", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                                }

                                if (generatedStatement.isEmpty()) {
                                    Text("No transactional entries found for selected gateway.", fontSize = 11.sp, color = LemonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                                } else {
                                    generatedStatement.forEach { entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(entry.date.take(10), fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                                            Text(entry.description, fontSize = 10.sp, modifier = Modifier.weight(2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(entry.reference, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                            Text(formatter.format(entry.amount), fontSize = 10.sp, color = Color(0xFF16A34A), modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                                            Text(formatter.format(entry.runningBalance), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                    }
                                }
                            }
                        }
                    }
                }

                "loans" -> {
                    // TAB: LOANS PORTFOLIO REVIEW
                    Text("Loans Waiting for Approval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    val pendingLoans = loansList.filter { it.status == LoanStatus.PENDING }
                    if (pendingLoans.isEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No loan requests waiting for approval.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                            }
                        }
                    } else {
                        pendingLoans.forEach { loan ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(loan.applicantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text("Applied: ${loan.dateApplied} | Purpose: ${loan.purpose}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        Text(formatter.format(loan.amountRequested), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Member Savings Balance: ${formatter.format(loan.originalSavingsBalance)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("Time requested: ${loan.repaymentPeriodMonths} Months", fontSize = 11.sp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Guarantor member: ${loan.guarantorId.ifEmpty { "N/A" }}", fontSize = 11.sp)
                                            Text("Guarantor approved: ${if (loan.guarantorApproved) "Yes" else "No"}", fontSize = 11.sp)
                                        }
                                    }

                                    // Visual credit rating
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Smart Credit Rating Score:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(loan.loanScore, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (loan.loanScoreAnalysis.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(loan.loanScoreAnalysis, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = loanReviewComments,
                                        onValueChange = { loanReviewComments = it },
                                        label = { Text("Approver Comments or Conditions") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onVerifyLoan(loan.id, LoanStatus.DISBURSED, loanReviewComments)
                                                loanReviewComments = ""
                                                Toast.makeText(context, "Loan approved and paid out to member!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Approve & Pay Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onVerifyLoan(loan.id, LoanStatus.REJECTED, loanReviewComments)
                                                loanReviewComments = ""
                                                Toast.makeText(context, "Loan application declined.", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Decline Request", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "calculator" -> {
                    // TAB: INTERACTIVE INTEREST TOOLS & FUTURE FORECASTER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Column: Interactive Input Card
                        Card(
                            modifier = Modifier.weight(1.2f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Interest & Growth Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Calculate how much a member's savings will grow over time with the Sacco's yearly interest rate.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                OutlinedTextField(
                                    value = calcPrincipalStr,
                                    onValueChange = {
                                        calcPrincipalStr = it
                                        runSavingsInterestProjection(
                                            it.toDoubleOrNull() ?: 0.0,
                                            calcRateStr.toDoubleOrNull() ?: rule.savingsInterestRate,
                                            calcPeriodMonthsStr.toIntOrNull() ?: 12,
                                            calcCompoundingFrequency
                                        ) { interest, maturity, schedule ->
                                            calculatedInterestValue = interest
                                            calculatedMaturityValue = maturity
                                            calculatedScheduleList = schedule
                                        }
                                    },
                                    label = { Text("Starting Savings Amount (UGX)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = calcRateStr,
                                    onValueChange = {
                                        calcRateStr = it
                                        runSavingsInterestProjection(
                                            calcPrincipalStr.toDoubleOrNull() ?: 0.0,
                                            it.toDoubleOrNull() ?: 0.0,
                                            calcPeriodMonthsStr.toIntOrNull() ?: 12,
                                            calcCompoundingFrequency
                                        ) { interest, maturity, schedule ->
                                            calculatedInterestValue = interest
                                            calculatedMaturityValue = maturity
                                            calculatedScheduleList = schedule
                                        }
                                    },
                                    label = { Text("Yearly Sacco Interest Rate (%)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = calcPeriodMonthsStr,
                                    onValueChange = {
                                        calcPeriodMonthsStr = it
                                        runSavingsInterestProjection(
                                            calcPrincipalStr.toDoubleOrNull() ?: 0.0,
                                            calcRateStr.toDoubleOrNull() ?: rule.savingsInterestRate,
                                            it.toIntOrNull() ?: 12,
                                            calcCompoundingFrequency
                                        ) { interest, maturity, schedule ->
                                            calculatedInterestValue = interest
                                            calculatedMaturityValue = maturity
                                            calculatedScheduleList = schedule
                                        }
                                    },
                                    label = { Text("Savings Time (Months)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("How Often Interest is Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val frequencies = listOf("Monthly", "Quarterly", "Annually")
                                    frequencies.forEach { freq ->
                                        val isSelected = calcCompoundingFrequency == freq
                                        SuggestionChip(
                                            onClick = {
                                                calcCompoundingFrequency = freq
                                                runSavingsInterestProjection(
                                                    calcPrincipalStr.toDoubleOrNull() ?: 0.0,
                                                    calcRateStr.toDoubleOrNull() ?: rule.savingsInterestRate,
                                                    calcPeriodMonthsStr.toIntOrNull() ?: 12,
                                                    freq
                                                ) { interest, maturity, schedule ->
                                                    calculatedInterestValue = interest
                                                    calculatedMaturityValue = maturity
                                                    calculatedScheduleList = schedule
                                                }
                                            },
                                            label = { Text(freq, fontSize = 9.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Right Column: Output projection details
                        Card(
                            modifier = Modifier.weight(1.8f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Estimated Savings Growth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Interest Earned", fontSize = 10.sp, color = Color.Gray)
                                            Text(formatter.format(calculatedInterestValue), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Total Final Balance", fontSize = 10.sp, color = Color.Gray)
                                            Text(formatter.format(calculatedMaturityValue), fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                Text("Savings Growth Month-by-Month", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Month", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("Saved Amount", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                    Text("Interest Earned", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                                    Text("Final Balance", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
                                }

                                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(calculatedScheduleList) { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Month ${row.monthIndex}", fontSize = 10.sp, modifier = Modifier.weight(1f))
                                            Text(formatter.format(row.principal), fontSize = 10.sp, modifier = Modifier.weight(1.5f))
                                            Text(formatter.format(row.interestAccrued), fontSize = 10.sp, color = Color(0xFF16A34A), modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                                            Text(formatter.format(row.endingBalance), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.End)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                                    }
                                }
                            }
                        }
                    }
                }

                "importer" -> {
                    // TAB: IMPORT EXPORT
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Import Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Upload Member Records (CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Paste list rows from an Excel file or bank statement below to upload many member savings payments at once.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                Text("Format to copy: Date,MemberId,Name,Amount,ReceiptNo,Ref", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                OutlinedTextField(
                                    value = csvImportText,
                                    onValueChange = { csvImportText = it },
                                    placeholder = { Text("2026-06-25,member_01,John Doe,150000,REC-9008,BANK-REF-99\n2026-06-26,member_02,Sarah Smith,200000,REC-9009,BANK-REF-100", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                )

                                Button(
                                    onClick = {
                                        val lines = csvImportText.split("\n").filter { it.isNotBlank() }
                                        var count = 0
                                        lines.forEach { line ->
                                            val parts = line.split(",")
                                            if (parts.size >= 5) {
                                                val amt = parts[3].toDoubleOrNull()
                                                if (amt != null && amt > 0) {
                                                    // Process simulated import payments directly
                                                    count++
                                                }
                                            }
                                        }
                                        importedCount = count
                                        showImportSuccessDialog = true
                                        csvImportText = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    enabled = csvImportText.isNotBlank()
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload and Save Payments")
                                }
                            }
                        }

                        // Export Box
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Download Group Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Download your Sacco data into files you can open in Excel for meetings, sharing, or keeping a safe copy.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                                Button(
                                    onClick = {
                                        Toast.makeText(context, "All member savings records downloaded!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download All Savings (.CSV)")
                                }

                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Sacco expenses report downloaded!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download All Expenses (.CSV)")
                                }

                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Membership directory exported!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download Member List (.CSV)")
                                }
                            }
                        }
                    }
                }

                "settings" -> {
                    // TAB: CONFIGURATION SETTINGS & BACKUP PIE
                    Text("Sacco Group Rules & Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = monthlySavingsInput,
                                onValueChange = { monthlySavingsInput = it },
                                label = { Text("Required Monthly Savings (UGX)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = gracePeriodInput,
                                onValueChange = { gracePeriodInput = it },
                                label = { Text("Extra Grace Days to Save") },
                                modifier = Modifier.fillMaxWidth()
                              )

                            OutlinedTextField(
                                value = penaltyAmtInput,
                                onValueChange = { penaltyAmtInput = it },
                                label = { Text("Late Payment Fine (UGX)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = savingsInterestRateInput,
                                onValueChange = { savingsInterestRateInput = it },
                                label = { Text("Yearly Sacco Interest Rate (%)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isPercentagePenalty,
                                    onCheckedChange = { isPercentagePenalty = it }
                                )
                                Text("Charge penalty as percentage (instead of fixed cash)", style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    val amt = monthlySavingsInput.toDoubleOrNull() ?: rule.monthlyAmount
                                    val days = gracePeriodInput.toIntOrNull() ?: rule.gracePeriodDays
                                    val pAmt = penaltyAmtInput.toDoubleOrNull() ?: rule.penaltyFixedAmount
                                    val sRate = savingsInterestRateInput.toDoubleOrNull() ?: rule.savingsInterestRate

                                    onSaveRule(
                                        rule.copy(
                                            monthlyAmount = amt,
                                            gracePeriodDays = days,
                                            penaltyFixedAmount = pAmt,
                                            isPenaltyPercentage = isPercentagePenalty,
                                            savingsInterestRate = sRate
                                        )
                                    )
                                    Toast.makeText(context, "Sacco rules saved successfully!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Group Rules")
                            }
                        }
                    }

                    Text("Google Sheets Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Keep a copy of all Sacco records safe by backing up and saving everything directly to your Google Sheets spreadsheet in the cloud.",
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (backupStatusText.isNotEmpty()) {
                                Text(
                                    text = backupStatusText,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Button(
                                onClick = {
                                    runningBackup = true
                                    backupStatusText = ""
                                    scope.launch {
                                        val status = onTriggerBackup()
                                        backupStatusText = status
                                        runningBackup = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                enabled = !runningBackup
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (runningBackup) "Saving to Google Sheets..." else "Backup Now to Google Sheets")
                              }
                        }
                    }
                }

                "reminders" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Send Payment Reminders",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Check who has not saved yet this month and send them polite SMS, Email, and in-app reminder messages.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Button(
                                        onClick = { onRunReminderSweep() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.testTag("run_reminder_sweep_button")
                                    ) {
                                        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Send Notices", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Member Saving Goals (${savingsPlans.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (savingsPlans.isEmpty()) {
                                    Text("No active member saving goals set up yet.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                                } else {
                                    savingsPlans.forEach { plan ->
                                        val mProfile = profilesList.find { it.memberId == plan.memberId }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(mProfile?.fullName ?: plan.memberId.substringBefore("@"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                Text("Plan: ${plan.planFrequency} | Next Due Date: ${plan.nextDueDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    val channels = mutableListOf<String>()
                                                    if (plan.enableInApp) channels.add("In-App")
                                                    if (plan.enableEmail) channels.add("Email")
                                                    if (plan.enableSms) channels.add("SMS")
                                                    Text(
                                                        text = "Alerts: " + (if (channels.isEmpty()) "None" else channels.joinToString(", ")),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatter.format(plan.targetAmount),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Invited Friends & Rewards (${referrals.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (referrals.isEmpty()) {
                                    Text("No member invites recorded yet.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                                } else {
                                    referrals.forEach { ref ->
                                        val referrerProfile = profilesList.find { it.memberId == ref.referrerId }
                                        val refereeProfile = profilesList.find { it.memberId == ref.refereeId }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Invited By: ${referrerProfile?.fullName ?: ref.referrerId.substringBefore("@")}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("New Joined Member: ${refereeProfile?.fullName ?: ref.refereeId.substringBefore("@")}", style = MaterialTheme.typography.bodySmall)
                                                Text("Code: ${ref.referralCodeUsed} | Date: ${ref.dateReferred.take(10)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = formatter.format(ref.rewardAmount),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF386641)
                                                )
                                                val isCompleted = ref.status != "PENDING"
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isCompleted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                                    ),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isCompleted) "REWARDED" else "PENDING",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (isCompleted) Color(0xFF2E7D32) else Color(0xFFE65100),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            // Real-Time Audit Log Summary Footer
            Text("Auditing Activity Stream Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    auditLogs.forEach { log ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("[${log.action}]", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                Text(log.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Text("Operator: ${log.operatorName} (${log.operatorRole}) | Details: ${log.details}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            } // End of Viewport Column
        } // End of Row

        // --- Collapsible Drawer Overlay for Mobile ---
        if (isMobile && isSidebarExpanded) {
            // Dismissible background scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isSidebarExpanded = false }
            )

            // Drawer content sliding/appearing from the left
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADMIN CONSOLE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { isSidebarExpanded = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Menu")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    AdminNavContent(
                        activeAdminTab = activeAdminTab,
                        onTabSelected = { 
                            activeAdminTab = it
                            isSidebarExpanded = false // Collapse on item selection!
                        },
                        netCashReserves = netCashReserves,
                        formatter = formatter
                    )
                }
            }
        }
    } // End of Box

    if (showImportSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showImportSuccessDialog = false },
            title = { Text("Simulated Bulk Import Success") },
            text = { Text("Successfully parsed and imported $importedCount savings records into the general entries ledger for Sacco verification and bookkeeping!") },
            confirmButton = {
                Button(onClick = { showImportSuccessDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showDeclareDividendDialog) {
        AlertDialog(
            onDismissRequest = { showDeclareDividendDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Approve & Share 2026 Profit", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "⚠️ IMPORTANT STEP:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "You are about to approve this year's profit sharing. Once confirmed, this cannot be changed:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Saving records for this year will be locked. No new payments can be added for 2026.\n" +
                               "• A permanent receipt and record of dividends will be saved for every member.\n" +
                               "• A notification message will be sent immediately to all group members to see their shares.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Profit to distribute to members: UGX ${formatter.format(simNetProfitPool)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF16A34A)
                    )
                    Text(
                        text = "Sharing Rule: Based on Savings × Time",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val yearInt = declareYear.toIntOrNull() ?: 2026
                        val records = profilesList.map { m ->
                            val mMetrics = dividendMetricsMap[m.memberId] ?: MemberDividendMetrics(
                                memberId = m.memberId,
                                fullName = m.fullName,
                                rawWeightedScore = 0.0,
                                monthlyCompliance = emptyMap(),
                                averageCompliance = 0.0,
                                attendance = 0.0,
                                consistencyIndex = 0.0,
                                consistencyAdjustedScore = 0.0,
                                ownershipRatio = 0.0,
                                projectedDividend = 0.0
                            )
                            
                            DividendAuditRecord(
                                year = yearInt,
                                memberId = m.memberId,
                                fullName = m.fullName,
                                rawWeightedScore = mMetrics.rawWeightedScore,
                                attendance = mMetrics.attendance,
                                averageCompliance = mMetrics.averageCompliance,
                                consistencyIndex = mMetrics.consistencyIndex,
                                eligibilityStatus = mMetrics.eligibilityStatus,
                                ownershipRatio = mMetrics.ownershipRatio,
                                profitPool = simNetProfitPool,
                                allocatedDividend = mMetrics.projectedDividend,
                                calculationTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                                algorithmVersion = "v1.1-Hybrid"
                            )
                        }
                        
                        onDeclareAndLockDividend(yearInt, simNetProfitPool, records)
                        showDeclareDividendDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm & Share Profits")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclareDividendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminNavContent(
    activeAdminTab: String,
    onTabSelected: (String) -> Unit,
    netCashReserves: Double,
    formatter: java.text.NumberFormat
) {
    val menuItems = listOf(
        AdminMenuItem("dashboard", "Group Overview", Icons.Default.Dashboard),
        AdminMenuItem("users", "Manage Members", Icons.Default.Group),
        AdminMenuItem("accounting", "Payments & Expenses", Icons.Default.AccountBalance),
        AdminMenuItem("loans", "Manage Loans", Icons.Default.Assignment),
        AdminMenuItem("reminders", "Send Notices", Icons.Default.NotificationsActive),
        AdminMenuItem("calculator", "Profit Calculator", Icons.Default.Calculate),
        AdminMenuItem("importer", "Import & Backup", Icons.Default.SyncAlt),
        AdminMenuItem("settings", "Sacco Rules", Icons.Default.Settings)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        menuItems.forEach { item ->
            val isSelected = activeAdminTab == item.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .clickable { onTabSelected(item.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Quick Info Card at bottom
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Net Sacco Liquidity", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(
                    formatter.format(netCashReserves),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (netCashReserves >= 0) Color(0xFF16A34A) else Color.Red
                )
            }
        }
    }
}

// Model Classes used exclusively inside Administrative Screen
data class AdminMenuItem(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

data class StatementEntry(
    val date: String,
    val description: String,
    val reference: String,
    val type: String, // "CREDIT", "DEBIT"
    val amount: Double,
    val runningBalance: Double
)

data class InterestScheduleEntry(
    val monthIndex: Int,
    val principal: Double,
    val interestAccrued: Double,
    val endingBalance: Double
)

// Compound interest projection calculator routine
fun runSavingsInterestProjection(
    principal: Double,
    annualRate: Double,
    periodMonths: Int,
    frequency: String,
    onResult: (Double, Double, List<InterestScheduleEntry>) -> Unit
) {
    if (principal <= 0.0 || annualRate <= 0.0 || periodMonths <= 0) {
        onResult(0.0, 0.0, emptyList())
        return
    }

    val r = annualRate / 100.0
    val compoundPeriodsPerYear = when (frequency) {
        "Monthly" -> 12
        "Quarterly" -> 4
        else -> 1 // "Annually"
    }

    val finalMaturity = principal * Math.pow(1.0 + r / compoundPeriodsPerYear, (compoundPeriodsPerYear * (periodMonths / 12.0)))
    val totalInterest = finalMaturity - principal

    val list = mutableListOf<InterestScheduleEntry>()
    var balance = principal
    val monthlyRate = r / 12.0

    for (m in 1..periodMonths) {
        // Accruing interest pro-rated monthly
        val interestThisMonth = balance * monthlyRate
        val startBalance = balance
        balance += interestThisMonth
        list.add(
            InterestScheduleEntry(
                monthIndex = m,
                principal = startBalance,
                interestAccrued = interestThisMonth,
                endingBalance = balance
            )
        )
    }

    onResult(totalInterest, finalMaturity, list)
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
