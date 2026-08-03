package com.litesails.saccomanager

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
import com.litesails.saccomanager.data.*
import com.litesails.saccomanager.network.SupabaseAuthManager
import com.litesails.saccomanager.network.GeminiApiClient
import com.litesails.saccomanager.ui.screens.*
import com.litesails.saccomanager.ui.theme.MyApplicationTheme
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

        // Initialize Clerk Auth Manager
        ClerkAuthManager.initialize(applicationContext)

        // 1. Initialize Room Database and Repository
        database = Room.databaseBuilder(
            applicationContext,
            SaccoDatabase::class.java,
            "sacco_management_db"
        )
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)
            .build()

        repository = SaccoRepository(applicationContext, database)

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
    // True once the Clerk session check on startup has completed (prevents LoginScreen flash)
    var sessionCheckDone by remember { mutableStateOf(false) }

    // Navigation States
    var currentScreenRoute by remember { mutableStateOf("DASHBOARD") }

    // -------------------------------------------------------------------------
    // Startup: restore Clerk session without re-login (REQ-4)
    // Startup: restore Supabase-backed session without re-login
    LaunchedEffect(Unit) {
        if (SupabaseAuthManager.isLoggedIn) {
            val storedUserId = SupabaseAuthManager.userId
            val storedRole = SupabaseAuthManager.role
            val storedMembership = SupabaseAuthManager.membershipNumber
            if (storedUserId != null) {
                val localUser = withContext(Dispatchers.IO) { repository.getUserById(storedUserId) }
                if (localUser != null) {
                    loggedInUser = localUser.copy(membershipNumber = storedMembership ?: localUser.membershipNumber)
                    activeRole = localUser.role
                    currentScreenRoute = if (localUser.role == UserRole.ADMIN || localUser.role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                } else {
                    val restored = SaccoUser(
                        id = storedUserId,
                        email = SupabaseAuthManager.email ?: storedUserId,
                        phone = "",
                        name = SupabaseAuthManager.name ?: "User",
                        role = UserRole.valueOf(storedRole ?: UserRole.MEMBER.name),
                        status = MemberStatus.ACTIVE,
                        membershipNumber = storedMembership ?: "",
                        clerkUserId = storedUserId
                    )
                    withContext(Dispatchers.IO) { repository.updateUser(restored) }
                    loggedInUser = restored
                    activeRole = restored.role
                    currentScreenRoute = if (restored.role == UserRole.ADMIN || restored.role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
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
        // Step 1: Authenticate via Supabase-backed auth flow using Clerk Third Party Auth
        val authResult = SupabaseAuthManager.signIn(username, password)
        if (authResult.isFailure) {
            Pair(false, authResult.exceptionOrNull()?.message ?: "Authentication failed. Please try again.")
        } else {
            // Step 2: Auth succeeded — look up local Room record for role/profile validation
            val authUser = authResult.getOrNull()
            val localUserId = authUser?.get("id") as? String ?: username
            val localUser = withContext(Dispatchers.IO) { repository.getUserById(localUserId) }
            if (localUser != null) {
                if (localUser.role == role) {
                    val storedRole = authUser?.get("role") as? String ?: localUser.role.name
                    val membershipNumber = authUser?.get("membershipNumber") as? String ?: localUser.membershipNumber
                    loggedInUser = localUser.copy(clerkUserId = localUserId, membershipNumber = membershipNumber)
                    activeRole = localUser.role
                    currentScreenRoute = if (localUser.role == UserRole.ADMIN || localUser.role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                    repository.logAudit(localUser.name, storedRole, "LOGIN_SUCCESS", "Successfully signed into SACCO Manager.")
                    Pair(true, "Login Successful")
                } else {
                    SupabaseAuthManager.logout()
                    Pair(false, "Role mismatch: User is registered as a ${localUser.role.name}.")
                }
            } else {
                // Auth account exists but no matching local Room record
                val membershipNumber = authUser?.get("membershipNumber") as? String
                val newLocal = SaccoUser(
                    id = localUserId,
                    email = localUserId,
                    phone = "",
                    name = (authUser?.get("name") as? String) ?: username,
                    role = UserRole.valueOf(storedRole ?: role.name),
                    status = MemberStatus.ACTIVE,
                    membershipNumber = membershipNumber ?: "",
                    clerkUserId = localUserId
                )
                withContext(Dispatchers.IO) { repository.updateUser(newLocal) }
                loggedInUser = newLocal
                activeRole = newLocal.role
                currentScreenRoute = if (newLocal.role == UserRole.ADMIN || newLocal.role == UserRole.SUPER_ADMIN) "ADMIN_PANEL" else "DASHBOARD"
                repository.logAudit(newLocal.name, newLocal.role.name, "LOGIN_SUCCESS", "Session restored via Supabase-backed auth.")
                Pair(true, "Login Successful")
            }
        }
    }

    val onRegisterSubmit: (SaccoUser, MemberProfile, String) -> Unit = { user, profile, password ->
        scope.launch {
            try {
                val authResult = SupabaseAuthManager.signUp(
                    email = user.email,
                    password = password,
                    fullName = user.name,
                    phone = user.phone,
                    role = user.role.name
                )
                if (authResult.isSuccess) {
                    val created = authResult.getOrNull() ?: emptyMap()
                    val clerkId = created["id"] as? String ?: user.id
                    val newUser = user.copy(id = clerkId, clerkUserId = clerkId)
                    repository.registerUser(newUser, profile.copy(memberId = clerkId), password)
                    loggedInUser = newUser
                    activeRole = user.role
                    currentScreenRoute = "DASHBOARD"
                } else {
                    Toast.makeText(context, authResult.exceptionOrNull()?.message ?: "Registration failed", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                Toast.makeText(context, t.message ?: "Registration failed", Toast.LENGTH_LONG).show()
            } finally {
                showRegisterForm = false
            }
        }
    }

    val onLogOut: () -> Unit = {
        scope.launch {
            loggedInUser?.let {
                repository.logAudit(it.name, activeRole.name, "LOGOUT", "Signed out of session.")
            }
            ClerkAuthManager.logout()
            loggedInUser = null
            showRegisterForm = false
            currentScreenRoute = "DASHBOARD"
        }
    }

    if (loggedInUser == null) {
        if (!sessionCheckDone) {
            // Still checking Clerk session — show a loading indicator to avoid LoginScreen flash
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
                    scope.launch {
                        SupabaseAuthManager.sendPasswordReset(username)
                    }
                },
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
                            NavigationBarItem(
                                selected = currentScreenRoute == "NOTIFICATIONS",
                                onClick = { currentScreenRoute = "NOTIFICATIONS" },
                                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                label = { Text("Alerts", fontSize = 10.sp) }
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
                            NavigationBarItem(
                                selected = currentScreenRoute == "NOTIFICATIONS",
                                onClick = { currentScreenRoute = "NOTIFICATIONS" },
                                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                                label = { Text("Alerts", fontSize = 10.sp) }
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
                        onResetPassword = { username, _ ->
                            scope.launch {
                                SupabaseAuthManager.sendPasswordReset(username)
                            }
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
                        },
                        onIotecContribution = { amt, month, year, phone, mId, mName, cType, statusCb ->
                            scope.launch {
                                try {
                                    val functionUrl = BuildConfig.SUPABASE_FUNCTION_URL
                                    if (functionUrl.isBlank()) {
                                        statusCb("IOTEC endpoint is not configured.")
                                        return@launch
                                    }
                                    val payload = org.json.JSONObject()
                                        .put("memberId", mId)
                                        .put("memberName", mName)
                                        .put("amount", amt)
                                        .put("phoneNumber", phone)
                                        .put("cycleMonthIndex", month)
                                        .put("cycleYear", year)
                                        .put("contributionType", cType)
                                        .toString()
                                    val raw = com.litesails.saccomanager.network.SaccoNetworkClient.postJson(functionUrl, payload)
                                    val json = org.json.JSONObject(raw)
                                    if (json.optBoolean("success", false)) {
                                        statusCb("Payment request sent successfully (${json.optString("status")}).")
                                    } else {
                                        statusCb(json.optString("message", "Payment request failed."))
                                    }
                                } catch (e: Exception) {
                                    statusCb("Error: ${e.message}")
                                }
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

                    "NOTIFICATIONS" -> NotificationInboxScreen(
                        notifications = notificationsList,
                        onBack = { currentScreenRoute = "DASHBOARD" },
                        onMarkRead = { recipientId ->
                            scope.launch {
                                repository.markNotificationsRead(recipientId)
                            }
                        },
                        onNavigate = { route -> currentScreenRoute = route }
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
