package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: suspend (String, String, UserRole) -> Pair<Boolean, String>,
    onNavigateToRegister: () -> Unit,
    onResetPassword: suspend (String, String) -> Pair<Boolean, String> = { _, _ -> Pair(false, "") }
) {
    var emailOrPhone by remember { mutableStateOf("") }
    var pinOrPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.MEMBER) }
    var errorMessage by remember { mutableStateOf("") }

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotNewPin by remember { mutableStateOf("") }
    var forgotMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 480.dp)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Brand Header with Material Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = "SACCO Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SACCO Manager",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Savings & Credit Cooperative Management System",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = {
                            emailOrPhone = it
                            errorMessage = ""
                        },
                        label = { Text("Email or Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = pinOrPassword,
                        onValueChange = {
                            pinOrPassword = it
                            errorMessage = ""
                        },
                        label = { Text("PIN / Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showForgotDialog = true },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Text("Forgot PIN / Password?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (showForgotDialog) {
                        AlertDialog(
                            onDismissRequest = { showForgotDialog = false },
                            title = { Text("Reset PIN / Password", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Enter your registered email/phone number and your new desired PIN / Password.", style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = forgotEmail,
                                        onValueChange = { forgotEmail = it; forgotMessage = "" },
                                        label = { Text("Email or Phone Number") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = forgotNewPin,
                                        onValueChange = { forgotNewPin = it; forgotMessage = "" },
                                        label = { Text("New PIN / Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    if (forgotMessage.isNotEmpty()) {
                                        Text(forgotMessage, color = if (forgotMessage.startsWith("Success")) Color(0xFF10B981) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (forgotEmail.isEmpty() || forgotNewPin.isEmpty()) {
                                            forgotMessage = "Please fill in all fields."
                                        } else {
                                            coroutineScope.launch {
                                                val (success, msg) = onResetPassword(forgotEmail, forgotNewPin)
                                                if (success) {
                                                    forgotMessage = "Success: $msg"
                                                    emailOrPhone = forgotEmail
                                                    pinOrPassword = forgotNewPin
                                                    kotlinx.coroutines.delay(1500)
                                                    showForgotDialog = false
                                                    forgotEmail = ""
                                                    forgotNewPin = ""
                                                    forgotMessage = ""
                                                } else {
                                                    forgotMessage = "Error: $msg"
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("Reset PIN")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showForgotDialog = false; forgotEmail = ""; forgotNewPin = ""; forgotMessage = "" }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Role Tabs Selector
                    Column {
                        Text(
                            text = "Login Role",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserRole.values().forEach { role ->
                                val isSelected = selectedRole == role
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedRole = role },
                                    label = {
                                        Text(
                                            text = role.name.replace("_", " "),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (emailOrPhone.isEmpty() || pinOrPassword.isEmpty()) {
                                errorMessage = "Please enter both credentials."
                            } else {
                                coroutineScope.launch {
                                    val (success, msg) = onLoginSuccess(emailOrPhone, pinOrPassword, selectedRole)
                                    if (!success) {
                                        errorMessage = msg
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dev quick links card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🛠️ SANDBOX QUICK ACCESS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    emailOrPhone = "ndayizeyebenon@gmail.com"
                                    pinOrPassword = "123"
                                    selectedRole = UserRole.MEMBER
                                    coroutineScope.launch {
                                        val (success, msg) = onLoginSuccess("ndayizeyebenon@gmail.com", "123", UserRole.MEMBER)
                                        if (!success) {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Benon (Member)", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    emailOrPhone = "admin@sacco.org"
                                    pinOrPassword = "123"
                                    selectedRole = UserRole.ADMIN
                                    coroutineScope.launch {
                                        val (success, msg) = onLoginSuccess("admin@sacco.org", "123", UserRole.ADMIN)
                                        if (!success) {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Admin", fontSize = 11.sp)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    emailOrPhone = "superadmin@sacco.org"
                                    pinOrPassword = "123"
                                    selectedRole = UserRole.SUPER_ADMIN
                                    coroutineScope.launch {
                                        val (success, msg) = onLoginSuccess("superadmin@sacco.org", "123", UserRole.SUPER_ADMIN)
                                        if (!success) {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Super Admin", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    emailOrPhone = "member2@sacco.org"
                                    pinOrPassword = "123"
                                    selectedRole = UserRole.GUARANTOR
                                    coroutineScope.launch {
                                        val (success, msg) = onLoginSuccess("member2@sacco.org", "123", UserRole.GUARANTOR)
                                        if (!success) {
                                            errorMessage = msg
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Guarantor Test", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.testTag("apply_membership_button")
            ) {
                Text(
                    text = "Apply for SACCO Membership",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
