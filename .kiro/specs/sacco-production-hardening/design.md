# Design — SACCO Manager Production Hardening & Firebase Integration

## Architecture Overview

The app follows an offline-first hybrid architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                    │
│   LoginScreen  RegistrationScreen  MemberDashboard  ...     │
│                    SaccoViewModel                           │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                   SaccoRepository                           │
│   (Single source of truth — delegates to Room + Firebase)   │
└─────────┬──────────────────────────────────────┬────────────┘
          │                                      │
┌─────────▼──────────┐              ┌────────────▼────────────┐
│   Room Database     │              │   Firebase Layer         │
│   (Local / Offline) │              │                         │
│   SaccoDatabase     │              │  FirebaseAuthManager    │
│   15 DAOs           │              │  FirebaseStorageManager │
│                     │              │  SaccoSyncEngine        │
└─────────────────────┘              │  (Firestore + RTDB)     │
                                     └─────────────────────────┘
```

### Key Architectural Decisions

1. **Firebase Auth owns credentials** — local `SaccoUser.password` is eliminated. `SaccoUser` stores `firebaseUid` instead. Role and profile data stay in Room and Firestore.

2. **Room is the UI data source** — all Composables collect from Room `Flow`s. Firebase writes are fire-and-forget side effects that eventually replicate back via real-time listeners.

3. **SaccoViewModel** sits between the repository and UI. It survives rotation and owns all coroutine scopes that were previously in `MainContent`.

4. **Firebase Storage** handles all binary assets (receipts, profile photos). URLs are stored as strings in Room and Firestore.

---

## Component Design

### FirebaseAuthManager

Location: `app/src/main/java/com/example/network/FirebaseAuthManager.kt`

```kotlin
object FirebaseAuthManager {
    private val auth = FirebaseAuth.getInstance()
    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun register(email: String, password: String): Result<FirebaseUser>
    suspend fun login(email: String, password: String): Result<FirebaseUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    fun logout()
    fun isLoggedIn(): Boolean
}
```

- Uses `suspendCoroutine` to bridge Firebase `Task<T>` callbacks to coroutines
- `register()` creates a Firebase Auth account, then sets custom claims (role) via Firestore document write — full Cloud Function claim setting is a post-v1 enhancement
- `login()` returns a `Result` — callers handle `Result.failure` to show error messages
- Session persistence is automatic via Firebase SDK; `isLoggedIn()` checks `currentUser != null`

### FirebaseStorageManager

Location: `app/src/main/java/com/example/network/FirebaseStorageManager.kt`

```kotlin
object FirebaseStorageManager {
    private val storage = FirebaseStorage.getInstance()

    // Returns download URL on success
    suspend fun uploadFile(uri: Uri, storagePath: String): Result<String>

    // Upload with progress
    fun uploadFileWithProgress(uri: Uri, storagePath: String): Flow<UploadState>

    fun deleteFile(storagePath: String)
}

sealed class UploadState {
    data class Progress(val fraction: Float) : UploadState()
    data class Success(val downloadUrl: String) : UploadState()
    data class Error(val exception: Exception) : UploadState()
}
```

Storage path conventions:
- Payment receipts: `receipts/{memberId}/{receiptNumber}.jpg`
- Profile photos: `profiles/{memberId}/photo.jpg`
- Signatures: `profiles/{memberId}/signature.jpg`

File size validation (5MB max) happens before the upload is initiated.

### SaccoViewModel

Location: `app/src/main/java/com/example/ui/viewmodel/SaccoViewModel.kt`

```kotlin
class SaccoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SaccoRepository  // initialized with DB

    // Auth state
    val loggedInUser: StateFlow<SaccoUser?>
    val activeRole: StateFlow<UserRole>
    val isSessionRestored: StateFlow<Boolean>  // true once Firebase Auth checked

    // Data streams (delegated from repository flows)
    val profilesList: StateFlow<List<MemberProfile>>
    val allPayments: StateFlow<List<SavingsPayment>>
    val allLoans: StateFlow<List<LoanApplication>>
    val allProducts: StateFlow<List<LoanProduct>>
    val auditLogs: StateFlow<List<AuditLog>>
    val savingsRule: StateFlow<SavingsRule>
    val allExpenses: StateFlow<List<SaccoExpense>>
    val allUsers: StateFlow<List<SaccoUser>>
    val allSavingsPlans: StateFlow<List<SavingsPlan>>
    val allReferrals: StateFlow<List<MemberReferral>>
    val isOnline: StateFlow<Boolean>
    val isSyncing: StateFlow<Boolean>

    // Actions
    fun login(email: String, password: String, role: UserRole)
    fun logout()
    fun register(user: SaccoUser, profile: MemberProfile)
    fun submitPayment(...)
    fun submitLoanApplication(...)
    fun repayLoan(...)
    fun verifyPayment(...)
    fun verifyLoan(...)
    fun approveGuarantee(...)
    fun runReminderSweep()
    fun triggerFirebaseBackup()
    // ... all other actions currently in MainContent
}
```

### SaccoRepository — Completed Functions

#### `repayLoan()`

```
1. Fetch LoanApplication by applicationId
2. If app is null or status != DISBURSED → throw IllegalStateException
3. Calculate interest portion: interestPortion = amount * (app.interestRate / (100 * app.repaymentPeriodMonths))
4. Calculate principal portion: principalPortion = amount - interestPortion
5. Detect overpayment: overpay = max(0, amount - app.outstandingBalance)
6. Clamp repayment to outstanding balance
7. Insert LoanRepayment record with installment details
8. Update LoanApplication: principalPaid += principal, interestPaid += interest, outstandingBalance -= clamped amount
9. If outstandingBalance <= 0 → set status = COMPLETED, notify member
10. Handle overpayment:
    - "SAVINGS" → submitSavingsPayment for the overpay amount
    - "NEXT_LOAN" → find nextLoanId loan and apply overpay toward its balance
    - "NONE" → ignore (real SACCO would issue a refund)
11. Log audit, enqueue sync, send notifications
```

#### `runAutomatedReminders()`

```
1. Get current date
2. Fetch all SavingsPlans
3. For each plan:
   a. Parse nextDueDate
   b. If (dueDate - today) <= reminderDaysBefore AND plan.enableInApp → insertNotification
   c. If plan.enableEmail → mark for email queue (SMS/email sending is a stub returning true)
4. Return list of MemberIds that were reminded
```

#### `executeGoogleSheetsBackup()`

```
1. Log audit that backup was triggered
2. Call syncEngine.syncAllToFirebase(payments, loans, profiles, users)
3. Log completion
// Google Sheets API integration is a post-v1 feature;
// this function now delegates to Firebase as the canonical backup target
```

#### `seedTestData()`

```
1. Check DataStore for "seed_completed" boolean flag
2. If flag is true → return immediately (already seeded)
3. Check Room userDao.getAllUsersFlow().firstOrNull()?.size — if > 0 → set flag and return
4. Insert test users, profiles, savings rule, loan products
5. Set DataStore "seed_completed" = true
```

### SaccoSyncEngine — Completed Functions

#### `monitorNetworkConnectivity()` completion

```kotlin
val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        _isOnline.value = true
        triggerSync() // flush pending queue when connection restored
    }
    override fun onLost(network: Network) {
        _isOnline.value = false
    }
    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
connectivityManager.registerNetworkCallback(request, callback)
```

---

## Firebase Integration Design

### Authentication Flow

```
App Launch
    │
    ├── FirebaseAuth.currentUser != null?
    │       YES → query Room for SaccoUser by firebaseUid
    │             → restore session, skip LoginScreen
    │       NO  → show LoginScreen
    │
LoginScreen "Log In"
    │
    ├── FirebaseAuthManager.login(email, password)
    │       Success → FirebaseUser obtained
    │               → query Room SaccoUser where id == email
    │               → verify role matches selected role
    │               → set loggedInUser in ViewModel
    │               → navigate to dashboard
    │       Failure → show error from Firebase (wrong password, no account, etc.)
    │
LoginScreen "Apply for Membership"
    │
    ├── RegistrationScreen filled out
    │       → FirebaseAuthManager.register(email, password)
    │               Success → FirebaseUser created
    │                       → repository.registerUser(saccoUser, memberProfile)
    │                       → SaccoUser.firebaseUid = firebaseUser.uid
    │                       → sync to Firestore
    │       Failure → show Firebase error (email already in use, weak password, etc.)
```

### Storage Upload Flow

```
Member taps "Upload Receipt" in SavingsTimelineScreen
    │
    ├── ActivityResultLauncher opens image picker
    │       → Uri returned
    │
    ├── Validate: file size ≤ 5MB
    │       FAIL → show "File too large" Snackbar
    │
    ├── FirebaseStorageManager.uploadFileWithProgress(uri, "receipts/{memberId}/{receipt}.jpg")
    │       → collect UploadState flow
    │       → show LinearProgressIndicator
    │       → on Success: store downloadUrl in receiptImageUrl
    │       → on Error: show error Snackbar, allow retry
    │
    └── SavingsPayment submitted to Room/Firestore with receiptImageUrl = downloadUrl
```

### Firestore Document Structure

```
/users_registration/{user_<email>}
  id: String (email)
  firebaseUid: String
  name: String
  role: String (enum as string)
  status: String (enum as string)
  membershipNumber: String

/member_profiles/{profile_<memberId>}
  memberId: String
  fullName: String
  nationalId: String
  profilePhotoUrl: String (Firebase Storage URL)
  signatureUrl: String (Firebase Storage URL)
  ... (all MemberProfile fields)

/savings_payments/{payment_<receiptNumber>}
  memberId: String
  amountPaid: Double
  receiptImageUrl: String (Firebase Storage URL)
  status: String
  ... (all SavingsPayment fields)

/loan_applications/{loan_<id>}
  ... (all LoanApplication fields)

/receipts/  ← Firebase Storage bucket path (not Firestore)
  {memberId}/
    {receiptNumber}.jpg

/profiles/  ← Firebase Storage bucket path
  {memberId}/
    photo.jpg
    signature.jpg
```

### Firestore Security Rules — Updated Design

The updated rules replace the current `getUserData()` lookup with a simpler, more performant approach that uses custom token claims for roles:

```
function isAdmin() {
  return isAuthenticated() && (
    request.auth.token.get('role', '') == 'ADMIN' ||
    request.auth.token.get('role', '') == 'SUPER_ADMIN'
  );
}
```

Until custom claims are set by a Cloud Function, the rules fall back to reading the user document. This is documented in the rules as a two-phase rollout.

### Storage Security Rules

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    
    // Payment receipts — member can write their own, admin can read all
    match /receipts/{memberId}/{fileName} {
      allow read: if request.auth != null && (
        request.auth.uid == memberId || 
        request.auth.token.get('role', '') == 'ADMIN' ||
        request.auth.token.get('role', '') == 'SUPER_ADMIN'
      );
      allow write: if request.auth != null &&
        request.auth.uid == memberId &&
        request.resource.size < 5 * 1024 * 1024 &&
        request.resource.contentType.matches('image/.*');
    }
    
    // Profile documents — member can write their own photo/signature
    match /profiles/{memberId}/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null &&
        request.auth.uid == memberId &&
        request.resource.size < 5 * 1024 * 1024 &&
        request.resource.contentType.matches('image/.*');
    }
  }
}
```

---

## Data Model Changes

### SaccoUser

Add field:
```kotlin
val firebaseUid: String = ""
```

Remove field:
```kotlin
val password: String = "123"   // REMOVED — Firebase Auth owns credentials
```

This requires a Room migration (version 6 → 7):
```kotlin
Migration(6, 7) {
    it.execSQL("ALTER TABLE sacco_users ADD COLUMN firebaseUid TEXT NOT NULL DEFAULT ''")
    it.execSQL("CREATE TABLE sacco_users_new (id TEXT PRIMARY KEY NOT NULL, email TEXT NOT NULL, phone TEXT NOT NULL, name TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE', membershipNumber TEXT NOT NULL DEFAULT '', firebaseUid TEXT NOT NULL DEFAULT '')")
    it.execSQL("INSERT INTO sacco_users_new SELECT id, email, phone, name, role, status, membershipNumber, '' FROM sacco_users")
    it.execSQL("DROP TABLE sacco_users")
    it.execSQL("ALTER TABLE sacco_users_new RENAME TO sacco_users")
}
```

### AuditLog

Change:
```kotlin
val ipAddress: String = ""   // was "192.168.1.100"
```

No schema change needed (same column type, just default value change).

---

## File Structure Changes

```
app/src/main/java/com/example/
├── MainActivity.kt                        (updated — uses ViewModel, checks Firebase Auth session)
├── data/
│   ├── SaccoModels.kt                     (updated — SaccoUser adds firebaseUid, removes password)
│   ├── SaccoDatabase.kt                   (updated — version 7, adds migration)
│   ├── SaccoDatabaseMigrations.kt         (NEW — Migration 6→7)
│   ├── SaccoRepository.kt                 (updated — completed functions, Firebase Auth calls)
│   └── DividendEngine.kt                  (unchanged)
├── network/
│   ├── FirebaseAuthManager.kt             (NEW)
│   ├── FirebaseStorageManager.kt          (NEW)
│   ├── GeminiApiClient.kt                 (updated — model name fix)
│   ├── SaccoNetworkClient.kt              (updated — remove CLEARTEXT in release)
│   └── SaccoSyncEngine.kt                 (updated — completed network callback, Firestore merge writes)
├── ui/
│   ├── viewmodel/
│   │   └── SaccoViewModel.kt              (NEW)
│   ├── screens/
│   │   ├── LoginScreen.kt                 (updated — Firebase Auth, debug guard on sandbox card)
│   │   ├── RegistrationScreen.kt          (updated — Firebase Auth register, Storage upload)
│   │   ├── SavingsTimelineScreen.kt       (updated — Storage receipt upload)
│   │   ├── MemberDashboardScreen.kt       (updated — Storage photo upload)
│   │   └── ... (other screens unchanged structurally)
│   └── theme/
│       └── PermissionHelper.kt            (NEW — version-aware permission utility)
└── ...

app/src/main/res/xml/
└── network_security_config.xml            (NEW)

app/src/main/AndroidManifest.xml           (updated — permissions, network security config)

firestore.rules                            (updated — auth UID alignment)
storage.rules                              (NEW)
firebase.json                              (updated — includes storage rules)

gradle/libs.versions.toml                  (updated — version fixes, new Firebase deps)
app/build.gradle.kts                       (updated — version fixes, applicationId fix, new deps)
```

---

## Build Configuration Corrections

### libs.versions.toml changes

| Key | Current (broken) | Corrected |
|-----|-----------------|-----------|
| `agp` | `9.1.1` | `8.7.3` |
| `kotlin` | `2.2.10` | `2.1.0` |
| `googleDevtoolsKsp` | `2.3.5` | `2.1.0-1.0.29` |
| `composeBom` | `2024.09.00` | `2024.12.01` |
| `kotlinxCoroutinesAndroid` | `1.10.2` | `1.9.0` |
| `kotlinxCoroutinesCore` | `1.10.2` | `1.9.0` |
| `kotlinxCoroutinesTest` | `1.10.2` | `1.9.0` |

New entries:
```toml
firebaseAuth = "23.1.0"
firebaseStorage = "21.0.1"
```

### build.gradle.kts changes

```kotlin
// compileSdk
compileSdk = 35    // was: compileSdk { version = release(36) ... }
targetSdk = 35

// applicationId
applicationId = "com.example"   // was: "com.swibztech.saccoapp"

// New dependencies
implementation(libs.firebase.auth)
implementation(libs.firebase.storage)
implementation(libs.androidx.datastore.preferences)  // uncomment existing line
```

---

## Security Checklist

| Item | Approach |
|------|----------|
| Password storage | Eliminated — Firebase Auth owns credentials |
| Session persistence | Firebase Auth SDK handles token refresh |
| API key exposure | `.env` + Secrets Gradle Plugin (existing) |
| Network security | TLS 1.3 + network_security_config.xml |
| Firestore access | Auth-gated rules per collection |
| Storage access | Auth-gated rules per user path |
| Sandbox credentials | `BuildConfig.DEBUG` guard |
| Certificate pinning | Placeholder — document where real certs go |

---

## Rollout Order (Implementation Sequence)

1. **Build fixes first** — fix versions, applicationId, add INTERNET permission (REQ-1, REQ-2, REQ-3). App must build before anything else.
2. **Complete truncated code** — `repayLoan`, `monitorNetworkConnectivity`, `seedTestData` guard (REQ-8). Prevents runtime crashes.
3. **Fix Gemini model name** (REQ-9). One-line change, high value.
4. **Firebase Auth integration** (REQ-4, REQ-5). Core identity change — all subsequent features depend on having a real FirebaseUser.
5. **Firebase Storage** (REQ-6). Upload flows in Registration and SavingsTimeline.
6. **Firestore alignment** (REQ-7, REQ-14). Rules update, merge writes, enum handling.
7. **ViewModel extraction** (REQ-16). Refactor — no new features, but cleans up MainActivity.
8. **Security hardening** (REQ-10, REQ-11, REQ-12, REQ-13). Debug guard, migration strategy, permissions, network config.
9. **Cleanup** (REQ-15). Audit log IP address.
