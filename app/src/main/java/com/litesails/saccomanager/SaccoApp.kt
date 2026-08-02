package com.litesails.saccomanager

import android.app.Application

class SaccoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Clerk Android SDK is not currently included in dependencies,
        // so initialization is deferred until auth is wired up.
    }
}
