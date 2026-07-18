# Software Development Proposal
## SACCO Manager — Android Application

---

**Prepared by:** [Your Company Name]
**Prepared for:** [Client Name / Organisation]
**Date:** July 15, 2026
**Reference:** SACCO-PROP-2026-001

---

## 1. Executive Summary

We are pleased to submit this proposal for the development of **SACCO Manager**, a custom Android mobile application designed to digitise and streamline the operations of your Savings and Credit Cooperative (SACCO).

The application will replace manual record-keeping with a reliable, secure, and easy-to-use system that your members and administrators can access from any Android device — online or offline. It will carry your SACCO's name, logo, and brand colours.

We have structured this proposal into **three tiers** so you can select the scope that best fits your current budget and operational needs. All tiers include cloud sync, document uploads, AI loan assessment, and custom branding. The key difference between tiers is the **level of automation** and **payment integration**.

---

## 2. Problem Statement

Most SACCOs in Uganda rely on physical registers, spreadsheets, and manual calculations to manage member savings, loan disbursements, and dividend distributions. This leads to:

- Errors in savings records and loan calculations
- Delayed or disputed dividend sharing at year end
- No real-time visibility into the group's financial position
- Difficulty tracking loan repayments and defaulters
- No secure, centralised record of member documents and receipts
- Slow loan processing with no objective credit scoring

**SACCO Manager solves all of this** with a purpose-built Android application tailored to how Ugandan SACCOs operate.

---

## 3. Proposed Solution

A native Android application built with modern technology, featuring:

- Your SACCO's name, logo, colours, and membership number format
- An offline-first architecture so the app works without internet
- Four user roles: Member, Guarantor, Admin, and Super Admin
- Real-time cloud sync to Firebase (all tiers)
- Receipt and document photo uploads (all tiers)
- AI-powered loan credit scoring (all tiers)
- Manual payment confirmation workflows (Tiers 1 & 2)
- Direct MTN and Airtel Money API integration with full automation (Tier 3 only)

---

## 4. What Is Included in Every Tier

Regardless of which tier you choose, you receive the following:

**Custom Branding**
- App name, icon, splash screen, and colour scheme matching your SACCO's identity
- Custom membership number format (your SACCO prefix)
- Custom report headers and footers with your SACCO name, registration number, and physical address

**Member Portal**
- Member registration with full profile (name, national ID, phone, employer, next of kin, emergency contact, bank account, mobile money number)
- Monthly savings contribution tracking with visual progress bar
- Personal savings history and payment timeline with receipt photos
- Loan application form with guarantor nomination and purpose description
- AI-powered loan eligibility assessment before submission
- In-app notification inbox for reminders and alerts
- Performance dashboard: on-time compliance score, reward points, estimated annual dividend share

**Admin Console**
- Secure admin and super admin login with role-based access
- Member verification and status management (Active, Suspended, Frozen)
- Loan management: review, approve, disburse, and track repayments
- Expense recording and categorisation
- SACCO rules configuration: savings target, grace period, penalty amounts, interest rate
- Dividend profit simulator and year-end dividend declaration and locking
- Full audit log of all admin actions
- Report export in CSV, Excel, and PDF (Monthly Savings, Loans, Defaulters, Cash Flow)
- Bank statement generator

**Cloud & Security**
- Firebase cloud sync — all data backed up to Firestore in real time
- Firebase Authentication — secure login; no passwords stored on the device
- Firebase Storage — receipt and document photo uploads (up to 5MB per file)
- Offline-first local database — fully functional without internet
- Automatic sync when connectivity is restored
- Firestore and Storage security rules per user identity
- HTTPS-only networking and TLS enforcement

**AI Loan Assessment**
- Google Gemini AI integration for automated credit scoring
- Analyses savings history, compliance rate, outstanding balance, employment, and guarantor strength
- AI recommendation: Approve, Review, or Decline — with written justification
- AI financial coach for member questions about savings and loan eligibility

---

## 5. Scope of Work & Pricing

---

### TIER 1 — BASIC
**Investment: UGX 2,100,000**
**Delivery Timeline: 3 weeks**

All standard features listed in Section 4, with the following approach:

**Office-Managed Operations**
All deposits, loan requests, and loan tracking are handled at the SACCO office by the admin. Members do not need their own device or app account — the admin manages everything on their behalf.

**Deposits & Savings**
- Member brings their payment (cash or mobile money) to the office
- Admin records the deposit in the app, uploads the receipt photo, and marks it as approved
- Member savings history and balance are updated immediately in the system and synced to the cloud

**Loan Requests & Tracking**
- Member submits a loan request form at the office
- Admin captures the application in the app, assigns a guarantor, and runs the AI credit assessment
- Loan approval, disbursement recording, and repayment tracking all done by the admin at the office
- Admin records each repayment as it is received at the office

**Reminders**
- Admin manually triggers the reminder sweep from the dashboard when needed
- In-app notifications sent to members who have the app installed; otherwise admin follows up directly

**Reporting**
- Admin generates and exports reports on demand (CSV, Excel, PDF)

**Support Included**
- Installation and setup on your device(s)
- User training for admin staff (up to 2 hours)
- 30-day bug fix warranty after handover
- Email support

---

### TIER 2 — STANDARD
**Investment: UGX 2,800,000**
**Delivery Timeline: 5 weeks**

All standard features listed in Section 4, with the same office-managed approach as Tier 1 but with a more capable admin console and additional management tools.

**Office-Managed Operations**
Deposits, loan requests, and loan tracking continue to be handled at the SACCO office by admin staff. The difference from Tier 1 is a richer admin experience and multi-device access.

**Deposits & Savings**
- Member brings payment to the office; admin records the deposit, uploads the receipt, and approves it
- Multiple admin staff can work simultaneously from different devices — all see the same live data
- Payment history and cloud sync happen in real time across all admin devices

**Loan Requests & Tracking**
- Loan applications captured at the office by admin staff
- AI credit assessment run at point of capture to assist the loan committee's decision
- Loan repayments recorded by admin as members pay at the office
- Guarantor management and loan status tracking visible to all admin devices

**Reminders**
- Admin configures a reminder schedule (e.g. notify members 3 days before their due date)
- System automatically sends in-app notifications at the configured time — no need for admin to trigger manually each time
- Admin can review and override the reminder list at any point

**Additional Management Tools**
- Member referral programme with UGX 15,000 activation bonus tracking
- Savings interest projection calculator for member consultations at the office
- Multi-device admin access — any authorised staff member can log in from any Android device

**Support Included**
- Cloud project setup (Firebase Auth, Firestore, Storage)
- Onboarding session for admin staff (up to 3 hours)
- 60-day bug fix warranty after handover
- Email and WhatsApp support

---

### TIER 3 — PREMIUM
**Investment: UGX 3,500,000**
**Delivery Timeline: 7 weeks**

All standard features listed in Section 4, plus full automation of member-facing processes and direct mobile money integration. Members no longer need to visit the office for routine transactions.

**MTN & Airtel Money API Integration**
- Direct integration with the MTN Mobile Money API and Airtel Money API
- Members initiate savings deposits from inside the app — no USSD codes, no office visit required
- Payment is processed in real time; the app receives a confirmation and records it automatically against the member's account
- Loan disbursements sent directly to the member's registered mobile money number without admin manually arranging a transfer
- Full transaction reference numbers stored against every payment record
- Failed transaction handling with automatic retry and member notification

**Automated Deposits**
- Member opens the app, selects the savings month, and taps "Pay Now"
- System deducts from their MTN or Airtel wallet and records the deposit instantly
- Receipt automatically generated and stored in the cloud — no paper, no office visit

**Automated Loan Requests & Tracking**
- Member submits loan application from their phone — AI assessment runs immediately
- Admin receives a notification and approves or declines from any device
- On approval, funds are disbursed directly to the member's mobile money account automatically
- Repayment reminders sent automatically; member can repay directly from the app
- Loan balance updated in real time after every repayment

**Fully Automated Reminders & Workflows**
- Reminders fire automatically on a configured schedule — no admin action required
- Overdue payment alerts, loan maturity notices, and grace period warnings all sent automatically
- Penalties calculated and applied automatically at the end of the grace period
- Dividend calculation run automatically at year end based on locked FY data

**Automated Reporting**
- Scheduled monthly reports generated and delivered to admin via email or in-app
- Defaulters list updated in real time as payments become overdue
- Sync and financial health dashboard refreshed continuously without manual triggers

**Support Included**
- MTN and Airtel API credentials setup and sandbox testing assistance
- On-site setup and staff training in Kampala (travel costs outside Kampala billed separately)
- 90-day priority bug fix warranty after handover
- Priority WhatsApp support for 90 days

---

## 6. Budget Summary

| | Tier 1 — Basic | Tier 2 — Standard | Tier 3 — Premium |
|---|:---:|:---:|:---:|
| **Total Investment** | **UGX 2,100,000** | **UGX 2,800,000** | **UGX 3,500,000** |
| **Delivery Timeline** | 3 weeks | 5 weeks | 7 weeks |
| Custom SACCO branding | ✅ | ✅ | ✅ |
| Firebase cloud sync | ✅ | ✅ | ✅ |
| Receipt & document uploads | ✅ | ✅ | ✅ |
| AI loan assessment | ✅ | ✅ | ✅ |
| Offline-first database | ✅ | ✅ | ✅ |
| Deposits handled at office | ✅ | ✅ | — |
| Member self-service deposits (via app) | ❌ | ❌ | ✅ |
| MTN & Airtel Money API | ❌ | ❌ | ✅ |
| Loan requests handled at office | ✅ | ✅ | — |
| Member self-service loan requests | ❌ | ❌ | ✅ |
| Automated loan disbursements | ❌ | ❌ | ✅ |
| Reminders | Manual trigger | Auto-scheduled | Fully automatic |
| Automated penalty application | ❌ | ❌ | ✅ |
| Scheduled report delivery | ❌ | ❌ | ✅ |
| Multi-device admin access | ❌ | ✅ | ✅ |
| Member referral programme | ❌ | ✅ | ✅ |
| Support period | 30 days | 60 days | 90 days |

**Payment Terms:** 50% deposit to commence work. 50% balance on delivery and sign-off.

---

## 7. Optional Add-Ons

These can be added to any tier at any time:

| Add-On | Price |
|---|---|
| SMS notifications (Africa's Talking or Twilio) | UGX 400,000 |
| Google Sheets / Excel cloud backup integration | UGX 500,000 |
| Support extension (additional 90 days) | UGX 200,000 |
| Additional on-site training day | UGX 150,000 |
| Firebase project setup (if no Google account exists) | UGX 100,000 |

---

## 8. What We Need From You

To begin development, we will require:

1. Confirmation of the selected tier and signed acceptance of this proposal
2. 50% deposit payment
3. Your SACCO's name, logo (PNG or vector), and brand colours
4. Your preferred membership number format (e.g. `KASUKU-001`)
5. List of initial admin accounts (name and email address)
6. Android device(s) available for testing during development
7. MTN and Airtel Money merchant credentials *(Tier 3 only)*

---

## 9. Terms & Conditions

- This proposal is valid for **30 days** from the date above.
- Scope changes after development has commenced may affect the timeline and cost. Any additions will be quoted separately and require written approval before work begins.
- The client is responsible for all ongoing Firebase usage costs (Firestore, Storage, Authentication). For most small-to-medium SACCOs these remain within Google's free tier.
- The Gemini AI API key is registered to the client's own Google account. Usage within the free quota is at no cost.
- MTN and Airtel Money API access (Tier 3) requires the client to hold an active merchant agreement with each provider. We will assist with the technical integration; commercial agreements are the client's responsibility.
- Bug fixes are covered during the warranty period. New feature requests after delivery are quoted separately.

---

## 10. Acceptance

By signing below, the client confirms acceptance of the scope of work and payment terms described in this proposal.

**Selected Tier:**

☐ &nbsp; **Tier 1 — Basic** &nbsp;&nbsp;&nbsp; UGX 2,100,000

☐ &nbsp; **Tier 2 — Standard** &nbsp;&nbsp;&nbsp; UGX 2,800,000

☐ &nbsp; **Tier 3 — Premium** &nbsp;&nbsp;&nbsp; UGX 3,500,000

&nbsp;

| | Client | Developer |
|---|---|---|
| **Name** | | |
| **Title / Role** | | |
| **Signature** | | |
| **Date** | | |

---

*Thank you for the opportunity to present this proposal. We look forward to building a solution that works for your SACCO and its members.*
