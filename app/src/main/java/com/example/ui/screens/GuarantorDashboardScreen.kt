package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.LemonGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GuarantorDashboardScreen(
    guarantorId: String,
    profile: MemberProfile?,
    guaranteeRequests: List<LoanApplication>,
    onApproveGuarantee: (Int, Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "UG")).apply { 
        currency = java.util.Currency.getInstance("UGX")
        maximumFractionDigits = 0
    } }

    // Constants
    val maxExposure = profile?.maxGuaranteeExposure ?: 5000000.0
    val activeGuaranteed = guaranteeRequests.filter { it.guarantorApproved && it.status == LoanStatus.DISBURSED }
    val currentExposure = activeGuaranteed.sumOf { it.outstandingBalance }
    val remainingCap = (maxExposure - currentExposure).coerceAtLeast(0.0)

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
        // 1. Guarantor Exposure Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Guarantor Risk Exposure",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "As a SACCO member, you can guarantee loans for other members. If they default, you assume responsibility for the outstanding balance up to your exposure limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Limit Cap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatter.format(maxExposure), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Column {
                        Text("Active Exposure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatter.format(currentExposure), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
                    }
                    Column {
                        Text("Remaining Cap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatter.format(remainingCap), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                    }
                }

                LinearProgressIndicator(
                    progress = { if (maxExposure > 0) (currentExposure / maxExposure).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 2. Pending Guarantee Requests
        Text(
            text = "Pending Guarantee Invitations",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        val pendingRequests = guaranteeRequests.filter { !it.guarantorApproved && it.status == LoanStatus.PENDING }
        if (pendingRequests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pending guarantee invitations.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = LemonGreen
                )
            }
        } else {
            pendingRequests.forEach { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = req.applicantName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Purpose: ${req.purpose}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = formatter.format(req.amountRequested),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Text(
                            text = "Applicant Savings: ${formatter.format(req.originalSavingsBalance)} | Repayment: ${req.repaymentPeriodMonths} Months",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onApproveGuarantee(req.id, false) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject Request", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { onApproveGuarantee(req.id, true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve Guarantee", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Historically Guaranteed Loans
        Text(
            text = "Guarantees History",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        val approvedGuarantees = guaranteeRequests.filter { it.guarantorApproved }
        if (approvedGuarantees.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No historically guaranteed loans found.", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = LemonGreen)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    approvedGuarantees.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(g.applicantName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Outstanding Bal: ${formatter.format(g.outstandingBalance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (g.status) {
                                        LoanStatus.DISBURSED -> Color(0xFFDBEAFE)
                                        LoanStatus.COMPLETED -> Color(0xFFD1FAE5)
                                        LoanStatus.DEFAULTED -> Color(0xFFFEE2E2)
                                        else -> Color(0xFFF1F5F9)
                                    },
                                    contentColor = when (g.status) {
                                        LoanStatus.DISBURSED -> Color(0xFF1E40AF)
                                        LoanStatus.COMPLETED -> Color(0xFF065F46)
                                        LoanStatus.DEFAULTED -> Color(0xFF991B1B)
                                        else -> Color(0xFF475569)
                                    }
                                )
                            ) {
                                Text(
                                    text = g.status.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
