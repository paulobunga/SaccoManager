package com.litesails.saccomanager

import android.app.Application
import com.clerk.android.Clerk

class SaccoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Clerk with the publishable key from BuildConfig.
        // The key is injected from .env via the Secrets Gradle Plugin.
        Clerk.initialize(
            context = this,
            publishableKey = BuildConfig.CLERK_PUBLISHABLE_KEY
        )
    }
}
