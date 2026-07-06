package com.recital.scavengerhunt

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class ScavengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyC9xRYjrl9g7h_3g_BnRoea7FfcvIlCCa0")
                .setApplicationId("1:1039061307164:android:scavengerhunt0001")
                .setProjectId("recital-tracker")
                .setDatabaseUrl("https://recital-tracker-default-rtdb.firebaseio.com")
                .setStorageBucket("recital-tracker.firebasestorage.app")
                .setGcmSenderId("1039061307164")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
