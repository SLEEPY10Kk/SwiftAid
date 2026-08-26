package com.example.swiftaid

import android.app.Application
import com.example.swiftaid.emergency.PendingSosSyncManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SwiftAidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PendingSosSyncManager.initialize(this)
    }
}
