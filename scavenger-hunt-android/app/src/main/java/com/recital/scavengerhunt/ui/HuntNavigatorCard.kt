package com.recital.scavengerhunt.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.recital.scavengerhunt.data.Checkpoint
import com.recital.scavengerhunt.location.GeoUtils
import com.recital.scavengerhunt.location.RADAR_MAX_RANGE_M
import com.recital.scavengerhunt.location.RADAR_RING_COUNT
import com.recital.scavengerhunt.location.NavigatorState
import com.recital.scavengerhunt.location.locationUpdates
import com.recital.scavengerhunt.ui.theme.HuntColors
import kotlin.math.min
import kotlin.math.roundToInt

private val RadarRed = Color(0xFFFF2D2D)
private val RadarOrange = Color(0xFFFF6B00)
private val RadarYellow = Color(0xFFFFD000)
private val RadarLime = Color(0xFFAAFF00)
private val RadarGreen = Color(0xFF39FF14)

/** Within this distance the radar shows only the green center pulse — no colored rings. */
private const val RADAR_GREEN_ONLY_M = 20.0

private const val RING_COUNT = RADAR_RING_COUNT
private val RadarBg = Color(0xFF2A1245)
private val RadarGridLine = Color.White.copy(alpha = 0.2f)

/** Visual-only: close enough that outer rings should be gone (uses distance, not GPS reliability). */
private fun isRadarGreenOnly(distanceM: Double, arrived: Boolean): Boolean =
    arrived || distanceM <= RADAR_GREEN_ONLY_M

private fun peeledBandCount(distanceM: Double, arrived: Boolean): Int =
    GeoUtils.radarPeeledBands(distanceM, arrived)

private fun activeBandStep(distanceM: Double, arrived: Boolean): Int {
    if (isRadarGreenOnly(distanceM, arrived)) return RING_COUNT - 1
    return peeledBandCount(distanceM, arrived).coerceAtMost(RING_COUNT - 1)
}

@Composable
fun HuntNavigatorCard(
    checkpoint: Checkpoint,
    onArrivedChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val targetLat = checkpoint.latitude
    val targetLon = checkpoint.longitude
    val hasGps = targetLat != null && targetLon != null

    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocation = granted }

    LaunchedEffect(Unit) {
        if (!hasLocation) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var nav by remember { mutableStateOf<NavigatorState?>(null) }
    var prevDistance by remember { mutableDoubleStateOf(-1.0) }

    LaunchedEffect(hasLocation, hasGps, targetLat, targetLon) {
        if (!hasLocation || !hasGps) return@LaunchedEffect
        locationUpdates(context).collect { fix ->
            val prev = if (prevDistance >= 0) prevDistance else null
            nav = GeoUtils.buildNavigator(fix, targetLat!!, targetLon!!, prev)
            prevDistance = nav!!.distanceM
        }
    }

    val state = nav

    LaunchedEffect(hasGps, state?.isArrived()) {
        onArrivedChange(!hasGps || state?.isArrived() == true)
    }

    val arrived = state?.isArrived() == true
    val fill = if (state != null) {
        GeoUtils.proximityFill(state.distanceM, arrived)
    } else {
        0.05f
    }

    val pulseMs = (320 + (1f - fill) * 680).toInt()

    val pulse by rememberInfiniteTransition(label = "proxPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(pulseMs, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HuntColors.cardDeep)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Distance radar",
                fontWeight = FontWeight.Bold,
                color = HuntColors.gold
            )

            when {
                !hasGps -> {
                    Text(
                        "This stop has no GPS pin — follow the direction clue!",
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )
                }
                !hasLocation -> {
                    Text("Allow location to use the radar", color = MaterialTheme.colorScheme.secondary)
                }
                state == null -> {
                    Text("Warming up GPS…", color = MaterialTheme.colorScheme.secondary)
                    ProximityRadarCanvas(distanceM = RADAR_MAX_RANGE_M, pulse = pulse, arrived = false)
                }
                else -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProximityRadarCanvas(
                            distanceM = state.distanceM,
                            pulse = pulse,
                            arrived = arrived
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                GeoUtils.formatDistance(state.distanceM),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRadarGreenOnly(state.distanceM, arrived)) {
                                    RadarGreen
                                } else {
                                    stepColor(activeBandStep(state.distanceM, arrived))
                                }
                            )
                            if (!state.gpsReliable) {
                                Text(
                                    "±${state.accuracyM.roundToInt()} m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HuntColors.gold
                                )
                            }
                        }
                    }

                    Text(
                        state.hotCold.trendLabel,
                        textAlign = TextAlign.Center,
                        color = if (isRadarGreenOnly(state.distanceM, arrived)) {
                            RadarGreen
                        } else {
                            stepColor(activeBandStep(state.distanceM, arrived))
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun ringColor(fill: Float): Color {
    val t = fill.coerceIn(0f, 1f)
    return when {
        t < 0.25f -> lerp(RadarRed, RadarOrange, t / 0.25f)
        t < 0.5f -> lerp(RadarOrange, RadarYellow, (t - 0.25f) / 0.25f)
        t < 0.75f -> lerp(RadarYellow, RadarLime, (t - 0.5f) / 0.25f)
        else -> lerp(RadarLime, RadarGreen, (t - 0.75f) / 0.25f)
    }
}

private fun stepColor(step: Int): Color {
    val t = step / (RING_COUNT - 1).coerceAtLeast(1).toFloat()
    return ringColor(t)
}

private fun DrawScope.drawRingBand(
    cx: Float,
    cy: Float,
    innerR: Float,
    outerR: Float,
    color: Color
) {
    if (outerR <= innerR + 0.5f) return
    val path = Path().apply {
        addOval(Rect(cx - outerR, cy - outerR, cx + outerR, cy + outerR))
        if (innerR > 0.5f) {
            addOval(Rect(cx - innerR, cy - innerR, cx + innerR, cy + innerR))
        }
        fillType = PathFillType.EvenOdd
    }
    drawPath(path, color)
}

@Composable
private fun ProximityRadarCanvas(distanceM: Double, pulse: Float, arrived: Boolean) {
    Canvas(Modifier.size(210.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = min(cx, cy) * 0.94f
        val greenOnly = isRadarGreenOnly(distanceM, arrived)
        val peeled = if (greenOnly) RING_COUNT else peeledBandCount(distanceM, arrived)

        drawCircle(color = RadarBg, radius = maxR, center = Offset(cx, cy))

        when {
            greenOnly -> drawGreenCenterPulse(cx, cy, maxR, pulse)
            peeled < RING_COUNT -> {
                val activeBand = peeled.coerceIn(0, RING_COUNT - 1)
                val outerR = maxR * (RING_COUNT - activeBand) / RING_COUNT.toFloat()
                val innerR = maxR * (RING_COUNT - activeBand - 1) / RING_COUNT.toFloat()
                drawRingBand(cx, cy, innerR, outerR, stepColor(activeBand).copy(alpha = 0.78f))

                val frontierR = maxR * (RING_COUNT - peeled) / RING_COUNT.toFloat()
                if (frontierR > 2f) {
                    val pulseColor = stepColor(peeled)
                    val pulseR = frontierR * (1f + pulse * 0.06f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                pulseColor.copy(alpha = 0.28f * (1f - pulse)),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = pulseR * 1.05f
                        ),
                        radius = pulseR * 1.05f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = pulseColor.copy(alpha = 0.55f + pulse * 0.4f),
                        radius = pulseR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.5f + pulse * 2f)
                    )
                }
            }
        }

        // Grid lines are always neutral — never tinted per ring (avoids "all segments colored" look)
        for (i in 1..RING_COUNT) {
            val radius = maxR * (i / RING_COUNT.toFloat())
            drawCircle(
                color = RadarGridLine,
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }
        drawCircle(
            color = RadarGridLine,
            radius = maxR,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
    }
}

private fun DrawScope.drawGreenCenterPulse(cx: Float, cy: Float, maxR: Float, pulse: Float) {
    val baseR = maxR / RING_COUNT.toFloat() * 0.55f
    val pulseR = baseR * (0.85f + pulse * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                RadarGreen.copy(alpha = 0.55f * (1f - pulse * 0.35f)),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = pulseR * 2.4f
        ),
        radius = pulseR * 2.4f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = RadarGreen.copy(alpha = 0.7f + pulse * 0.3f),
        radius = pulseR,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f + pulse * 2.5f)
    )
    drawCircle(color = RadarGreen, radius = 5f + pulse * 3f, center = Offset(cx, cy))
}
