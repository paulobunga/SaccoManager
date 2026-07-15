package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.LemonGreen
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsTimelineScreen(
    memberId: String,
    memberName: String,
    rule: SavingsRule,
    payments: List<SavingsPayment>,
    allPayments: List<SavingsPayment> = emptyList(),
    profilesList: List<MemberProfile> = emptyList(),
    allLoans: List<LoanApplication> = emptyList(),
    allExpenses: List<SaccoExpense> = emptyList(),
    onSubmitPayment: (Double, Int, String, String, String, String, String, String) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    // Upload Form State
    val currentCalendar = remember { java.util.Calendar.getInstance() }
    val sysCurrentMonthIndex = remember { currentCalendar.get(java.util.Calendar.MONTH) + 1 }
    val sysCurrentYear = remember { currentCalendar.get(java.util.Calendar.YEAR) }

    var amountStr by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("Stanbic Bank") }
    var branchName by remember { mutableStateOf("Main Branch") }
    var receiptNo by remember { mutableStateOf("") }
    var txId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMonthIndex by remember { mutableStateOf(sysCurrentMonthIndex) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }

    // Attachment States
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isImageSelected by remember { mutableStateOf(false) }
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Interactive Savings Interest Calculator State
    var showCalcTools by remember { mutableStateOf(false) }
    var calcPrincipalStr by remember { mutableStateOf("500000") }
    var calcPeriodMonthsStr by remember { mutableStateOf("12") }
    var calcCompoundingFreq by remember { mutableStateOf("Monthly") }
    var computedInterestValue by remember { mutableStateOf(0.0) }
    var computedMaturityValue by remember { mutableStateOf(0.0) }

    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            cameraBitmap = null
            selectedFileName = getFileName(context, it)
            val mimeType = context.contentResolver.getType(it)
            isImageSelected = mimeType?.startsWith("image/") == true
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            cameraBitmap = bitmap
            selectedFileUri = null
            selectedFileName = "Camera_Capture_${System.currentTimeMillis()}.jpg"
            isImageSelected = true
        }
    }

    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Calculate Member's Actual Savings Interest Earned Pro-rata
    // Interest = amountPaid * (interestRate/100) * ((12 - cycleMonthIndex + 1) / 12)
    val approvedPayments = payments.filter { it.status == VerificationStatus.APPROVED }
    val totalApprovedSavings = approvedPayments.sumOf { it.amountPaid }
    
    val totalAccruedInterest = approvedPayments.sumOf { p ->
        val monthsSavedInYear = (12 - p.cycleMonthIndex + 1).coerceIn(1, 12)
        p.amountPaid * (rule.savingsInterestRate / 100.0) * (monthsSavedInYear / 12.0)
    }

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
        fullName = "Member",
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

    val allSortedMetrics = dividendMetricsMap.values.sortedByDescending { it.consistencyAdjustedScore }
    val myRankIndex = allSortedMetrics.indexOfFirst { it.memberId == memberId }
    val myRank = if (myRankIndex != -1) myRankIndex + 1 else 1
    val totalRankedMembers = allSortedMetrics.size.coerceAtLeast(1)

    // Current time is 2026-07-06
    val firstUnsavedMonthIndex = (1..12).find { m -> (myMetrics.monthlyCompliance[m] ?: 0.0) < 100.0 } ?: 7
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val upcomingMonthName = monthNames.getOrNull(firstUnsavedMonthIndex - 1) ?: "next month"
    val daysInMonth = when (firstUnsavedMonthIndex) {
        2 -> "February 28"
        4, 6, 9, 11 -> "${monthNames[firstUnsavedMonthIndex - 1]} 30"
        else -> "${monthNames[firstUnsavedMonthIndex - 1]} 31"
    }

    fun getActualMonthIndex(datePaid: String, cycleMonthIndex: Int): Int {
        return DividendEngine.getActualMonthIndex(datePaid, cycleMonthIndex)
    }

    val monthlyRequiredAmount = rule.monthlyAmount.coerceAtLeast(1.0)
    val memberCompliancePerMonth = myMetrics.monthlyCompliance

    var showTimeWeightedBreakdown by remember { mutableStateOf(false) }

    // Run first calculation on projection
    LaunchedEffect(rule.savingsInterestRate, calcPrincipalStr, calcPeriodMonthsStr, calcCompoundingFreq) {
        val p = calcPrincipalStr.toDoubleOrNull() ?: 500000.0
        val r = rule.savingsInterestRate / 100.0
        val t = (calcPeriodMonthsStr.toIntOrNull() ?: 12) / 12.0
        val n = when (calcCompoundingFreq) {
            "Monthly" -> 12
            "Quarterly" -> 4
            else -> 1
        }
        computedMaturityValue = p * Math.pow(1.0 + r / n, n * t)
        computedInterestValue = computedMaturityValue - p
    }

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
        // --- Member Savings & Interest Capital Header ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Accrued Savings Value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Text(
                            text = formatter.format(totalApprovedSavings + totalAccruedInterest),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Rate: ${rule.savingsInterestRate}% p.a.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Approved Savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(formatter.format(totalApprovedSavings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interest Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(formatter.format(totalAccruedInterest), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }
        }

        // --- Color-coded Monthly Savings Timeline ---
        Text(
            text = "Your Savings Progress (2026)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(months) { index, monthName ->
                val monthIndex = index + 1
                val monthPayments = payments.filter { it.cycleMonthIndex == monthIndex }
                val approvedPayment = monthPayments.find { it.status == VerificationStatus.APPROVED }
                val pendingPayment = monthPayments.find { it.status == VerificationStatus.PENDING }

                val required = rule.monthlyAmount
                val paid = approvedPayment?.amountPaid ?: 0.0

                val currentMonthIndex = sysCurrentMonthIndex
                val timelineColor: Color
                val statusText: String

                if (approvedPayment != null && paid >= required) {
                    if (monthIndex > currentMonthIndex) {
                        timelineColor = Color(0xFF3B82F6) // Blue (Advance Payment)
                        statusText = "Advance Paid"
                    } else {
                        timelineColor = Color(0xFF10B981) // Green (Paid)
                        statusText = "Paid"
                    }
                } else if (pendingPayment != null) {
                    timelineColor = Color(0xFFF59E0B) // Yellow (Pending)
                    statusText = "Pending Verification"
                } else if (monthIndex < currentMonthIndex && paid < required) {
                    timelineColor = Color(0xFFEF4444) // Red (Missed / Deficit)
                    statusText = "Missed/Deficit"
                } else {
                    timelineColor = Color(0xFF94A3B8) // Grey (Unpaid / Future)
                    statusText = "Not Paid"
                }

                Card(
                    modifier = Modifier
                        .width(135.dp)
                        .border(1.5.dp, timelineColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = monthName.take(3).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(timelineColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (statusText) {
                                    "Paid" -> Icons.Default.CheckCircle
                                    "Advance Paid" -> Icons.Default.TrendingUp
                                    "Pending Verification" -> Icons.Default.HourglassEmpty
                                    "Missed/Deficit" -> Icons.Default.ErrorOutline
                                    else -> Icons.Default.RadioButtonUnchecked
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = timelineColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Saved: ${formatter.format(paid)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // --- Interactive Savings Interest Calculator / Forecaster Tool ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showCalcTools = !showCalcTools },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Interactive Savings Interest Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = if (showCalcTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }

                if (showCalcTools) {
                    Text("Forecast your wealth growth based on regular compound interest calculations using the Sacco annual interest rate.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    OutlinedTextField(
                        value = calcPrincipalStr,
                        onValueChange = { calcPrincipalStr = it },
                        label = { Text("Projection Principal Deposit (UGX)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = calcPeriodMonthsStr,
                        onValueChange = { calcPeriodMonthsStr = it },
                        label = { Text("Savings Duration (Months)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Compounding:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        listOf("Monthly", "Quarterly", "Annually").forEach { freq ->
                            val isSelected = calcCompoundingFreq == freq
                            SuggestionChip(
                                onClick = { calcCompoundingFreq = freq },
                                label = { Text(freq, fontSize = 9.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Projected Interest", fontSize = 10.sp, color = Color.Gray)
                                Text(formatter.format(computedInterestValue), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Maturity Value", fontSize = 10.sp, color = Color.Gray)
                                Text(formatter.format(computedMaturityValue), fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Time-Weighted Monthly performance Board & Annual Reward Share
        Card(
            modifier = Modifier.fillMaxWidth().testTag("time_weighted_reward_card_savings"),
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
                                val actualMonth = getActualMonthIndex(p.datePaid, p.cycleMonthIndex)
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

        // --- Submit Bank Deposit / Mobile Money Receipt ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Submit Savings Deposit Receipt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Deposit monthly savings directly to Sacco Account or Mobile Money Merchant, then upload details here for verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                if (formError.isNotEmpty()) {
                    Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; formError = "" },
                    label = { Text("Deposit Amount (UGX)") },
                    leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_amount_input"),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("For Month:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(months[paymentMonthIndex - 1])
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                months.forEachIndexed { i, m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            paymentMonthIndex = i + 1
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bank Operator:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        var bankExpanded by remember { mutableStateOf(false) }
                        val banks = listOf("Stanbic Bank", "Centenary Bank", "dfcu Bank", "Airtel Mobile Money", "MTN MoMo")
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { bankExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(bankName)
                            }
                            DropdownMenu(
                                expanded = bankExpanded,
                                onDismissRequest = { bankExpanded = false }
                            ) {
                                banks.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b) },
                                        onClick = {
                                            bankName = b
                                            bankExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Bank Branch") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = receiptNo,
                        onValueChange = { receiptNo = it; formError = "" },
                        label = { Text("Receipt No / Slip ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = txId,
                    onValueChange = { txId = it },
                    label = { Text("Transaction ID / Reference") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Remarks (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                // Virtual Camera Attach Placeholder
                Text(
                    text = "Attach Receipt Document / Image (PDF, JPG, PNG)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasCameraPermission) {
                                cameraLauncher.launch(null)
                            } else {
                                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Upload Document")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Selected File Preview
                if (selectedFileUri != null || cameraBitmap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isImageSelected) Icons.Default.Image else Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(selectedFileName ?: "Attached Receipt Image", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = {
                                selectedFileUri = null
                                cameraBitmap = null
                                selectedFileName = null
                                isImageSelected = false
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull()
                        if (amt == null || amt <= 0.0) {
                            formError = "Please enter a valid deposit amount greater than zero."
                        } else if (receiptNo.isEmpty()) {
                            formError = "Please enter the receipt or slip reference number."
                        } else if (selectedFileUri == null && cameraBitmap == null) {
                            formError = "Please attach a deposit receipt image or PDF document first."
                        } else {
                            onSubmitPayment(amt, paymentMonthIndex, bankName, branchName, txId, receiptNo, notes, selectedFileUri?.toString() ?: selectedFileName ?: "")
                            showSuccessDialog = true
                            amountStr = ""
                            receiptNo = ""
                            txId = ""
                            notes = ""
                            selectedFileUri = null
                            selectedFileName = null
                            cameraBitmap = null
                            isImageSelected = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_deposit_slip"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit Deposit Slip")
                }
            }
        }

        // --- Your Transaction ledger table ---
        Text(
            text = "Verified Savings & Accumulated Interest",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (payments.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No payment transactions recorded.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deposit Date", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        Text("Month Saved", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text("Amount (UGX)", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                        Text("Est. Interest", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    }

                    payments.forEach { p ->
                        val monthsSavedInYear = (12 - p.cycleMonthIndex + 1).coerceIn(1, 12)
                        val estInterest = if (p.status == VerificationStatus.APPROVED) {
                            p.amountPaid * (rule.savingsInterestRate / 100.0) * (monthsSavedInYear / 12.0)
                        } else 0.0

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.datePaid.take(10), fontSize = 10.sp, modifier = Modifier.weight(1.2f))
                            Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                                Text(months[p.cycleMonthIndex - 1], fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (p.status) {
                                            VerificationStatus.APPROVED -> Color(0xFFD1FAE5)
                                            VerificationStatus.PENDING -> Color(0xFFFEF3C7)
                                            VerificationStatus.REJECTED -> Color(0xFFFEE2E2)
                                        }
                                    )
                                ) {
                                    Text(p.status.name.take(3), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                                }
                            }
                            Text(formatter.format(p.amountPaid), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                            Text(formatter.format(estInterest), fontSize = 10.sp, color = if (estInterest > 0) Color(0xFF10B981) else Color.Gray, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    }
                }
            }
        }

        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                title = { Text("Receipt Uploaded Successfully") },
                text = { Text("Your deposit receipt has been submitted and is pending verification by the Sacco administrators. You will receive a notification as soon as it is approved.") },
                confirmButton = {
                    Button(onClick = { showSuccessDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

// Utility function to resolve file names
fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Selected File"
}

