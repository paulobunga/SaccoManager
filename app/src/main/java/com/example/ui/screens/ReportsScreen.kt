package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.LemonGreen
import java.text.NumberFormat
import java.util.Locale
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    profilesList: List<MemberProfile>,
    allPayments: List<SavingsPayment>,
    allLoans: List<LoanApplication>,
    syncEngine: com.example.network.SaccoSyncEngine? = null,
    allUsers: List<SaccoUser> = emptyList(),
    loggedInUserId: String = "admin",
    activeRole: UserRole = UserRole.ADMIN
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    val isUserAdmin = activeRole == UserRole.ADMIN || activeRole == UserRole.SUPER_ADMIN
    val reportTypes = if (isUserAdmin) {
        listOf("Monthly Savings", "Outstanding Loans", "Sacco Defaulters", "Cash Flow", "Supabase Database Sync")
    } else {
        listOf("Monthly Savings", "Outstanding Loans")
    }

    // Search and Filters State
    var searchQuery by remember { mutableStateOf("") }
    var selectedReportType by remember(reportTypes) { mutableStateOf(reportTypes.first()) }

    var toastMessage by remember { mutableStateOf("") }

    // Helper functions to generate CSV
    fun generateCSV(reportType: String): String {
        val builder = StringBuilder()
        when (reportType) {
            "Monthly Savings" -> {
                builder.append("Member,Month,Year,Amount,Status,Receipt No,Date Paid\n")
                val paymentsToExport = if (isUserAdmin) allPayments else allPayments.filter { it.memberId == loggedInUserId }
                paymentsToExport.forEach { p ->
                    builder.append("\"${p.memberName}\",${p.cycleMonthIndex},2026,${p.amountPaid},${p.status},${p.receiptNumber},\"${p.datePaid}\"\n")
                }
            }
            "Outstanding Loans" -> {
                builder.append("Applicant,Requested,Outstanding Balance,Rate,Status,Date Applied\n")
                val loansToExport = if (isUserAdmin) allLoans else allLoans.filter { it.memberId == loggedInUserId || it.guarantorId == loggedInUserId }
                loansToExport.forEach { l ->
                    builder.append("\"${l.applicantName}\",${l.amountRequested},${l.outstandingBalance},${l.interestRate}%,${l.status},\"${l.dateApplied}\"\n")
                }
            }
            "Sacco Defaulters" -> {
                builder.append("Member,Membership Number,Phone,Status,Unpaid Penalties\n")
                if (isUserAdmin) {
                    val defaulters = profilesList.filter { it.status == MemberStatus.SUSPENDED || it.status == MemberStatus.FROZEN }
                    defaulters.forEach { d ->
                        builder.append("\"${d.fullName}\",${d.membershipNumber},${d.phoneNumber},${d.status},UGX 10000\n")
                    }
                }
            }
            else -> {
                builder.append("Category,Summary Value\n")
                val approvedSavings = if (isUserAdmin) {
                    allPayments.filter { it.status == VerificationStatus.APPROVED }
                } else {
                    allPayments.filter { it.status == VerificationStatus.APPROVED && it.memberId == loggedInUserId }
                }
                val activeLoans = if (isUserAdmin) {
                    allLoans.filter { it.status == LoanStatus.DISBURSED }
                } else {
                    allLoans.filter { it.status == LoanStatus.DISBURSED && (it.memberId == loggedInUserId || it.guarantorId == loggedInUserId) }
                }
                builder.append("Total Verified Savings,UGX ${approvedSavings.sumOf { it.amountPaid }}\n")
                builder.append("Total Active Loans,UGX ${activeLoans.sumOf { it.amountRequested }}\n")
            }
        }
        return builder.toString()
    }

    fun exportCSV() {
        try {
            val csvContent = generateCSV(selectedReportType)
            val fileName = "${selectedReportType.replace(" ", "_")}_Report.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvContent)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$selectedReportType Report - SACCO Manager")
                putExtra(Intent.EXTRA_TEXT, "Attached is the CSV export for the $selectedReportType report.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share CSV Report"))
            toastMessage = "CSV file generated and shared successfully!"
        } catch (e: Exception) {
            toastMessage = "Error generating CSV: ${e.localizedMessage}"
        }
    }

    fun exportExcel() {
        try {
            val csvContent = generateCSV(selectedReportType)
            val fileName = "${selectedReportType.replace(" ", "_")}_Report.xls"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvContent)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.ms-excel"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$selectedReportType Report - SACCO Manager")
                putExtra(Intent.EXTRA_TEXT, "Attached is the Excel XLS export for the $selectedReportType report.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Excel Report"))
            toastMessage = "Excel sheet generated and shared successfully!"
        } catch (e: Exception) {
            toastMessage = "Error generating Excel: ${e.localizedMessage}"
        }
    }

    fun exportPDF() {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 pt
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }
            val boldPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val headerPaint = Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val titlePaint = Paint().apply {
                color = android.graphics.Color.rgb(56, 102, 65) // Dark forest green - SACCO theme
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = android.graphics.Color.LTGRAY
                strokeWidth = 1f
            }

            var y = 50f

            // Title block
            canvas.drawText("SACCO BUSINESS INTELLIGENCE SYSTEM", 50f, y, titlePaint)
            y += 25f
            canvas.drawText("Official Report: $selectedReportType", 50f, y, boldPaint.apply { textSize = 12f })
            y += 20f

            // Metadata block
            val currentDateTime = "2026-06-26 14:40"
            canvas.drawText("Generated On: $currentDateTime (UTC)", 50f, y, textPaint.apply { textSize = 9f })
            y += 15f
            canvas.drawText("Institution: Sacco Management Ltd", 50f, y, textPaint)
            y += 20f

            // Thick separator line
            paint.color = android.graphics.Color.rgb(56, 102, 65)
            paint.strokeWidth = 2f
            canvas.drawLine(50f, y, 545f, y, paint)
            y += 25f

            // Table depending on selected report
            when (selectedReportType) {
                "Monthly Savings" -> {
                    // Draw Table Headers
                    canvas.drawText("Member Name", 50f, y, headerPaint)
                    canvas.drawText("Month", 250f, y, headerPaint)
                    canvas.drawText("Amount Paid", 340f, y, headerPaint)
                    canvas.drawText("Status", 450f, y, headerPaint)
                    y += 15f
                    canvas.drawLine(50f, y, 545f, y, linePaint)
                    y += 18f

                    val paymentsToExport = if (isUserAdmin) allPayments else allPayments.filter { it.memberId == loggedInUserId }
                    paymentsToExport.forEach { p ->
                        if (y > 780f) return@forEach // Single page overflow safety
                        canvas.drawText(p.memberName, 50f, y, textPaint.apply { textSize = 9.5f })
                        canvas.drawText("Month ${p.cycleMonthIndex}", 250f, y, textPaint)
                        canvas.drawText(formatter.format(p.amountPaid), 340f, y, textPaint)
                        canvas.drawText(p.status.name, 450f, y, textPaint)
                        y += 18f
                    }
                }
                "Outstanding Loans" -> {
                    canvas.drawText("Applicant Name", 50f, y, headerPaint)
                    canvas.drawText("Requested", 210f, y, headerPaint)
                    canvas.drawText("Outstanding", 320f, y, headerPaint)
                    canvas.drawText("Rate", 430f, y, headerPaint)
                    canvas.drawText("Status", 480f, y, headerPaint)
                    y += 15f
                    canvas.drawLine(50f, y, 545f, y, linePaint)
                    y += 18f

                    val loansToExport = if (isUserAdmin) allLoans else allLoans.filter { it.memberId == loggedInUserId || it.guarantorId == loggedInUserId }
                    loansToExport.forEach { l ->
                        if (y > 780f) return@forEach
                        canvas.drawText(l.applicantName, 50f, y, textPaint.apply { textSize = 9.5f })
                        canvas.drawText(formatter.format(l.amountRequested), 210f, y, textPaint)
                        canvas.drawText(formatter.format(l.outstandingBalance), 320f, y, textPaint)
                        canvas.drawText("${l.interestRate}%", 430f, y, textPaint)
                        canvas.drawText(l.status.name, 480f, y, textPaint)
                        y += 18f
                    }
                }
                "Sacco Defaulters" -> {
                    canvas.drawText("Member Name", 50f, y, headerPaint)
                    canvas.drawText("Membership No", 210f, y, headerPaint)
                    canvas.drawText("Phone Number", 320f, y, headerPaint)
                    canvas.drawText("Defaulter Status", 440f, y, headerPaint)
                    y += 15f
                    canvas.drawLine(50f, y, 545f, y, linePaint)
                    y += 18f

                    if (isUserAdmin) {
                        val defaulters = profilesList.filter { it.status == MemberStatus.SUSPENDED || it.status == MemberStatus.FROZEN }
                        defaulters.forEach { d ->
                            if (y > 780f) return@forEach
                            canvas.drawText(d.fullName, 50f, y, textPaint.apply { textSize = 9.5f })
                            canvas.drawText(d.membershipNumber, 210f, y, textPaint)
                            canvas.drawText(d.phoneNumber, 320f, y, textPaint)
                            canvas.drawText(d.status.name, 440f, y, textPaint)
                            y += 18f
                        }
                    }
                }
                else -> {
                    // Cash Flow summary table
                    canvas.drawText("Cash Flow Category Summary", 50f, y, headerPaint)
                    canvas.drawText("Aggregated Balance (UGX)", 350f, y, headerPaint)
                    y += 15f
                    canvas.drawLine(50f, y, 545f, y, linePaint)
                    y += 22f

                    val approvedSavings = if (isUserAdmin) {
                        allPayments.filter { it.status == VerificationStatus.APPROVED }
                    } else {
                        allPayments.filter { it.status == VerificationStatus.APPROVED && it.memberId == loggedInUserId }
                    }
                    val activeLoans = if (isUserAdmin) {
                        allLoans.filter { it.status == LoanStatus.DISBURSED }
                    } else {
                        allLoans.filter { it.status == LoanStatus.DISBURSED && (it.memberId == loggedInUserId || it.guarantorId == loggedInUserId) }
                    }
                    val totalApprovedSavings = approvedSavings.sumOf { it.amountPaid }
                    val totalActiveLoans = activeLoans.sumOf { it.amountRequested }
                    val totalInterest = if (isUserAdmin) {
                        allLoans.filter { it.status == LoanStatus.COMPLETED }.sumOf { it.interestPaid }
                    } else {
                        allLoans.filter { it.status == LoanStatus.COMPLETED && it.memberId == loggedInUserId }.sumOf { it.interestPaid }
                    }

                    canvas.drawText(if (isUserAdmin) "Total Verified SACCO Savings Capital" else "My Total Verified Savings", 50f, y, textPaint.apply { textSize = 10f })
                    canvas.drawText(formatter.format(totalApprovedSavings), 350f, y, boldPaint)
                    y += 22f

                    canvas.drawText(if (isUserAdmin) "Total Active Loans Issued Book Value" else "My Outstanding Active Loans", 50f, y, textPaint)
                    canvas.drawText(formatter.format(totalActiveLoans), 350f, y, boldPaint)
                    y += 22f

                    canvas.drawText(if (isUserAdmin) "Total Interest Capital Revenue Earned" else "My Total Paid Loan Interest", 50f, y, textPaint)
                    canvas.drawText(formatter.format(totalInterest), 350f, y, boldPaint)
                    y += 22f
                }
            }

            // Decorative footer line and page metadata
            canvas.drawLine(50f, 800f, 545f, 800f, linePaint)
            canvas.drawText("Sacco Manager BI Tool Suite © 2026. Confidential report for administrative use only.", 50f, 815f, textPaint.apply { textSize = 8f; color = android.graphics.Color.GRAY })

            pdfDocument.finishPage(page)

            val fileName = "${selectedReportType.replace(" ", "_")}_Report.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(file.outputStream())
            pdfDocument.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$selectedReportType Report - SACCO Manager")
                putExtra(Intent.EXTRA_TEXT, "Attached is the PDF print report for $selectedReportType.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
            toastMessage = "Vector PDF print report generated and shared!"
        } catch (e: Exception) {
            toastMessage = "Error generating PDF: ${e.localizedMessage}"
        }
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
        // 1. Title & Search Box
        Text(
            text = "SACCO Business Intelligence Reports",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search records by Member, Phone, ID, Ref, Status...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("report_search_input"),
            singleLine = true
        )

        // 2. Select Report Categories Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rows = reportTypes.chunked(3)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { rType ->
                        FilterChip(
                            selected = selectedReportType == rType,
                            onClick = { selectedReportType = rType },
                            label = { Text(rType, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad empty weight to avoid awkward stretching of single trailing elements on large screens
                    if (rowItems.size < 3) {
                        Spacer(modifier = Modifier.weight((3 - rowItems.size).toFloat()))
                    }
                }
            }
        }

        // 3. Export Summary Button Strip
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Export $selectedReportType Data Table",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            exportCSV()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            exportExcel()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            exportPDF()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 11.sp)
                    }
                }
            }
        }

        // Display results based on selection
        Text(
            text = "Report Overview ($selectedReportType)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        when (selectedReportType) {
            "Monthly Savings" -> {
                val basePayments = if (isUserAdmin) allPayments else allPayments.filter { it.memberId == loggedInUserId }
                val filteredPayments = basePayments.filter {
                    searchQuery.isEmpty() ||
                            it.memberName.contains(searchQuery, ignoreCase = true) ||
                            it.receiptNumber.contains(searchQuery, ignoreCase = true) ||
                            it.status.name.contains(searchQuery, ignoreCase = true)
                }

                if (filteredPayments.isEmpty()) {
                    Text("No matching savings logs.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                } else {
                    filteredPayments.forEach { p ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(p.memberName, fontWeight = FontWeight.Bold)
                                    Text(formatter.format(p.amountPaid), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Ref: ${p.receiptNumber} | Month ${p.cycleMonthIndex}", fontSize = 11.sp, color = Color.Gray)
                                    Text("Status: ${p.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (p.status == VerificationStatus.APPROVED) Color(0xFF10B981) else Color(0xFFF59E0B))
                                }
                            }
                        }
                    }
                }
            }

            "Outstanding Loans" -> {
                val baseLoans = if (isUserAdmin) allLoans else allLoans.filter { it.memberId == loggedInUserId || it.guarantorId == loggedInUserId }
                val filteredLoans = baseLoans.filter {
                    searchQuery.isEmpty() ||
                            it.applicantName.contains(searchQuery, ignoreCase = true) ||
                            it.status.name.contains(searchQuery, ignoreCase = true) ||
                            it.purpose.contains(searchQuery, ignoreCase = true)
                }

                if (filteredLoans.isEmpty()) {
                    Text("No matching loans found.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                } else {
                    filteredLoans.forEach { l ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(l.applicantName, fontWeight = FontWeight.Bold)
                                    Text(formatter.format(l.outstandingBalance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                Text("Purpose: ${l.purpose} | Rate: ${l.interestRate}%", fontSize = 12.sp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Applied on: ${l.dateApplied.take(10)}", fontSize = 11.sp, color = Color.Gray)
                                    Text("Status: ${l.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            "Sacco Defaulters" -> {
                val defaulters = profilesList.filter {
                    it.status == MemberStatus.SUSPENDED || it.status == MemberStatus.FROZEN ||
                            (searchQuery.isNotEmpty() && it.fullName.contains(searchQuery, ignoreCase = true))
                }

                if (defaulters.isEmpty()) {
                    Text("No suspended or frozen defaulter accounts found in the system.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
                } else {
                    defaulters.forEach { d ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(d.fullName, fontWeight = FontWeight.Bold)
                                    Text("LIMIT: FROZEN", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Phone: ${d.phoneNumber} | Email: ${d.email}", fontSize = 12.sp)
                                Text("Outstanding balance default checks: UGX 100,000 late payment penalty applied.", fontSize = 11.sp, color = Color.Red)
                            }
                        }
                    }
                }
            }

            "Supabase Database Sync" -> {
                if (syncEngine != null) {
                    val supabaseRestState by syncEngine.supabaseRestStatus.collectAsState(initial = "Uninitialized")
                    val supabaseAuthState by syncEngine.supabaseAuthStatus.collectAsState(initial = "Uninitialized")
                    val syncCount by syncEngine.supabaseSyncCount.collectAsState(initial = 0)
                    val logs by syncEngine.supabaseLogs.collectAsState(initial = emptyList())
                    val isSyncing by syncEngine.isSyncing.collectAsState(initial = false)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Firebase Cloud Databases Status Board
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Cloud",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Supabase Databases Status Desk",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                                // Status 1: Supabase REST API
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Supabase REST API Database", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(supabaseRestState, fontSize = 11.sp, color = if (supabaseRestState.startsWith("Connected") || supabaseRestState.startsWith("Local")) Color(0xFF10B981) else Color.Red)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                color = if (supabaseRestState.startsWith("Connected") || supabaseRestState.startsWith("Local")) Color(0xFF10B981) else Color.Red,
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                    )
                                }

                                // Status 2: Supabase Auth
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Supabase Auth Gateway", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(supabaseAuthState, fontSize = 11.sp, color = if (supabaseAuthState.startsWith("Connected") || supabaseAuthState.startsWith("Local")) Color(0xFF10B981) else Color.Red)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(
                                                color = if (supabaseAuthState.startsWith("Connected") || supabaseAuthState.startsWith("Local")) Color(0xFF10B981) else Color.Red,
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Active Synced PostgreSQL Rows Ledger", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("$syncCount entries", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Complete Database Sync Trigger
                        Button(
                            onClick = {
                                syncEngine.syncAllToFirebase(allPayments, allLoans, profilesList, allUsers)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing SACCO Ledger to Supabase...")
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Complete Database Sweep to Supabase")
                            }
                        }

                        // Live Supabase Log terminal Console
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E), contentColor = Color(0xFF33FF33)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("supabase-sync-monitor.sh", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("LIVE LOGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                                
                                HorizontalDivider(thickness = 1.dp, color = Color.DarkGray)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (logs.isEmpty()) {
                                        Text("Waiting for sync action or database operations... \nIdle system monitor running.", fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            logs.forEach { log ->
                                                Text(log, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Live Cloud Database Tree Viewer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Text("Supabase JSON & Table Tree Explorer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("Real-time visual schema snapshot mapped in Supabase Database Table consoles:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

                                HorizontalDivider(thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                // Folder: PostgreSQL Tables
                                Text("📁 postgres-root/ (Supabase PostgreSQL Tables)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("📂 savings_payments/   (${allPayments.size} rows)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text("📂 loan_applications/   (${allLoans.size} rows)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text("📂 users_registration/   (${profilesList.size} rows)", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Folder: Supabase Realtime Channels
                                Text("📁 supabase-realtime-root/ (Supabase Realtime Channel Tree)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("▼ 🗃️ sacco_supabase_sync", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("    ▶ 🗂️ savings_payments: { ... }", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("    ▶ 🗂️ loan_repayments: { ... }", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("    ▶ 🗂️ loan_applications: { ... }", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("    ▶ 🗂️ users_registration: { ... }", fontSize = 11.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                } else {
                    Text("Sync engine is not available.", color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                // TAB: Cash Flow
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Sacco Cash Reserves", fontWeight = FontWeight.Bold)
                            val totalApprovedSavings = allPayments.filter { it.status == VerificationStatus.APPROVED }.sumOf { it.amountPaid }
                            Text(formatter.format(totalApprovedSavings), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Out-Loan Asset Book", fontWeight = FontWeight.Bold)
                            val totalActiveLoans = allLoans.filter { it.status == LoanStatus.DISBURSED }.sumOf { it.amountRequested }
                            Text(formatter.format(totalActiveLoans), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Interest Capital Earned", fontWeight = FontWeight.Bold)
                            val totalInterest = allLoans.filter { it.status == LoanStatus.COMPLETED }.sumOf { it.interestPaid }
                            Text(formatter.format(totalInterest), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (toastMessage.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { toastMessage = "" },
                title = { Text("Export Action Successful") },
                text = { Text(toastMessage) },
                confirmButton = {
                    Button(onClick = { toastMessage = "" }) {
                        Text("Awesome")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
