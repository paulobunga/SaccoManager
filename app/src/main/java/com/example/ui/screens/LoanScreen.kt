package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.LemonGreen
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen(
    memberId: String,
    memberName: String,
    products: List<LoanProduct>,
    applications: List<LoanApplication>,
    membersList: List<MemberProfile>,
    eligibilityCheck: suspend (Double) -> Pair<Boolean, String>,
    onSubmitApplication: (String, Double, Int, String, String) -> Unit,
    onRepayLoan: (Int, Double, String, String, Int?) -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    // Application Form state
    var selectedProduct by remember { mutableStateOf(products.firstOrNull()?.id ?: "EMERGENCY") }
    var purpose by remember { mutableStateOf("") }
    var requestedAmountStr by remember { mutableStateOf("") }
    var repaymentMonthsStr by remember { mutableStateOf("6") }
    var guarantorId by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }

    // Eligibility check feedback state
    var eligibilityChecked by remember { mutableStateOf(false) }
    var eligibilitySuccess by remember { mutableStateOf(false) }
    var eligibilityMessage by remember { mutableStateOf("") }
    var checkingEligibility by remember { mutableStateOf(false) }

    // Repayment dialog state
    var showRepayDialog by remember { mutableStateOf(false) }
    var selectedLoanIdToRepay by remember { mutableStateOf<Int?>(null) }
    var repaymentAmountStr by remember { mutableStateOf("") }
    var repayReceiptNo by remember { mutableStateOf("") }
    var overpaymentAction by remember { mutableStateOf("SAVINGS") }
    var selectedNextLoanIdToRepay by remember { mutableStateOf<Int?>(null) }

    var formError by remember { mutableStateOf("") }
    var showApplySuccessDialog by remember { mutableStateOf(false) }

    val myLoans = applications.filter { it.memberId == memberId }

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
        // 1. Available Loan Products List
        Text(
            text = "Cooperative Loan Products",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            products.forEach { prod ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = prod.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Text(
                            text = "${prod.interestRateForMembers}% Rate",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Up to ${prod.maxLoanMultiplier}x Savings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 2. Active Loans and Repayment Controls
        Text(
            text = "Your Active Loans",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        val activeLoans = myLoans.filter { it.status == LoanStatus.DISBURSED }
        if (activeLoans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No active loans outstanding.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
            }
        } else {
            activeLoans.forEach { loan ->
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
                                Text(loan.purpose, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Guarantor Approved: ${loan.guarantorApproved}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5), contentColor = Color(0xFF065F46))
                            ) {
                                Text(
                                    text = "DISBURSED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Principal Requested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(formatter.format(loan.amountRequested), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            }
                            Column {
                                Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text(formatter.format(loan.outstandingBalance), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                            }
                        }

                        LinearProgressIndicator(
                            progress = {
                                val totalToPay = loan.amountRequested * (1 + (loan.interestRate / 100))
                                val paid = totalToPay - loan.outstandingBalance
                                if (totalToPay > 0) (paid / totalToPay).toFloat().coerceIn(0f, 1f) else 0f
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Interest Rate: ${loan.interestRate}% | Months: ${loan.repaymentPeriodMonths}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            Button(
                                onClick = {
                                    selectedLoanIdToRepay = loan.id
                                    showRepayDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Repay Loan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Loan Application Form Card
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
                    Icon(Icons.Default.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Apply for a Cooperative Loan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (formError.isNotEmpty()) {
                    Text(formError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                // Loan Product Dropdown
                Column {
                    Text("Select Loan Product", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    var pExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { pExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(products.find { it.id == selectedProduct }?.name ?: selectedProduct)
                        }
                        DropdownMenu(expanded = pExpanded, onDismissRequest = { pExpanded = false }) {
                            products.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text("${prod.name} (${prod.interestRateForMembers}% rate)") },
                                    onClick = {
                                        selectedProduct = prod.id
                                        repaymentMonthsStr = prod.repaymentPeriodMonths.toString()
                                        pExpanded = false
                                        // Reset eligibility when product changes
                                        eligibilityChecked = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = requestedAmountStr,
                    onValueChange = {
                        requestedAmountStr = it
                        formError = ""
                        eligibilityChecked = false
                    },
                    label = { Text("Requested Amount (UGX)") },
                    leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Row for Repayment Months and Check Eligibility Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = repaymentMonthsStr,
                        onValueChange = { repaymentMonthsStr = it },
                        label = { Text("Repayment Period (Months)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val amt = requestedAmountStr.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                formError = "Please enter a valid amount before checking eligibility."
                            } else {
                                checkingEligibility = true
                                eligibilityChecked = false
                                // Launch inside dynamic block
                                coroutineScope.launch {
                                    val (success, msg) = eligibilityCheck(amt)
                                    eligibilitySuccess = success
                                    eligibilityMessage = msg
                                    eligibilityChecked = true
                                    checkingEligibility = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !checkingEligibility
                    ) {
                        Text(if (checkingEligibility) "Checking..." else "Check Eligibility", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Display Eligibility feedback
                if (eligibilityChecked) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (eligibilitySuccess) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                            contentColor = if (eligibilitySuccess) Color(0xFF065F46) else Color(0xFF991B1B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (eligibilitySuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (eligibilitySuccess) "Eligibility Verified" else "Limit Exceeded",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(eligibilityMessage, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it; formError = "" },
                    label = { Text("Loan Purpose (e.g. Expand shop, buy seeds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Guarantor Selector Dropdown
                Column {
                    Text("Select Guarantor (Required if over limits/non-members)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    var gExpanded by remember { mutableStateOf(false) }
                    val otherMembers = membersList.filter { it.memberId != memberId }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { gExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(otherMembers.find { it.memberId == guarantorId }?.fullName ?: "Select Guarantor Member")
                        }
                        DropdownMenu(expanded = gExpanded, onDismissRequest = { gExpanded = false }) {
                            otherMembers.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.fullName} (${m.membershipNumber})") },
                                    onClick = {
                                        guarantorId = m.memberId
                                        gExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Additional Comments") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Button(
                    onClick = {
                        val amt = requestedAmountStr.toDoubleOrNull()
                        val mths = repaymentMonthsStr.toIntOrNull()
                        if (amt == null || amt <= 0) {
                            formError = "Please enter a valid loan amount."
                        } else if (mths == null || mths <= 0) {
                            formError = "Please enter a valid repayment period in months."
                        } else if (purpose.isEmpty()) {
                            formError = "Please state the purpose of this loan."
                        } else {
                            onSubmitApplication(purpose, amt, mths, guarantorId, comments)
                            showApplySuccessDialog = true
                            purpose = ""
                            requestedAmountStr = ""
                            comments = ""
                            guarantorId = ""
                            eligibilityChecked = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("apply_for_loan_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit Loan Application", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // 4. Past Applications status list
        Text(
            text = "Loan Requests History",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        val pendingAndRejected = myLoans.filter { it.status != LoanStatus.DISBURSED }
        if (pendingAndRejected.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No past loan requests found.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendingAndRejected.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(app.purpose, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Applied on: ${app.dateApplied.take(10)} | Score: ${app.loanScore}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatter.format(app.amountRequested),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (app.status) {
                                            LoanStatus.APPROVED -> Color(0xFFD1FAE5)
                                            LoanStatus.PENDING -> Color(0xFFFEF3C7)
                                            LoanStatus.REJECTED -> Color(0xFFFEE2E2)
                                            LoanStatus.COMPLETED -> Color(0xFFDBEAFE)
                                            else -> Color(0xFFF1F5F9)
                                        },
                                        contentColor = when (app.status) {
                                            LoanStatus.APPROVED -> Color(0xFF065F46)
                                            LoanStatus.PENDING -> Color(0xFF92400E)
                                            LoanStatus.REJECTED -> Color(0xFF991B1B)
                                            LoanStatus.COMPLETED -> Color(0xFF1E40AF)
                                            else -> Color(0xFF475569)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = app.status.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }

        // Repayment Dialog
        if (showRepayDialog && selectedLoanIdToRepay != null) {
            val selectedLoan = activeLoans.find { it.id == selectedLoanIdToRepay }
            val outstanding = selectedLoan?.outstandingBalance ?: 0.0
            val enteredAmount = repaymentAmountStr.toDoubleOrNull() ?: 0.0
            val overpaidAmount = enteredAmount - outstanding
            val isOverpaid = overpaidAmount > 0.0

            AlertDialog(
                onDismissRequest = { showRepayDialog = false },
                title = { Text("Repay Loan Installation") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Outstanding balance for this loan: ${formatter.format(outstanding)}")
                        Text("Enter the installment amount paid and bank slip receipt reference.")
                        OutlinedTextField(
                            value = repaymentAmountStr,
                            onValueChange = { repaymentAmountStr = it },
                            label = { Text("Amount Paid (UGX)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = repayReceiptNo,
                            onValueChange = { repayReceiptNo = it },
                            label = { Text("Receipt Slip Number") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isOverpaid) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Text(
                                            text = "Overpayment: ${formatter.format(overpaidAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Text(
                                        text = "Choose how you want to allocate the excess balance of your payment:",
                                        style = MaterialTheme.typography.labelSmall
                                    )

                                    // Option 1: Settle on savings
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = overpaymentAction == "SAVINGS",
                                            onClick = { overpaymentAction = "SAVINGS" }
                                        )
                                        Text(
                                            "Settle on my monthly savings",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (overpaymentAction == "SAVINGS") FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    // Option 2: Apply to another loan (if available)
                                    val otherActiveLoans = activeLoans.filter { it.id != selectedLoanIdToRepay }
                                    if (otherActiveLoans.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            RadioButton(
                                                selected = overpaymentAction == "NEXT_LOAN",
                                                onClick = { 
                                                    overpaymentAction = "NEXT_LOAN" 
                                                    if (selectedNextLoanIdToRepay == null) {
                                                        selectedNextLoanIdToRepay = otherActiveLoans.firstOrNull()?.id
                                                    }
                                                }
                                            )
                                            Text(
                                                "Apply to another active loan",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (overpaymentAction == "NEXT_LOAN") FontWeight.Bold else FontWeight.Normal
                                            )
                                        }

                                        if (overpaymentAction == "NEXT_LOAN") {
                                            var nextLoanExpanded by remember { mutableStateOf(false) }
                                            val selectedNextLoan = otherActiveLoans.find { it.id == selectedNextLoanIdToRepay }

                                            Box(modifier = Modifier.fillMaxWidth().padding(start = 24.dp)) {
                                                OutlinedButton(
                                                    onClick = { nextLoanExpanded = true },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = selectedNextLoan?.let { "${it.purpose} (${formatter.format(it.outstandingBalance)} left)" } ?: "Select Loan",
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = nextLoanExpanded,
                                                    onDismissRequest = { nextLoanExpanded = false }
                                                ) {
                                                    otherActiveLoans.forEach { loanItem ->
                                                        DropdownMenuItem(
                                                            text = { Text("${loanItem.purpose} (${formatter.format(loanItem.outstandingBalance)} outstanding)") },
                                                            onClick = {
                                                                selectedNextLoanIdToRepay = loanItem.id
                                                                nextLoanExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "(No other active loans available to route overpayment)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = repaymentAmountStr.toDoubleOrNull()
                            if (amt != null && amt > 0 && repayReceiptNo.isNotEmpty()) {
                                onRepayLoan(
                                    selectedLoanIdToRepay!!,
                                    amt,
                                    repayReceiptNo,
                                    if (isOverpaid) overpaymentAction else "NONE",
                                    if (isOverpaid && overpaymentAction == "NEXT_LOAN") selectedNextLoanIdToRepay else null
                                )
                                showRepayDialog = false
                                repaymentAmountStr = ""
                                repayReceiptNo = ""
                                overpaymentAction = "SAVINGS"
                                selectedNextLoanIdToRepay = null
                            }
                        }
                    ) {
                        Text("Record Repayment")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRepayDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showApplySuccessDialog) {
            AlertDialog(
                onDismissRequest = { showApplySuccessDialog = false },
                title = { Text("Application Submitted") },
                text = { Text("Your loan application has been submitted. Sacco administrators will assess your savings ratio and AI Credit score before approving this loan.") },
                confirmButton = {
                    Button(onClick = { showApplySuccessDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
