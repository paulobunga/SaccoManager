# SACCO Manager — Production Hardening & Firebase Integration

## Overview

This spec covers all work required to transform SACCO Manager from a functional prototype into a production-ready Android application. It addresses critical security gaps, broken functionality, incomplete code, and the full integration of Firebase Authentication, Firebase Storage (for file/receipt uploads), and Cloud Firestore (as the primary cloud data store).

The app is a Savings & Credit Cooperative management system targeting Ugandan SACCOs. Currency is UGX. It has four user roles: MEMBER, GUARANTOR, ADMIN, SUPER_ADMIN. The local Room database serves as the offline-first source of truth, with Firebase as the real-time cloud sync layer.

---

## Requirements

### REQ-1: Android Manifest — Missing Permissions

**Priority: Critical**

**User Story:** As a developer, I need the app to declare all required Android permissions so networking, camera, and storage features work on all supported API levels.

**Acceptance Criteria:**
- [ ] `android.permission.INTERNET` is declared in AndroidManifest.xml
- [ ] `android.permission.ACCESS_NETWORK_STATE` is declared
- [ ] `android.permission.CAMERA` remains declared (camera feature planned)
- [ ] `android.permission.READ_MEDIA_IMAGES` is declared for API 33+
- [ ] `android.permission.READ_EXTERNAL_STORAGE` is scoped to `maxSdkVersion="32"` for backward compatibility
- [ ] `android.permission.WRITE_EXTERNAL_STORAGE` is declared scoped to `maxSdkVersion="28"` for PDF/CSV export on older devices
- [ ] `android.permission.USE_BIOMETRIC` and `android.permission.USE_FINGERPRINT` are declared for future biometric auth
- [ ] The `application` block sets `android:usesCleartextTraffic="false"` (enforce HTTPS only)
- [ ] `android:networkSecurityConfig` points to a network security config XML that pins the production domain

---

### REQ-2: Build Configuration — Version Corrections

**Priority: Critical**

**User Story:** As a developer, I need valid, stable dependency versions so the project builds successfully without relying on pre-release or non-existent artifacts.

**Acceptance Criteria:**
- [ ] `compileSdk` is set to a valid released API level (35)
- [ ] `targetSdk` is set to 35
- [ ] AGP version is a stable release (8.7.x or latest stable)
- [ ] Kotlin version is a stable release (2.1.x or latest stable)
- [ ] KSP version matches the Kotlin version exactly
- [ ] `kotlinx-coroutines` version is pinned to a stable release (1.9.x)
- [ ] All dependency version refs in `libs.versions.toml` are updated accordingly
- [ ] `firebase-storage` is added to `libs.versions.toml` and `build.gradle.kts`
- [ ] `firebase-auth` is added to `libs.versions.toml` and `build.gradle.kts`
- [ ] `androidx.datastore.preferences` is uncommented and added (needed for auth token persistence)

---

### REQ-3: Package Name Consistency

**Priority: Critical**

**User Story:** As a developer, I need a single consistent package name throughout the project so Firebase connects to the correct app.

**Acceptance Criteria:**
- [ ] `applicationId` in `build.gradle.kts` is changed from `com.swibztech.saccoapp` to `com.example` to match `google-services.json` and all source files
- [ ] All source files continue using `package com.example`
- [ ] `BuildConfig.APPLICATION_ID` resolves to `com.example`
- [ ] `google-services.json` package name matches the applicationId

---

### REQ-4: Firebase Authentication Integration

**Priority: Critical**

**User Story:** As a user, I want my credentials to be verified by Firebase Authentication so the app has a real, secure identity system backed by Firebase rather than plaintext password comparison in a local database.

**Acceptance Criteria:**
- [ ] Firebase Auth SDK is integrated (`firebase-auth` dependency added)
- [ ] A `FirebaseAuthManager` singleton/object is created in the `network` package
- [ ] Registration creates a Firebase Auth account using email/password via `createUserWithEmailAndPassword`
- [ ] Login authenticates via `signInWithEmailAndPassword` before checking the local Room DB for role/profile data
- [ ] Password reset uses `sendPasswordResetEmail` for real email-based reset (replaces the current in-app plaintext reset)
- [ ] The existing in-app "Forgot PIN" dialog is updated to call Firebase password reset and show an email-sent confirmation rather than directly updating the DB
- [ ] On successful Firebase Auth login, the Firebase UID is stored alongside the local `SaccoUser` record
- [ ] Session persistence is handled by Firebase Auth (user stays logged in across app restarts)
- [ ] `MainActivity` checks `FirebaseAuth.getInstance().currentUser` on startup to restore session without re-login
- [ ] Logout calls `FirebaseAuth.getInstance().signOut()` in addition to clearing in-memory state
- [ ] The `SaccoUser` data class gains an optional `firebaseUid: String = ""` field
- [ ] Phone number authentication is scaffolded but not required for v1 (email/password is sufficient)

---

### REQ-5: Password Security — Hashing

**Priority: Critical**

**User Story:** As a security-conscious operator, I need member passwords to never be stored in plaintext so a database leak does not expose credentials.

**Acceptance Criteria:**
- [ ] Plaintext password storage is eliminated
- [ ] Passwords are hashed using a secure one-way algorithm before being stored in Room DB
- [ ] Since Android doesn't include BCrypt in the standard SDK, use `java.security.MessageDigest` SHA-256 with a per-user salt stored separately, OR defer entirely to Firebase Auth (preferred — passwords are never stored locally at all)
- [ ] The preferred approach: Firebase Auth owns all credentials; local `SaccoUser.password` field is replaced with `firebaseUid`; local password comparison logic is removed from `onLoginSuccess`
- [ ] The hardcoded `password = "123"` default in the `SaccoUser` model is removed
- [ ] Seed test data uses Firebase Auth `createUserWithEmailAndPassword` or skips local password storage entirely
- [ ] The `resetPassword` function in `SaccoRepository` is updated to use Firebase Auth password reset instead of overwriting the `password` field

---

### REQ-6: Firebase Storage — File Upload Integration

**Priority: High**

**User Story:** As a member, I want to upload my savings payment receipt photo and registration documents so admins can verify them without manual document exchange.

**Acceptance Criteria:**
- [ ] Firebase Storage SDK is added (`firebase-storage` dependency)
- [ ] A `FirebaseStorageManager` object/class is created in the `network` package
- [ ] `FirebaseStorageManager` exposes a `uploadFile(uri: Uri, path: String): Result<String>` suspend function that returns the download URL on success
- [ ] Storage paths follow the convention: `receipts/{memberId}/{receiptNumber}.jpg` for payment receipts
- [ ] Storage paths for registration docs follow: `profiles/{memberId}/photo.jpg` and `profiles/{memberId}/signature.jpg`
- [ ] Upload progress is exposed as a `Flow<Float>` (0.0 to 1.0)
- [ ] On successful upload, the returned download URL is stored in `SavingsPayment.receiptImageUrl` or `MemberProfile.profilePhotoUrl`/`signatureUrl`
- [ ] `SavingsTimelineScreen` receipt upload button triggers the file picker, then calls `FirebaseStorageManager.uploadFile()`, and stores the result URL in the payment
- [ ] `RegistrationScreen` photo/signature capture buttons trigger the file picker and upload to Firebase Storage
- [ ] Coil image loading (already a dependency) is used to display uploaded receipt thumbnails in `SavingsTimelineScreen` and admin verification view
- [ ] Upload errors are surfaced to the user with a dismissible snackbar
- [ ] Firebase Storage security rules restrict uploads: members can only write to their own `receipts/{memberId}/` and `profiles/{memberId}/` paths; admins can read all
- [ ] Storage rules are added to `firebase.json` and a `storage.rules` file is created in the project root

---

### REQ-7: Firestore as Primary Cloud Store

**Priority: High**

**User Story:** As an admin, I want all SACCO data to be reliably persisted in Firestore so data is available across devices and survives app reinstalls.

**Acceptance Criteria:**
- [ ] All 13 Firestore collections already defined in `SaccoSyncEngine` are confirmed and documented
- [ ] `SaccoSyncEngine.pushToFirebase()` is verified to correctly write all entity types (existing implementation reviewed)
- [ ] Firestore document IDs use stable, deterministic keys (e.g. `memberId`, `receiptNumber`) — not auto-generated random IDs
- [ ] The `_syncTimestamp`, `_actionType`, `_id` metadata fields added by the sync engine are cleaned from documents before writing (or stored in a separate subcollection) so they don't pollute data reads
- [ ] Firestore listeners in `setupFirestoreListeners()` correctly handle enum deserialization (enums are stored as strings; Moshi must deserialize them back correctly)
- [ ] `SaccoSyncEngine` correctly handles `Long` vs `Int` type coercion for Firestore numeric fields (Firestore returns all numbers as `Long`; Room expects `Int` for some fields)
- [ ] On first app launch after Firebase is configured, the app performs an initial full sync from Firestore to Room so existing cloud data appears locally
- [ ] The `syncAllToFirebase()` function in `SaccoSyncEngine` is exposed as the manual "backup sweep" trigger from the Reports screen
- [ ] Firestore security rules in `firestore.rules` are updated to be compatible with Firebase Auth UIDs (the `isOwner` function uses `request.auth.uid`)
- [ ] Write conflict resolution: Firestore `set()` with merge is used instead of `set()` to avoid overwriting fields not present in the local payload

---

### REQ-8: Complete Truncated/Stub Functions

**Priority: Critical**

**User Story:** As a developer, I need all functions to have complete implementations so the app doesn't crash at runtime when those code paths are reached.

**Acceptance Criteria:**
- [ ] `SaccoRepository.repayLoan()` is fully implemented — the function is cut off mid-line and must be completed including: fetching the loan, calculating principal/interest split, recording the `LoanRepayment` installment, updating `LoanApplication.outstandingBalance`, handling the `overpaymentAction` parameter (SAVINGS = credit excess to savings, NEXT_LOAN = apply to another loan, NONE = return), marking loan COMPLETED when balance reaches zero
- [ ] `SaccoRepository.runAutomatedReminders()` is fully implemented — iterates all active members with a `SavingsPlan`, finds those with upcoming due dates within `reminderDaysBefore` days, inserts a `SaccoNotification` for each, returns the list of notified members
- [ ] `SaccoRepository.executeGoogleSheetsBackup()` is either implemented (using a webhook/API) or replaced with a call to `syncEngine.syncAllToFirebase()` with a clear comment explaining the substitution
- [ ] `SaccoSyncEngine.monitorNetworkConnectivity()` network callback registration is completed — the `connectivityManager.registerNetworkCallback(request, callback)` call must be finished with a proper `NetworkCallback` that sets `_isOnline` to `true` on `onAvailable` and `false` on `onLost`
- [ ] `SaccoSyncEngine.pushToCloudGateway()` either actually executes the OkHttp request or is clearly documented as a simulation stub with a `TODO` comment — currently it silently builds a request and discards it while claiming success
- [ ] `SaccoRepository.seedTestData()` is guarded — it must check if data already exists before inserting, using a flag (DataStore preference or checking user count) so it only seeds once on a fresh install, never on subsequent launches

---

### REQ-9: Gemini API — Correct Model Name

**Priority: Critical**

**User Story:** As an admin, I want the AI loan assessment to actually call a valid Gemini model so AI credit scoring works.

**Acceptance Criteria:**
- [ ] `GeminiApiClient.DEFAULT_MODEL` is changed from `"gemini-3.5-flash"` to `"gemini-2.0-flash"` (or `"gemini-1.5-flash"` as fallback)
- [ ] The model name is validated against the Gemini API models list endpoint on first use, or documented clearly in a comment
- [ ] If the model returns a 404, the client falls back to `"gemini-1.5-flash"` before entering offline mode
- [ ] The `GEMINI_API_KEY` placeholder detection logic remains (`"MY_GEMINI_API_KEY"` check triggers offline mode)

---

### REQ-10: Sandbox Quick-Login — Debug Guard

**Priority: High**

**User Story:** As a security auditor, I need the sandbox quick-login buttons to be removed from production builds so test credentials are not accessible to end users.

**Acceptance Criteria:**
- [ ] The "SANDBOX QUICK ACCESS" card in `LoginScreen.kt` is wrapped in a `if (BuildConfig.DEBUG)` block
- [ ] In release builds the card is not rendered
- [ ] Hardcoded test emails (`ndayizeyebenon@gmail.com`, `admin@sacco.org`, etc.) do not appear in release APKs

---

### REQ-11: Database Migration Strategy

**Priority: High**

**User Story:** As a user upgrading the app, I need my data to be preserved across app updates so I don't lose savings history and loan records.

**Acceptance Criteria:**
- [ ] `fallbackToDestructiveMigration()` is replaced with `fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)` to only destroy data on very old schema versions, OR proper `Migration` objects are written for versions 5→6
- [ ] A `SaccoDatabaseMigrations.kt` file is created containing all future migration definitions
- [ ] Going forward, any schema change increments the version and adds a corresponding `Migration` object
- [ ] The README documents the migration policy

---

### REQ-12: Storage Permission — API Level Handling

**Priority: High**

**User Story:** As a user on Android 13+, I want receipt image uploads to work without requesting deprecated storage permissions.

**Acceptance Criteria:**
- [ ] `READ_EXTERNAL_STORAGE` in the manifest is gated with `android:maxSdkVersion="32"`
- [ ] `READ_MEDIA_IMAGES` is declared for API 33+
- [ ] Runtime permission request logic in the UI checks `Build.VERSION.SDK_INT` and requests the appropriate permission
- [ ] A `PermissionHelper.kt` utility in the `ui` package handles the version-aware permission request and returns a consistent callback

---

### REQ-13: Network Security Configuration

**Priority: Medium**

**User Story:** As a security auditor, I need the app to enforce HTTPS-only communication and have a proper network security config rather than relying on default settings.

**Acceptance Criteria:**
- [ ] A `res/xml/network_security_config.xml` file is created
- [ ] The config pins the `api.sacco.org` domain (placeholder pins updated with real SHA-256 hashes, or removed and replaced with a comment to add before production)
- [ ] `cleartextTrafficPermitted` is set to `false` globally
- [ ] `localhost` and `10.0.2.2` are whitelisted for debug builds only (needed for local development/emulator)
- [ ] `AndroidManifest.xml` references the network security config via `android:networkSecurityConfig="@xml/network_security_config"`
- [ ] The `CLEARTEXT` fallback in `SaccoNetworkClient.connectionSpecs` is removed or guarded behind a `BuildConfig.DEBUG` check

---

### REQ-14: Firestore Security Rules — Firebase Auth Alignment

**Priority: High**

**User Story:** As a security architect, I need Firestore rules to use real Firebase Auth UIDs so only authenticated users can access their own data.

**Acceptance Criteria:**
- [ ] `firestore.rules` is updated so `isOwner(memberId)` resolves correctly using Firebase Auth UIDs
- [ ] Custom claims (`role`) are set on Firebase Auth tokens during registration so `request.auth.token.role` works in rules
- [ ] A Cloud Function (or server-side process) sets custom claims when a user's role is assigned — OR the rules fall back to reading the role from the `users_registration` Firestore document
- [ ] All 13 collections have explicit read/write rules
- [ ] The `misc_transactions` collection rule is tightened (currently too permissive)
- [ ] `audit_logs` collection is added with admin-only read, write-once-from-any-authenticated-user rule
- [ ] `sacco_notifications` rules allow users to read their own notifications and mark as read
- [ ] Storage rules file (`storage.rules`) is created as described in REQ-6

---

### REQ-15: Audit Log — Remove Hardcoded IP

**Priority: Low**

**User Story:** As an auditor, I need audit log entries to contain accurate metadata rather than a hardcoded placeholder IP address.

**Acceptance Criteria:**
- [ ] The `ipAddress = "192.168.1.100"` default in `AuditLog` is replaced with an empty string or a utility function that retrieves the device's actual local IP
- [ ] `SaccoRepository.logAudit()` captures the device IP at log time using `NetworkInterface` enumeration, with a fallback to `"UNKNOWN"` if unavailable
- [ ] The `AuditLog` entity schema change is handled via a proper Room migration

---

### REQ-16: App Architecture — ViewModel Extraction

**Priority: Medium**

**User Story:** As a developer maintaining this codebase, I need business logic extracted into ViewModels so screens don't hold coroutine scopes directly and configuration changes (e.g. rotation) don't cancel in-flight operations.

**Acceptance Criteria:**
- [ ] A `SaccoViewModel` is created in a new `ui/viewmodel` package
- [ ] `SaccoViewModel` holds the `SaccoRepository` reference and exposes all StateFlows currently collected in `MainContent`
- [ ] All `scope.launch { repository.x() }` calls in `MainContent` are moved to `SaccoViewModel` functions
- [ ] `MainContent` receives the ViewModel via `viewModel()` delegate
- [ ] The ViewModel survives configuration changes (standard `ViewModel` lifecycle)
- [ ] Auth state (`loggedInUser`, `activeRole`) is managed within the ViewModel
- [ ] Navigation state (`currentScreenRoute`) may remain in the Composable or move to ViewModel — either is acceptable

---

### Non-Functional Requirements

- All new Kotlin code must compile without warnings (no `@Suppress` unless justified with a comment)
- Firebase operations must be wrapped in `try/catch` and never crash the app — graceful degradation to local mode is mandatory
- All suspend functions accessing Room must run on `Dispatchers.IO`
- Firebase callbacks must marshal results back to the coroutine context via `suspendCoroutine` or `Tasks.await()`
- No sensitive keys (API keys, Firebase config) are committed to source control — `.env` and `google-services.json` are in `.gitignore`
- Receipt images uploaded to Firebase Storage must not exceed 5MB; validation happens before upload
- The app must remain fully functional offline — Firebase unavailability must never prevent the user from submitting payments or viewing their data
