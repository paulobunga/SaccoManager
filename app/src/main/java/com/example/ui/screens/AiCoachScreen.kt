package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachScreen(
    isAdmin: Boolean,
    loansList: List<LoanApplication>,
    onAssessLoan: suspend (Int) -> String,
    onCallCoach: suspend (String) -> String
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(if (isAdmin) 1 else 0) }

    // Coach Chat State
    var chatQuery by remember { mutableStateOf("") }
    var chatResponse by remember { mutableStateOf("") }
    var loadingChat by remember { mutableStateOf(false) }

    // Risk Assessor State
    var selectedLoanId by remember { mutableStateOf<Int?>(loansList.firstOrNull()?.id) }
    var assessmentResult by remember { mutableStateOf("") }
    var loadingAssessment by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab header
        TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Gemini Financial Coach", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            if (isAdmin) {
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("AI Credit Assessor", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (selectedTab == 0) {
            // Coach Chat View
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Ask Gemini Financial Advisor", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Text(
                    text = "Welcome to the Sacco Gemini AI wealth coach. Ask about investment strategies, saving cycle optimization, interest structures, or credit rating improvement advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Quick query pills
                val quickQueries = listOf(
                    "How can I double my loan eligibility limits?",
                    "What is Reducing Balance vs Flat rate interest?",
                    "Suggest a savings budget to hit UGX 2M this cycle."
                )

                Text("Suggested Queries:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickQueries.forEach { query ->
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!loadingChat) {
                                    chatQuery = query
                                    loadingChat = true
                                    chatResponse = ""
                                    scope.launch {
                                        chatResponse = onCallCoach(query)
                                        loadingChat = false
                                    }
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = query,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 3,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                // Chat response viewport
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (loadingChat) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (chatResponse.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Gemini Response:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    text = chatResponse,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Ask your first question above or select a suggested topic to begin conversing with the financial coach.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // Input bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chatQuery,
                        onValueChange = { chatQuery = it },
                        placeholder = { Text("Ask anything...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_coach_query_input"),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (chatQuery.isNotEmpty() && !loadingChat) {
                                val q = chatQuery
                                chatQuery = ""
                                loadingChat = true
                                chatResponse = ""
                                scope.launch {
                                    chatResponse = onCallCoach(q)
                                    loadingChat = false
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    } else {
            // AI Credit Assessor View
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
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Gemini Automated Credit Scoring", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Text(
                    text = "Select any pending loan application in the registry. Gemini will read their historical member profile parameters, savings cycle timeline ledger, past repayment metrics, and evaluate their credit rating, interest eligibility, and potential default risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Select Loan application dropdown
                Column {
                    Text("Select Target Loan Application:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    var lExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { lExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            val selectedLoan = loansList.find { it.id == selectedLoanId }
                            Text(
                                if (selectedLoan != null) "${selectedLoan.applicantName} - UGX ${selectedLoan.amountRequested} (${selectedLoan.purpose})"
                                else "Select Loan Application"
                            )
                        }
                        DropdownMenu(expanded = lExpanded, onDismissRequest = { lExpanded = false }) {
                            loansList.forEach { loan ->
                                DropdownMenuItem(
                                    text = { Text("${loan.applicantName} - UGX ${loan.amountRequested} (${loan.status})") },
                                    onClick = {
                                        selectedLoanId = loan.id
                                        lExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (selectedLoanId != null) {
                            loadingAssessment = true
                            assessmentResult = ""
                            scope.launch {
                                val res = onAssessLoan(selectedLoanId!!)
                                assessmentResult = res
                                loadingAssessment = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("assess_with_ai_button"),
                    enabled = selectedLoanId != null && !loadingAssessment
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Credit Assessment with Gemini AI")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (loadingAssessment) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (assessmentResult.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "AI Credit Assessor Output:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = assessmentResult,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                        } else {
                            Text(
                                text = "Assessment metrics not run yet. Select an application above and execute evaluation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
          }
        }
    }
}
