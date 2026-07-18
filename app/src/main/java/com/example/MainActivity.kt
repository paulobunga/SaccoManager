package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.data.*
import com.example.network.FirebaseAuthManager
import com.example.network.GeminiApiClient
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var database: SaccoDatabase
    private lateinit var repository: SaccoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize Room Database and Repository
        database = Room.databaseBuilder(
            applicationContext,
            SaccoDatabase::class.java,
            "sacco_management_db"
        )
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
            .build()

        repository = SaccoRepository(applicationContext, database)

        // 2. Pre-seed Test Data on Startup (async coroutine)
        lifecycleScope.launch {
            repository.seedTestData()
        }

        setContent {
            MyApplicationTheme {
                MainContent(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(repository: SaccoRepository) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Auth States
    var loggedInUser by remember { mutableStateOf<SaccoUser?>(null) }
    var activeRole by remember { mutableStateOf(UserRole.MEMBER) }
    var showRegisterForm by remember { mutableStateOf(false) }
    // True once the Firebase session check on startup has completed (prevents LoginScreen flash)
    var sessionCheckDone by remember { mutableStateOf(false) }

    // Navigation States
    var currentScreenRoute by remember { mutableStateOf("DASHBOARD") }

    // -------------------------------------------------------------------------
    // Startup: restore Firebase Auth session without re-login (REQ-4)
    // -------------------------------------------------------------------------
    LaunchedEffect(Unit) {
        if (FirebaseAuthManager.isLoggedIn()) {
            val firebaseUid = FirebaseAuthManager.currentUser?.uid
            if (firebaseUid != null) {
                val user = withContext(Dispatchers.IO) {
                    repository.getUserByFirebaseUid(firebaseUid)
                }
                if (user != null) {
                    loggedInUser = user
                    activeRole = user.role
                    currentScreenRoute = if (user.role == UserRole.ADMIN || user.role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                }
            }
        }
        sessionCheckDone = true
    }

    // Flow Observers (Live Database updates)
    val profilesList by repository.allProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPayments by repository.allPayments.collectAsStateWithLifecycle(initialValue = emptyList())
    val allLoans by repository.allApplications.collectAsStateWithLifecycle(initialValue = emptyList())
    val allProducts by repository.allProducts.collectAsStateWithLifecycle(initialValue = emptyList())
    val auditLogs by repository.allAuditLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val savingsRuleState by repository.savingsRule.collectAsStateWithLifecycle(initialValue = null)
    val allExpenses by repository.allExpenses.collectAsStateWithLifecycle(initialValue = emptyList())
    val allUsers by repository.allUsers.collectAsStateWithLifecycle(initialValue = emptyList())
    val allSavingsPlans by repository.allSavingsPlans.collectAsStateWithLifecycle(initialValue = emptyList())
    val allReferrals by repository.allReferrals.collectAsStateWithLifecycle(initialValue = emptyList())

    // Hybrid Edge Caching States
    val isOnline by repository.syncEngine.isOnline.collectAsStateWithLifecycle(initialValue = false)
    val isSyncing by repository.syncEngine.isSyncing.collectAsStateWithLifecycle(initialValue = false)

    val currentRule = savingsRuleState ?: SavingsRule()

    // Filter flows based on logged-in member context
    val memberPayments = remember(loggedInUser, allPayments) {
        if (loggedInUser != null) {
            allPayments.filter { it.memberId == loggedInUser?.id }
        } else emptyList()
    }

    val memberLoans = remember(loggedInUser, allLoans) {
        if (loggedInUser != null) {
            allLoans.filter { it.memberId == loggedInUser?.id }
        } else emptyList()
    }

    val guarantorLoans = remember(loggedInUser, allLoans) {
        if (loggedInUser != null) {
            allLoans.filter { it.guarantorId == loggedInUser?.id }
        } else emptyList()
    }

    val memberPlan = remember(loggedInUser, allSavingsPlans) {
        if (loggedInUser != null) {
            allSavingsPlans.find { it.memberId == loggedInUser?.id }
        } else null
    }

    val memberReferralsList = remember(loggedInUser, allReferrals) {
        if (loggedInUser != null) {
            allReferrals.filter { it.referrerId == loggedInUser?.id }
        } else emptyList()
    }

    val notificationsList by remember(loggedInUser) {
        derivedStateOf {
            if (loggedInUser != null) {
                repository.getNotifications(loggedInUser!!.id)
            } else repository.getNotifications("ALL")
        }
    }.value.collectAsStateWithLifecycle(initialValue = emptyList())

    val activeProfile = remember(loggedInUser, profilesList) {
        profilesList.find { it.memberId == loggedInUser?.id }
    }

    // Handlers for authentication success
    val onLoginSuccess: suspend (String, String, UserRole) -> Pair<Boolean, String> = { username, password, role ->
        // Step 1: Authenticate via Firebase Auth (REQ-4)
        val firebaseResult = FirebaseAuthManager.login(username, password)
        if (firebaseResult.isFailure) {
            // Firebase Auth failed — return the Firebase error message directly
            Pair(false, firebaseResult.exceptionOrNull()?.message ?: "Authentication failed. Please try again.")
        } else {
            // Step 2: Firebase Auth succeeded — look up local Room record for role/profile validation
            val firebaseUser = firebaseResult.getOrNull()
            val user = withContext(Dispatchers.IO) { repository.getUserById(username) }
            if (user != null) {
                if (user.role == role) {
                    // Step 3: Store the Firebase UID on the local record if not already set (REQ-4)
                    if (firebaseUser != null && user.firebaseUid != firebaseUser.uid) {
                        withContext(Dispatchers.IO) {
                            repository.updateUser(user.copy(firebaseUid = firebaseUser.uid))
                        }
                        loggedInUser = user.copy(firebaseUid = firebaseUser.uid)
                    } else {
                        loggedInUser = user
                    }
                    activeRole = role
                    currentScreenRoute = if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                    repository.logAudit(user.name, role.name, "LOGIN_SUCCESS", "Successfully signed into SACCO Manager.")
                    Pair(true, "Login Successful")
                } else {
                    // Firebase auth passed but the selected role doesn't match the registered role
                    FirebaseAuthManager.logout()
                    Pair(false, "Role mismatch: User is registered as a ${user.role.name}.")
                }
            } else {
                // Firebase account exists but no matching local Room record
                // This can happen after a DB wipe; still allow login and restore from Firebase UID
                val userByUid = if (firebaseUser != null) {
                    withContext(Dispatchers.IO) { repository.getUserByFirebaseUid(firebaseUser.uid) }
                } else null
                if (userByUid != null) {
                    if (userByUid.role == role) {
                        loggedInUser = userByUid
                        activeRole = role
                        currentScreenRoute = if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                        repository.logAudit(userByUid.name, role.name, "LOGIN_SUCCESS", "Session restored via Firebase UID.")
                        Pair(true, "Login Successful")
                    } else {
                        FirebaseAuthManager.logout()
                        Pair(false, "Role mismatch: User is registered as a ${userByUid.role.name}.")
                    }
                } else {
                    FirebaseAuthManager.logout()
                    Pair(false, "User account not found locally. Please contact your SACCO administrator.")
                }
            }
        }
    }

    val onRegisterSubmit: (SaccoUser, MemberProfile, String) -> Unit = { user, profile, password ->
        scope.launch {
            repository.registerUser(user, profile, password)
            showRegisterForm = false
        }
    }

    val onLogOut: () -> Unit = {
        scope.launch {
            loggedInUser?.let {
                repository.logAudit(it.name, activeRole.name, "LOGOUT", "Signed out of session.")
            }
            loggedInUser = null
            showRegisterForm = false
            currentScreenRoute = "DASHBOARD"
        }
    }

    if (loggedInUser == null) {
        if (!sessionCheckDone) {
            // Still checking Firebase session — show a loading indicator to avoid LoginScreen flash
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (showRegisterForm) {
            RegistrationScreen(
                onRegisterSubmit = onRegisterSubmit,
                onNavigateBack = { showRegisterForm = false }
            )
        } else {
            LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onNavigateToRegister = { showRegisterForm = true },
                onResetPassword = { username, _ ->
                    repository.resetPassword(username)
                }
            )
        }
    } else {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isMobile = configuration.screenWidthDp < 600

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("SACCO Manager", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("Session: ${loggedInUser?.name} (${activeRole.name})", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = onLogOut, modifier = Modifier.testTag("logout_button")) {
                            Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                if (isMobile) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (activeRole == UserRole.MEMBER || activeRole == UserRole.GUARANTOR) {
                            NavigationBarItem(
                                selected = currentScreenRoute == "DASHBOARD",
                                onClick = { currentScreenRoute = "DASHBOARD" },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Home", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "SAVINGS",
                                onClick = { currentScreenRoute = "SAVINGS" },
                                icon = { Icon(Icons.Default.Savings, contentDescription = null) },
                                label = { Text("Savings", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "LOANS",
                                onClick = { currentScreenRoute = "LOANS" },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                label = { Text("Loans", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "GUARANTOR",
                                onClick = { currentScreenRoute = "GUARANTOR" },
                                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                label = { Text("Guarantees", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "AI_COACH",
                                onClick = { currentScreenRoute = "AI_COACH" },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                label = { Text("AI Coach", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "REPORTS",
                                onClick = { currentScreenRoute = "REPORTS" },
                                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                label = { Text("Reports", fontSize = 10.sp) }
                            )
                        } else {
                            // Admin / Super Admin navigation icons
                            NavigationBarItem(
                                selected = currentScreenRoute == "ADMIN_PANEL",
                                onClick = { currentScreenRoute = "ADMIN_PANEL" },
                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                label = { Text("Admin Desk", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "AI_COACH",
                                onClick = { currentScreenRoute = "AI_COACH" },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                label = { Text("AI Assessor", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = currentScreenRoute == "REPORTS",
                                onClick = { currentScreenRoute = "REPORTS" },
                                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                label = { Text("Reports", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!isMobile) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        if (activeRole == UserRole.MEMBER || activeRole == UserRole.GUARANTOR) {
                            NavigationRailItem(
                                selected = currentScreenRoute == "DASHBOARD",
                                onClick = { currentScreenRoute = "DASHBOARD" },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                label = { Text("Home", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "SAVINGS",
                                onClick = { currentScreenRoute = "SAVINGS" },
                                icon = { Icon(Icons.Default.Savings, contentDescription = null) },
                                label = { Text("Savings", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "LOANS",
                                onClick = { currentScreenRoute = "LOANS" },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                label = { Text("Loans", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "GUARANTOR",
                                onClick = { currentScreenRoute = "GUARANTOR" },
                                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                label = { Text("Guarantees", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "AI_COACH",
                                onClick = { currentScreenRoute = "AI_COACH" },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                label = { Text("AI Coach", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "REPORTS",
                                onClick = { currentScreenRoute = "REPORTS" },
                                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                label = { Text("Reports", fontSize = 11.sp) }
                            )
                        } else {
                            NavigationRailItem(
                                selected = currentScreenRoute == "ADMIN_PANEL",
                                onClick = { currentScreenRoute = "ADMIN_PANEL" },
                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                label = { Text("Admin Desk", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "AI_COACH",
                                onClick = { currentScreenRoute = "AI_COACH" },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                                label = { Text("AI Assessor", fontSize = 11.sp) }
                            )
                            NavigationRailItem(
                                selected = currentScreenRoute == "REPORTS",
                                onClick = { currentScreenRoute = "REPORTS" },
                                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                label = { Text("Reports", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                when (currentScreenRoute) {
                    "DASHBOARD" -> MemberDashboardScreen(
                        memberId = loggedInUser!!.id,
                        profile = activeProfile,
                        rule = currentRule,
                        payments = memberPayments,
                        allPayments = allPayments,
                        profilesList = profilesList,
                        allLoans = allLoans,
                        allExpenses = allExpenses,
                        loans = memberLoans,
                        notifications = notificationsList,
                        savingsPlan = memberPlan,
                        referrals = memberReferralsList,
                        onUpdateSavingsPlan = { plan ->
                            scope.launch {
                                repository.updateSavingsPlan(plan)
                            }
                        },
                        onQuickAction = { route -> currentScreenRoute = route },
                        onUpdateProfileAndName = { newName, updatedProfile ->
                            scope.launch {
                                repository.updateProfileAndName(loggedInUser!!.id, newName, updatedProfile, loggedInUser!!.name, activeRole.name)
                            }
                        },
                        onResetPassword = { mId, _ ->
                            repository.resetPassword(mId)
                        },
                        isOnline = isOnline,
                        isSyncing = isSyncing
                    )

                    "SAVINGS" -> SavingsTimelineScreen(
                        memberId = loggedInUser!!.id,
                        memberName = loggedInUser!!.name,
                        rule = currentRule,
                        payments = memberPayments,
                        allPayments = allPayments,
                        profilesList = profilesList,
                        allLoans = allLoans,
                        allExpenses = allExpenses,
                        onSubmitPayment = { amt, month, bName, branch, txId, receipt, notes, receiptImg ->
                            scope.launch {
                                repository.submitSavingsPayment(
                                    memberId = loggedInUser!!.id,
                                    memberName = loggedInUser!!.name,
                                    amount = amt,
                                    cycleMonthIndex = month,
                                    cycleYear = 2026,
                                    bankName = bName,
                                    branch = branch,
                                    transactionId = txId,
                                    receiptNumber = receipt,
                                    notes = notes,
                                    receiptImageUrl = receiptImg
                                )
                            }
                        }
                    )

                    "LOANS" -> LoanScreen(
                        memberId = loggedInUser!!.id,
                        memberName = loggedInUser!!.name,
                        products = allProducts,
                        applications = memberLoans,
                        membersList = profilesList,
                        eligibilityCheck = { amt -> repository.checkLoanEligibility(loggedInUser!!.id, amt) },
                        onSubmitApplication = { purpose, amt, period, guarantor, comments ->
                            scope.launch {
                                repository.applyForLoan(
                                    memberId = loggedInUser!!.id,
                                    applicantName = loggedInUser!!.name,
                                    purpose = purpose,
                                    amount = amt,
                                    periodMonths = period,
                                    guarantorId = guarantor,
                                    comments = comments
                                )
                            }
                        },
                        onRepayLoan = { loanId, amt, receipt, overpaymentAction, nextLoanId ->
                            scope.launch {
                                repository.repayLoan(
                                    applicationId = loanId,
                                    memberId = loggedInUser!!.id,
                                    amount = amt,
                                    receiptNumber = receipt,
                                    overpaymentAction = overpaymentAction,
                                    nextLoanId = nextLoanId
                                )
                            }
                        }
                    )

                    "GUARANTOR" -> GuarantorDashboardScreen(
                        guarantorId = loggedInUser!!.id,
                        profile = activeProfile,
                        guaranteeRequests = guarantorLoans,
                        onApproveGuarantee = { appId, approved ->
                            scope.launch {
                                repository.approveGuarantee(appId, loggedInUser!!.id, approved)
                            }
                        }
                    )

                    "ADMIN_PANEL" -> AdminDashboardScreen(
                        profilesList = profilesList,
                        usersList = allUsers,
                        paymentsList = allPayments,
                        loansList = allLoans,
                        expensesList = allExpenses,
                        rule = currentRule,
                        auditLogs = auditLogs,
                        savingsPlans = allSavingsPlans,
                        referrals = allReferrals,
                        onRunReminderSweep = {
                            scope.launch {
                                val results = repository.runAutomatedReminders(loggedInUser!!.name)
                                Toast.makeText(context, "Sweep Complete! Reminded ${results.size} members.", Toast.LENGTH_LONG).show()
                            }
                        },
                        onVerifyMember = { mId, status ->
                            scope.launch {
                                repository.updateMemberStatus(mId, status, loggedInUser!!.name, activeRole.name)
                            }
                        },
                        onVerifyPayment = { paymentId, status ->
                            scope.launch {
                                repository.verifySavingsPayment(paymentId, status, loggedInUser!!.name, activeRole.name)
                            }
                        },
                        onVerifyLoan = { appId, status, comments ->
                            scope.launch {
                                repository.verifyLoanApplication(appId, status, comments, loggedInUser!!.name, activeRole.name)
                            }
                        },
                        onSaveRule = { r ->
                            scope.launch {
                                repository.updateSavingsRule(r, loggedInUser!!.name, activeRole.name)
                            }
                        },
                        onSubmitExpense = { amt, cat, desc ->
                            scope.launch {
                                repository.submitExpense(amt, cat, desc, loggedInUser!!.name)
                            }
                        },
                        onDeleteExpense = { id ->
                            scope.launch {
                                repository.deleteExpense(id, loggedInUser!!.name)
                            }
                        },
                        onTriggerBackup = {
                            repository.executeGoogleSheetsBackup(loggedInUser!!.name)
                        },
                        onDeclareAndLockDividend = { year, poolAmount, records ->
                            scope.launch {
                                try {
                                    repository.declareAndLockDividend(year, poolAmount, records, loggedInUser!!.name, activeRole.name)
                                    Toast.makeText(context, "Dividends declared and locked successfully for FY $year!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )

                    "REPORTS" -> ReportsScreen(
                        profilesList = profilesList,
                        allPayments = allPayments,
                        allLoans = allLoans,
                        syncEngine = repository.syncEngine,
                        allUsers = allUsers,
                        loggedInUserId = loggedInUser?.id ?: "",
                        activeRole = activeRole
                    )

                    "AI_COACH" -> AiCoachScreen(
                        isAdmin = activeRole == UserRole.ADMIN || activeRole == UserRole.SUPER_ADMIN,
                        loansList = allLoans,
                        onAssessLoan = { loanId -> repository.assessLoanWithAi(loanId) },
                        onCallCoach = { query ->
                            val sysMsg = "You are a friendly, highly intelligent wealth coach advising cooperative members on managing their savings and qualifying for micro-loans."
                            GeminiApiClient.generateContent(query, sysMsg)
                        }
                    )
                }
            }
          }
        }
    }
}
