package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.data.MemberProfile
import com.example.data.MemberStatus
import com.example.data.SaccoUser
import com.example.data.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    onRegisterSubmit: (SaccoUser, MemberProfile) -> Unit,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Registration Fields
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var physicalAddress by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var bankAccount by remember { mutableStateOf("") }
    var mobileMoneyNumber by remember { mutableStateOf("") }
    var nextOfKin by remember { mutableStateOf("") }
    var referralCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var passportUploaded by remember { mutableStateOf(false) }
    var signatureUploaded by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SACCO Application Form", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Apply for Sacco Cooperative membership by filling all the required statutory information.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                if (formError.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Section 1: Basic Information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "1. Personal Information",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full Name (as on National ID)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { dob = it },
                                label = { Text("Date of Birth (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // Gender dropdown-ish simple row
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gender", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Male", "Female").forEach { g ->
                                        ElevatedFilterChip(
                                            selected = gender == g,
                                            onClick = { gender = g },
                                            label = { Text(g, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = nationalId,
                            onValueChange = { nationalId = it },
                            label = { Text("National ID Number (NIN)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Section 2: Contact Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. Contact & Addresses",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number (with Mobile Money)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = physicalAddress,
                            onValueChange = { physicalAddress = it },
                            label = { Text("Physical Residential Address") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }

                // Section 3: Professional Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "3. Occupation & Employment",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = occupation,
                            onValueChange = { occupation = it },
                            label = { Text("Occupation") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = employer,
                            onValueChange = { employer = it },
                            label = { Text("Employer / Company") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = emergencyContact,
                            onValueChange = { emergencyContact = it },
                            label = { Text("Emergency Contact (Name & Phone)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Section 4: Finance & Next of Kin
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "4. Bank Accounts & Beneficiary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = bankAccount,
                            onValueChange = { bankAccount = it },
                            label = { Text("Bank Name & Account Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = mobileMoneyNumber,
                            onValueChange = { mobileMoneyNumber = it },
                            label = { Text("Mobile Money Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = nextOfKin,
                            onValueChange = { nextOfKin = it },
                            label = { Text("Next of Kin (Beneficiary Details)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = referralCode,
                            onValueChange = { referralCode = it },
                            label = { Text("Referral Code (Optional)") },
                            placeholder = { Text("e.g., 100-REF") },
                            modifier = Modifier.fillMaxWidth().testTag("referral_code_input"),
                            singleLine = true
                        )
                    }
                }

                // Section 5: Secure Upload Verification
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "5. Biometrics & Documents",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { passportUploaded = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (passportUploaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (passportUploaded) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (passportUploaded) Icons.Default.Check else Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Passport Photo", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { signatureUploaded = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (signatureUploaded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (signatureUploaded) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (signatureUploaded) Icons.Default.Check else Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Digital Signature", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Section 6: Account Security (PIN / Password)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "6. Account Security",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Choose a PIN / Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("choose_password_input"),
                            singleLine = true
                        )
                    }
                }

                // Submit Action
                Button(
                    onClick = {
                        if (email.isEmpty() || phone.isEmpty() || fullName.isEmpty() || nationalId.isEmpty() || password.isEmpty()) {
                            formError = "Required Fields Missing: Please fill Full Name, Email, Phone, National ID, and choose a PIN / Password."
                        } else {
                            val user = SaccoUser(
                                id = email,
                                email = email,
                                phone = phone,
                                name = fullName,
                                role = UserRole.MEMBER,
                                status = MemberStatus.PENDING,
                                membershipNumber = "SACCO-PEND-${System.currentTimeMillis().toString().takeLast(4)}",
                                password = password
                            )
                            val profile = MemberProfile(
                                memberId = email,
                                membershipNumber = user.membershipNumber,
                                nationalId = nationalId,
                                fullName = fullName,
                                gender = gender,
                                dateOfBirth = dob.ifEmpty { "1990-01-01" },
                                phoneNumber = phone,
                                email = email,
                                physicalAddress = physicalAddress.ifEmpty { "Not Provided" },
                                occupation = occupation.ifEmpty { "Not Provided" },
                                employer = employer.ifEmpty { "Not Provided" },
                                emergencyContact = emergencyContact.ifEmpty { "Not Provided" },
                                bankAccount = bankAccount.ifEmpty { "Not Provided" },
                                mobileMoneyNumber = mobileMoneyNumber.ifEmpty { phone },
                                dateJoined = "2026-06-26",
                                status = MemberStatus.PENDING,
                                nextOfKin = nextOfKin.ifEmpty { "Not Provided" },
                                profilePhotoUrl = "mock_photo_uri",
                                signatureUrl = "mock_sig_uri",
                                referredByCode = referralCode
                            )
                            onRegisterSubmit(user, profile)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_registration_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit Application", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
