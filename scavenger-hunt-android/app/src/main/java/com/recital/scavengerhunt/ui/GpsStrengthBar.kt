package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.recital.scavengerhunt.location.GeoUtils
import com.recital.scavengerhunt.location.LocationFix
import com.recital.scavengerhunt.location.locationUpdates
import com.recital.scavengerhunt.ui.theme.HuntColors

private val GpsWeak = Color(0xFFFF2D2D)
private val GpsFair = Color(0xFFFF6B00)
private val GpsGood = Color(0xFFFFD93D)

@Composable
fun GpsStrengthBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fix by remember { mutableStateOf<LocationFix?>(null) }
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            fix = null
            return@LaunchedEffect
        }
        locationUpdates(context).collect { fix = it }
    }

    val strength = fix?.let { GeoUtils.playerGpsStrength(it.accuracyM) }
    val barColor = gpsBarColor(strength, hasPermission)
    val trackColor = HuntColors.cardDeep

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp),
        contentAlignment = Alignment.Center
    ) {
        if (strength == null && hasPermission) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                color = HuntColors.cyan.copy(alpha = 0.7f),
                trackColor = trackColor
            )
        } else {
            LinearProgressIndicator(
                progress = { strength ?: 0.06f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                color = barColor,
                trackColor = trackColor
            )
        }
        Text(
            text = "GPS strength",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.92f)
        )
    }
}

private fun gpsBarColor(strength: Float?, hasPermission: Boolean): Color {
    if (!hasPermission) return GpsWeak
    val s = strength ?: return GpsWeak
    return when {
        s >= 0.72f -> HuntColors.success
        s >= 0.45f -> GpsGood
        s >= 0.22f -> GpsFair
        else -> GpsWeak
    }
}
