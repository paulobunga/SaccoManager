# Tasks — SACCO Manager Production Hardening & Firebase Integration

## Implementation Order

Tasks are ordered by dependency. Complete each group before moving to the next.

---

## Group 1: Build & Manifest Fixes (Must be done first — nothing else works until the app builds)

- [x] 1. Fix `gradle/libs.versions.toml` — correct AGP to `8.7.3`, Kotlin to `2.1.0`, KSP to `2.1.0-1.0.29`, Compose BOM to `2024.12.01`, coroutines to `1.9.0`; add `firebase-auth` and `firebase-storage` library entries
  - File: `gradle/libs.versions.toml`
  - Related: REQ-2

- [x] 2. Fix `app/build.gradle.kts` — change `compileSdk`/`targetSdk` to 35 (remove the non-standard `release()` block), fix `applicationId` to `"com.example"`, add `firebase.auth`, `firebase.storage`, and uncomment `androidx.datastore.preferences` dependencies
  - File: `app/build.gradle.kts`
  - Related: REQ-2, REQ-3

- [x] 3. Add all missing permissions to `AndroidManifest.xml` — `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_MEDIA_IMAGES` (API 33+), scope `READ_EXTERNAL_STORAGE` to `maxSdkVersion="32"`, add `WRITE_EXTERNAL_STORAGE` scoped to `maxSdkVersion="28"`, add `USE_BIOMETRIC`, `USE_FINGERPRINT`; add `android:usesCleartextTraffic="false"` to application block; add `android:networkSecurityConfig` reference
  - File: `app/src/main/AndroidManifest.xml`
  - Related: REQ-1

- [x] 4. Create `res/xml/network_security_config.xml` — global `cleartextTrafficPermitted="false"`, pin `api.sacco.org` domain (with placeholder comment for real certs), whitelist `localhost` and `10.0.2.2` for debug builds only
  - File: `app/src/main/res/xml/network_security_config.xml`
  - Related: REQ-13

---

## Group 2: One-Line Critical Fixes

- [x] 5. Fix Gemini model name in `GeminiApiClient.kt` — change `DEFAULT_MODEL` from `"gemini-3.5-flash"` to `"gemini-2.0-flash"`; add fallback to `"gemini-1.5-flash"` if the primary returns 404
  - File: `app/src/main/java/com/example/network/GeminiApiClient.kt`
  - Related: REQ-9

- [x] 6. Fix `SaccoNetworkClient.kt` — remove `ConnectionSpec.CLEARTEXT` from `connectionSpecs` list in release builds (wrap in `if (BuildConfig.DEBUG)` or remove entirely since TLS is enforced by network_security_config)
  - File: `app/src/main/java/com/example/network/SaccoNetworkClient.kt`
  - Related: REQ-13

---

## Group 3: Data Model & Database Migration

- [x] 7. Update `SaccoModels.kt` — add `firebaseUid: String = ""` to `SaccoUser`; remove `password: String = "123"` field; update `AuditLog.ipAddress` default from `"192.168.1.100"` to `""`
  - File: `app/src/main/java/com/example/data/SaccoModels.kt`
  - Related: REQ-5, REQ-15

- [x] 8. Create `SaccoDatabaseMigrations.kt` — define `MIGRATION_6_7` that: adds `firebaseUid` column to `sacco_users`, drops and recreates the table without the `password` column; documents the migration policy going forward
  - File: `app/src/main/java/com/example/data/SaccoDatabaseMigrations.kt`
  - Related: REQ-11

- [x] 9. Update `SaccoDatabase.kt` — bump version from 6 to 7; replace `fallbackToDestructiveMigration()` with `.addMigrations(MIGRATION_6_7).fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)` in the Room builder in `MainActivity.kt`
  - Files: `app/src/main/java/com/example/data/SaccoDatabase.kt`, `app/src/main/java/com/example/MainActivity.kt`
  - Related: REQ-11

---

## Group 4: Firebase Auth Integration

- [x] 10. Create `FirebaseAuthManager.kt` — implement `register(email, password): Result<FirebaseUser>`, `login(email, password): Result<FirebaseUser>`, `sendPasswordReset(email): Result<Unit>`, `logout()`, `isLoggedIn(): Boolean`; use `suspendCoroutine` to bridge Firebase Task callbacks; handle all Firebase Auth exceptions with descriptive messages (`FirebaseAuthUserCollisionException`, `FirebaseAuthInvalidCredentialsException`, etc.)
  - File: `app/src/main/java/com/example/network/FirebaseAuthManager.kt`
  - Related: REQ-4

- [x] 11. Update `SaccoRepository.registerUser()` — after inserting into Room, call `FirebaseAuthManager.register(user.email, password)` and store the returned `firebaseUid` in the `SaccoUser` record; update the Firestore sync payload to include `firebaseUid`
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-4, REQ-5

- [x] 12. Update `SaccoRepository.resetPassword()` — replace the plaintext password update with `FirebaseAuthManager.sendPasswordReset(email)`; remove the `user.copy(password = newPin)` line; update the notification message to say "A password reset link has been sent to your email"
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-4, REQ-5

- [x] 13. Update `LoginScreen.kt` — wrap the "SANDBOX QUICK ACCESS" card in `if (BuildConfig.DEBUG) { ... }`; update the `onResetPassword` callback to show "Check your email for a reset link" confirmation instead of accepting a new PIN directly in the dialog
  - File: `app/src/main/java/com/example/ui/screens/LoginScreen.kt`
  - Related: REQ-10, REQ-4

- [ ] 14. Update `MainActivity.kt` `onLoginSuccess` lambda — call `FirebaseAuthManager.login(email, password)` first; on Firebase Auth success, proceed with local role validation; on Firebase Auth failure, return the Firebase error message; add startup session check: if `FirebaseAuthManager.isLoggedIn()`, query Room for the user and restore session without re-login
  - File: `app/src/main/java/com/example/MainActivity.kt`
  - Related: REQ-4

- [~] 15. Update `MainContent` `onLogOut` — call `FirebaseAuthManager.logout()` in addition to clearing `loggedInUser` state
  - File: `app/src/main/java/com/example/MainActivity.kt`
  - Related: REQ-4

---

## Group 5: Firebase Storage Integration

- [~] 16. Create `FirebaseStorageManager.kt` — implement `uploadFile(uri: Uri, storagePath: String): Result<String>` using `suspendCoroutine`; implement `uploadFileWithProgress(uri, path): Flow<UploadState>` using `callbackFlow`; implement `deleteFile(storagePath: String)`; validate file size ≤ 5MB before upload; define `UploadState` sealed class (Progress, Success, Error)
  - File: `app/src/main/java/com/example/network/FirebaseStorageManager.kt`
  - Related: REQ-6

- [~] 17. Create `PermissionHelper.kt` — version-aware utility with `requestImagePermission(launcher: ActivityResultLauncher<String>)` that requests `READ_MEDIA_IMAGES` on API 33+ and `READ_EXTERNAL_STORAGE` on older; also provide `hasImagePermission(context: Context): Boolean`
  - File: `app/src/main/java/com/example/ui/PermissionHelper.kt`
  - Related: REQ-12

- [~] 18. Update `SavingsTimelineScreen.kt` — replace the mock `receiptImageUrl` string field with a real `ActivityResultLauncher` for the image picker; on image selection, call `FirebaseStorageManager.uploadFileWithProgress()`; show upload progress with `LinearProgressIndicator`; on success store download URL; on error show Snackbar with retry option; display uploaded receipt thumbnail using Coil `AsyncImage`
  - File: `app/src/main/java/com/example/ui/screens/SavingsTimelineScreen.kt`
  - Related: REQ-6

- [~] 19. Update `RegistrationScreen.kt` — wire the profile photo and signature capture buttons to the image picker using `ActivityResultLauncher`; on selection, upload to `profiles/{memberId}/photo.jpg` and `profiles/{memberId}/signature.jpg` via `FirebaseStorageManager`; store download URLs in the `MemberProfile` fields; show upload progress and success/error states
  - File: `app/src/main/java/com/example/ui/screens/RegistrationScreen.kt`
  - Related: REQ-6

- [~] 20. Create `storage.rules` — implement Firebase Storage security rules as designed: members can write to their own `receipts/{memberId}/` and `profiles/{memberId}/` paths; file size limit 5MB; content type restricted to `image/*`; admins can read all; add the storage rules target to `firebase.json`
  - Files: `storage.rules`, `firebase.json`
  - Related: REQ-6

---

## Group 6: Complete Truncated & Stub Functions

- [~] 21. Complete `SaccoRepository.repayLoan()` — the function is cut off at `val app = loanDao.getA...`; implement the full repayment logic: fetch application, validate status is DISBURSED, split payment into principal/interest using flat-rate amortization, insert `LoanRepayment` record, update `LoanApplication` outstanding balance, mark COMPLETED if balance ≤ 0, handle `overpaymentAction` ("SAVINGS" / "NEXT_LOAN" / "NONE"), send member notification, log audit, enqueue sync
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-8

- [~] 22. Implement `SaccoRepository.runAutomatedReminders()` — iterate all `SavingsPlan` records; for each plan parse `nextDueDate`; if days until due ≤ `reminderDaysBefore` and `enableInApp` is true, insert a `SaccoNotification`; return `List<String>` of reminded member IDs; log audit with count
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-8

- [~] 23. Implement `SaccoRepository.executeGoogleSheetsBackup()` — delegate to `syncEngine.syncAllToFirebase(payments, loans, profiles, users)` after fetching the latest data from Room flows; log audit; add a clear `// TODO: Replace with Google Sheets API integration in v2` comment
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-8

- [~] 24. Guard `SaccoRepository.seedTestData()` — add a DataStore preferences check using the `androidx.datastore.preferences` dependency; if the key `"seed_v1_complete"` is true, return immediately; otherwise seed and set the key to true; ensure seed data doesn't include plaintext passwords (use empty strings for `firebaseUid` in seed users)
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-8

- [~] 25. Complete `SaccoSyncEngine.monitorNetworkConnectivity()` — add the `NetworkCallback` implementation with `onAvailable` (sets `_isOnline = true`, calls `triggerSync()`), `onLost` (sets `_isOnline = false`), and `onCapabilitiesChanged`; call `connectivityManager.registerNetworkCallback(request, callback)`
  - File: `app/src/main/java/com/example/network/SaccoSyncEngine.kt`
  - Related: REQ-8

- [~] 26. Fix `SaccoSyncEngine.pushToCloudGateway()` — add a clear comment that this is a simulation stub; remove the misleading code that builds a `Request` but never executes it; replace with `Log.d(TAG, "Cloud Gateway: simulated push for ${entry.actionType}")` and return `true`; add `// TODO: Replace with real Kubernetes API Gateway call using SaccoNetworkClient.okHttpClient`
  - File: `app/src/main/java/com/example/network/SaccoSyncEngine.kt`
  - Related: REQ-8

- [~] 27. Fix `SaccoSyncEngine.pushToFirebase()` — change all Firestore `set(firebasePayload)` calls to `set(firebasePayload, SetOptions.merge())` to prevent overwriting existing fields; strip internal metadata keys (`_syncTimestamp`, `_actionType`, `_id`) from the document payload before writing (these can remain in the local `SyncQueueEntry` but should not pollute Firestore documents)
  - File: `app/src/main/java/com/example/network/SaccoSyncEngine.kt`
  - Related: REQ-7

---

## Group 7: ViewModel Extraction

- [~] 28. Create `SaccoViewModel.kt` — extend `AndroidViewModel`; initialize `SaccoDatabase` and `SaccoRepository` in `init`; expose all data flows as `StateFlow` using `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`; move all `scope.launch { repository.x() }` calls from `MainContent` into named ViewModel functions; manage `loggedInUser`, `activeRole`, `currentScreenRoute`, and `showRegisterForm` as `MutableStateFlow`s; handle Firebase Auth session restoration in `init`
  - File: `app/src/main/java/com/example/ui/viewmodel/SaccoViewModel.kt`
  - Related: REQ-16

- [~] 29. Refactor `MainActivity.kt` to use `SaccoViewModel` — replace direct `database`/`repository` instantiation with `viewModel<SaccoViewModel>()`; replace all `collectAsStateWithLifecycle` calls with delegating to the ViewModel's StateFlows; remove all `scope.launch { repository.x() }` lambda definitions and replace with `viewModel.functionName()`; remove `private lateinit var database` and `private lateinit var repository` fields from `MainActivity`
  - File: `app/src/main/java/com/example/MainActivity.kt`
  - Related: REQ-16

---

## Group 8: Firestore Security Rules Update

- [~] 30. Update `firestore.rules` — add explicit rules for all 13 collections (currently only 5 have explicit rules); update `isOwner()` to resolve against Firebase Auth UID; add `audit_logs` collection rule (write-once from any authenticated user, admin-only read); add `sacco_notifications` rule (users can read their own, admins can write all); add `loan_products`, `savings_rules`, `declared_dividends`, `dividend_audit_records`, `member_referrals`, `savings_plans` rules; tighten `misc_transactions` to admin-only
  - File: `firestore.rules`
  - Related: REQ-14

---

## Group 9: Final Cleanup

- [~] 31. Update `SaccoSyncEngine.cleanFirebaseMap()` — add `firebaseUid` to the string keys list; remove `password` from any key mappings; ensure enum string fields pass through correctly
  - File: `app/src/main/java/com/example/network/SaccoSyncEngine.kt`
  - Related: REQ-7

- [~] 32. Update `SaccoRepository.logAudit()` helper — replace hardcoded `ipAddress` by using `NetworkInterface.getNetworkInterfaces()` to retrieve the device's local IP; wrap in try/catch and fall back to `"UNKNOWN"`
  - File: `app/src/main/java/com/example/data/SaccoRepository.kt`
  - Related: REQ-15

- [~] 33. Update `README.md` — document the Firebase setup steps (Auth, Storage, Firestore), the `.env` setup for `GEMINI_API_KEY`, the database migration policy, and the build configuration requirements; add a "Production Deployment Checklist" section covering: real certificate pinning hashes, removing/guarding sandbox test accounts, configuring Firebase App Check

---

## Acceptance Gates

Before marking this spec complete, verify:

- [ ] App builds without errors on AGP 8.7.x with `./gradlew assembleDebug`
- [ ] App launches on a physical device or emulator without crashing
- [ ] Login with an existing test account works via Firebase Auth
- [ ] New member registration creates a Firebase Auth account
- [ ] Password reset sends a real Firebase email (not the in-app dialog update)
- [ ] Payment submission with a receipt image uploads to Firebase Storage and stores the URL
- [ ] Loan repayment reduces outstanding balance and marks COMPLETED at zero
- [ ] Reminder sweep creates in-app notifications for members with upcoming due dates
- [ ] Firebase sync status in Reports screen shows "Connected" when online and real sync counts
- [ ] Sandbox quick-login card is NOT visible in a release build (`./gradlew assembleRelease`)
- [ ] Upgrading from DB version 6 to 7 preserves existing data (test with a pre-existing DB)
- [ ] App is fully usable offline with no Firebase-related crashes when airplane mode is on
