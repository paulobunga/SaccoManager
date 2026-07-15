# ⚡ Connecting and Deploying to your Firebase Project (`sacco-app-bebb1`)

We have successfully integrated both **Cloud Firestore** and the **Firebase Realtime Database** into the SACCO Manager applet! The application is fully built with safe fallback structures, meaning it will run smoothly in the offline preview sandbox using local mock simulations and will seamlessly activate real-time cloud writes as soon as the configuration is present.

To link your newly created Firebase project **`sacco-app-bebb1`** and deploy the Firestore security rules, follow the steps below.

---

## 📥 Step 1: Add your `google-services.json`
To allow the Android app to connect directly to `sacco-app-bebb1`, you need to place the configuration file inside the app module:

1. Go to the [Firebase Console](https://console.firebase.google.com/) and open your project **`sacco-app-bebb1`**.
2. Click the gear icon next to **Project Overview** and select **Project settings**.
3. Under **Your apps**, click **Add app** (select the **Android** icon).
4. Enter the package name / application ID:
   * **Package Name / Namespace**: `com.example`
5. Register the app and click **Download google-services.json**.
6. Upload or copy this file directly into the `/app` folder of your project workspace:
   ```text
   /app/google-services.json
   ```

---

## 🛡️ Step 2: Deploy Firestore Security Rules
We have generated the optimal role-based security rules in **`firestore.rules`** in the project root. These rules ensure that:
* **Admins/Super Admins** have full restricted read/write access.
* **Members** can only read or write their own documents (payments, profile registrations, loan applications).
* **Guarantors** can safely read and approve/sign off on loans they are guaranteeing.

### Option A: Via the Firebase Console (Easiest & Fastest)
1. In the project root, open the **`firestore.rules`** file.
2. Copy the entire contents of the file.
3. Open the [Firebase Console](https://console.firebase.google.com/) for `sacco-app-bebb1`.
4. In the left navigation, click **Firestore Database** and select the **Rules** tab.
5. Paste the copied rules, replacing everything in the editor, and click **Publish**.

### Option B: Via the Firebase CLI (Command Line)
If you have the Firebase CLI installed on your local computer, you can download this project as a ZIP and run:
```bash
firebase login
firebase use sacco-app-bebb1
firebase deploy --only firestore:rules
```

---

## 📡 Step 3: Enable the Realtime Database
1. In the left navigation of the Firebase Console, click **Realtime Database**.
2. Click **Create Database**, choose your database location, and click **Next**.
3. Select **Start in locked mode** and click **Enable**.
4. *(Optional)* To ensure real-time updates flow without restrictions, you can set the Realtime Database rules under its **Rules** tab:
   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null"
     }
   }
   ```

---

## 📊 Step 4: Explore the Firebase Dashboard
In the SACCO application, navigate to the **Reports** screen and select the **Firebase Database Sync** tab. You will find:
* **Firebase Databases Status Board**: Real-time status indicators for both Firestore and Realtime DB.
* **Sync Button**: Manually trigger a full database backup sweep that syncs all existing records (Savings, Loans, Profiles, Users) into your Firebase collections.
* **Live Sync Logs Monitor**: A virtual terminal showing live background synchronization tasks, successes, and errors.
* **Firebase Document Tree Explorer**: A real-time schema viewer reflecting the visual map of your Firebase cloud databases.
