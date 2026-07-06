package com.recital.scavengerhunt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recital.scavengerhunt.location.GeoUtils
import com.recital.scavengerhunt.location.HOST_PIN_IDEAL_ACCURACY_M
import com.recital.scavengerhunt.location.HOST_PIN_MAX_ACCURACY_M
import com.recital.scavengerhunt.location.LocationFix
import com.recital.scavengerhunt.location.locationUpdates
import com.recital.scavengerhunt.ui.theme.HuntColors
import kotlin.math.roundToInt

@Composable
fun LiveGpsAccuracyCard(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fix by remember { mutableStateOf<LocationFix?>(null) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            fix = null
            return@LaunchedEffect
        }
        locationUpdates(context).collect { fix = it }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HuntColors.cardDeep)
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (fix != null) Icons.Default.LocationOn else Icons.Default.LocationSearching,
                    contentDescription = null,
                    tint = pinAccent(fix?.accuracyM)
                )
                Text("Live GPS", fontWeight = FontWeight.SemiBold)
            }

            when {
                !enabled -> {
                    Text(
                        "Allow location to see accuracy before pinning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                fix == null -> {
                    Text(
                        "Searching for satellites…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = HuntColors.cyan,
                        trackColor = HuntColors.card
                    )
                }
                else -> {
                    val acc = fix!!.accuracyM
                    Text(
                        "±${acc.roundToInt()} m",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = pinAccent(acc)
                    )
                    LinearProgressIndicator(
                        progress = { pinStrength(acc) },
                        modifier = Modifier.fillMaxWidth(),
                        color = pinAccent(acc),
                        trackColor = HuntColors.card
                    )
                    Text(
                        pinHint(acc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

/** 0 = unusable, 1 = ideal pin accuracy. */
private fun pinStrength(accuracyM: Float): Float {
    if (accuracyM <= HOST_PIN_IDEAL_ACCURACY_M) return 1f
    if (accuracyM >= HOST_PIN_MAX_ACCURACY_M) return 0.12f
    val span = HOST_PIN_MAX_ACCURACY_M - HOST_PIN_IDEAL_ACCURACY_M
    return (1f - (accuracyM - HOST_PIN_IDEAL_ACCURACY_M) / span).coerceIn(0.12f, 1f)
}

private fun pinAccent(accuracyM: Float?): androidx.compose.ui.graphics.Color {
    if (accuracyM == null) return HuntColors.cyan
    return when {
        GeoUtils.isPinIdeal(accuracyM) -> HuntColors.success
        GeoUtils.isPinAccurateEnough(accuracyM) -> HuntColors.gold
        else -> HuntColors.accent
    }
}

private fun pinHint(accuracyM: Float): String = when {
    !GeoUtils.isPinAccurateEnough(accuracyM) ->
        "Too fuzzy (need ±${HOST_PIN_MAX_ACCURACY_M.toInt()} m or better). " +
            "Step to open sky, hold phone up, stand still — or walk a few steps and watch this number drop."
    !GeoUtils.isPinIdeal(accuracyM) ->
        "OK to pin now. For a tighter stop, wait or move slowly until you see ±${HOST_PIN_IDEAL_ACCURACY_M.toInt()} m, then tap Set GPS."
    else ->
        "Good signal — stand at the exact spot and tap Set GPS."
}
