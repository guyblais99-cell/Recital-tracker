package com.recital.scavengerhunt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.recital.scavengerhunt.ui.ScavengerApp
import com.recital.scavengerhunt.ui.theme.ScavengerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            ScavengerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScavengerApp()
                }
            }
        }
    }
}
