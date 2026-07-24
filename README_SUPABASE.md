# ⚡ Connecting and Deploying to your Supabase Project

This application has been successfully migrated to use **Supabase** for both **Authentication** and **Database synchronization**!

The application is built with a dual-mode edge-first architecture:
1. **Local Mock Sandbox Mode**: If no credentials are configured, the app will run locally and simulate both authentication and real-time database syncing with visual feedback on the Reports screen.
2. **Real Sync Mode**: Once you supply your real Supabase credentials, the app will perform real network authentication requests and upsert synchronized rows to your remote PostgreSQL instance.

---

## 📥 Step 1: Add your Supabase Credentials

To link your remote Supabase project, simply add your credentials to the `.env` file at the project root:

```env
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_KEY=your-supabase-anon-key
```

The app's build system automatically picks up these values via the **Secrets Gradle Plugin** and compiles them into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_KEY`.

---

## 🛡️ Step 2: Deploy Database Tables in Supabase

To support full-edge database synchronization, execute the following SQL scripts in the **SQL Editor** of your Supabase Dashboard to create the tables. All tables support real-time postgREST upserts.

```sql
-- 1. Users Registration
CREATE TABLE IF NOT EXISTS users_registration (
    id VARCHAR PRIMARY KEY,
    email VARCHAR UNIQUE NOT NULL,
    phone VARCHAR,
    name VARCHAR,
    role VARCHAR,
    status VARCHAR,
    membershipNumber VARCHAR,
    firebaseUid VARCHAR, -- Stores the Supabase Auth User ID
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 2. Member Profiles
CREATE TABLE IF NOT EXISTS member_profiles (
    id VARCHAR PRIMARY KEY,
    memberId VARCHAR UNIQUE NOT NULL,
    savingsPlanId VARCHAR,
    referralCode VARCHAR,
    totalSavings NUMERIC DEFAULT 0,
    outstandingLoans NUMERIC DEFAULT 0,
    creditScore INTEGER DEFAULT 500,
    membershipTier VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 3. Savings Payments
CREATE TABLE IF NOT EXISTS savings_payments (
    id VARCHAR PRIMARY KEY,
    receiptNumber VARCHAR UNIQUE,
    memberId VARCHAR NOT NULL,
    memberName VARCHAR,
    amount NUMERIC,
    cycleMonthIndex INTEGER,
    cycleYear INTEGER,
    bankName VARCHAR,
    branch VARCHAR,
    transactionId VARCHAR,
    status VARCHAR,
    submittedAt VARCHAR,
    notes VARCHAR,
    receiptImageUrl VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 4. Loan Applications
CREATE TABLE IF NOT EXISTS loan_applications (
    id VARCHAR PRIMARY KEY,
    memberId VARCHAR NOT NULL,
    applicantName VARCHAR,
    purpose VARCHAR,
    amount NUMERIC,
    repaymentPeriodMonths INTEGER,
    guarantorId VARCHAR,
    guarantorApproved BOOLEAN DEFAULT FALSE,
    status VARCHAR,
    appliedAt VARCHAR,
    comments VARCHAR,
    assessedByAi BOOLEAN DEFAULT FALSE,
    aiRecommendation VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 5. Loan Repayments
CREATE TABLE IF NOT EXISTS loan_repayments (
    id VARCHAR PRIMARY KEY,
    receiptNumber VARCHAR UNIQUE,
    applicationId VARCHAR NOT NULL,
    memberId VARCHAR NOT NULL,
    amount NUMERIC,
    repaymentDate VARCHAR,
    installmentNumber INTEGER,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 6. Sacco Expenses
CREATE TABLE IF NOT EXISTS sacco_expenses (
    id VARCHAR PRIMARY KEY,
    amount NUMERIC,
    category VARCHAR,
    description VARCHAR,
    submittedBy VARCHAR,
    submittedAt VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 7. Savings Plans
CREATE TABLE IF NOT EXISTS savings_plans (
    id VARCHAR PRIMARY KEY,
    memberId VARCHAR NOT NULL,
    targetAmount NUMERIC,
    currentAccumulated NUMERIC,
    frequency VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 8. Member Referrals
CREATE TABLE IF NOT EXISTS member_referrals (
    id VARCHAR PRIMARY KEY,
    referrerId VARCHAR NOT NULL,
    referredId VARCHAR UNIQUE NOT NULL,
    referredName VARCHAR,
    status VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 9. Savings Rules
CREATE TABLE IF NOT EXISTS savings_rules (
    id VARCHAR PRIMARY KEY,
    isLocked BOOLEAN DEFAULT FALSE,
    interestRate NUMERIC,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 10. Declared Dividends
CREATE TABLE IF NOT EXISTS declared_dividends (
    id VARCHAR PRIMARY KEY,
    year INTEGER NOT NULL,
    poolAmount NUMERIC,
    declaredBy VARCHAR,
    declaredAt VARCHAR,
    isLocked BOOLEAN DEFAULT FALSE,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 11. Dividend Audit Records
CREATE TABLE IF NOT EXISTS dividend_audit_records (
    id VARCHAR PRIMARY KEY,
    dividendYear INTEGER,
    memberId VARCHAR,
    memberName VARCHAR,
    savingsSnapshot NUMERIC,
    dividendPayout NUMERIC,
    calculatedAt VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 12. Sacco Notifications
CREATE TABLE IF NOT EXISTS sacco_notifications (
    id VARCHAR PRIMARY KEY,
    memberId VARCHAR NOT NULL,
    title VARCHAR,
    content VARCHAR,
    type VARCHAR,
    isRead BOOLEAN DEFAULT FALSE,
    createdAt VARCHAR,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);

-- 13. Loan Products
CREATE TABLE IF NOT EXISTS loan_products (
    id VARCHAR PRIMARY KEY,
    name VARCHAR,
    interestRate NUMERIC,
    _syncTimestamp BIGINT,
    _actionType VARCHAR,
    _id INTEGER
);
```

---

## ⚡ Step 3: Enable Realtime (Optional)

If you wish to allow live bidirectional updates:
1. Go to the **Database** tab in your Supabase project dashboard.
2. Select **Replication** and click on the **active** replication set.
3. Toggle replication **on** for the synced tables (e.g., `savings_payments`, `loan_applications`, `member_profiles`, `users_registration`).

---

## 📊 Step 4: Explore the Supabase Dashboard in App

In the SACCO Manager application, log in as an Admin or Super Admin, navigate to the **Reports** screen and select the **Supabase Database Sync** tab to view:
* **Supabase Databases Status Board**: Real-time connection status for both Supabase REST Database and Supabase Auth.
* **Sync Button**: Manually trigger a complete sweep backing up all local Room entities to Supabase PostgreSQL tables.
* **Live Sync Logs Monitor**: A live, virtual terminal demonstrating active sync operations, background polls, deletes, and successes.
* **Supabase Table Tree Explorer**: A real-time visual schema snapshot mapped in the Supabase consoles.
