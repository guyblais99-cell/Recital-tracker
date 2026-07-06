package com.recital.scavengerhunt.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Count as "arrived" when within this radius (meters). Matches typical phone GPS (~±12 m). */
const val ARRIVED_RADIUS_M = 12.0

/** Prefer this accuracy when pinning; up to [HOST_PIN_FALLBACK_ACCURACY_M] is accepted with a warning. */
const val HOST_PIN_IDEAL_ACCURACY_M = 12f

/** Host can still save a pin outdoors when GPS is fuzzy (common on phones). */
const val HOST_PIN_MAX_ACCURACY_M = 40f

/** Player hot/cold works while GPS is at least this accurate (meters). */
const val PLAYER_RELIABLE_ACCURACY_M = 50f

/** Radar uses 7 concentric bands; peel thresholds remove one outer band each. */
const val RADAR_RING_COUNT = 7

/** Farthest distance (m) where the outer red ring is still fully active. */
const val RADAR_MAX_RANGE_M = 170.0

enum class HotColdLevel(val label: String, val emoji: String) {
    FREEZING("Freezing", "🥶"),
    COLD("Cold", "❄️"),
    COOL("Cool", "🌤️"),
    WARM("Getting warm", "🔥"),
    HOT("Hot!", "♨️"),
    ARRIVED("You're here!", "📍")
}

data class HotColdState(
    val distanceM: Double,
    val level: HotColdLevel,
    val trendLabel: String
)

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float
)

data class NavigatorState(
    val distanceM: Double,
    val accuracyM: Float,
    val targetBearingDeg: Float,
    val hotCold: HotColdState,
    val cardinal: String,
    val gpsReliable: Boolean
) {
    /** True when close enough to count as physically at the stop. */
    fun isArrived(): Boolean =
        gpsReliable && distanceM <= ARRIVED_RADIUS_M
}

object GeoUtils {

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /** Bearing from point A to B in degrees (0 = north, clockwise). */
    fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x))
        if (bearing < 0) bearing += 360.0
        return bearing.toFloat()
    }

    fun bearingToCardinal(bearingDeg: Float): String {
        val dirs = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        val idx = ((bearingDeg + 22.5f) / 45f).toInt() % 8
        return dirs[idx]
    }

    /** Smallest signed difference a - b in degrees (-180..180). */
    fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Visual fill level 0 (far) .. 1 (at stop). Used for pulse speed only. */
    fun proximityFill(distanceM: Double, isArrived: Boolean): Float {
        if (isArrived) return 1f
        return (1.0 - distanceM / RADAR_MAX_RANGE_M).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * How many outer radar bands have peeled away (0 = far, only outer red ring).
     * Each threshold removes the next outer band as the player walks closer.
     */
    fun radarPeeledBands(distanceM: Double, isArrived: Boolean): Int {
        if (isArrived) return RADAR_RING_COUNT
        val peelWhenCloserThanM = doubleArrayOf(
            RADAR_MAX_RANGE_M * 200.0 / 260.0,
            RADAR_MAX_RANGE_M * 140.0 / 260.0,
            RADAR_MAX_RANGE_M * 95.0 / 260.0,
            RADAR_MAX_RANGE_M * 65.0 / 260.0,
            RADAR_MAX_RANGE_M * 42.0 / 260.0,
            RADAR_MAX_RANGE_M * 28.0 / 260.0
        )
        var peeled = 0
        for (threshold in peelWhenCloserThanM) {
            if (distanceM < threshold) peeled++ else break
        }
        return peeled.coerceIn(0, RADAR_RING_COUNT - 1)
    }

    fun closerFurtherLabel(
        distanceM: Double,
        previousDistanceM: Double?,
        gpsReliable: Boolean,
        isArrived: Boolean
    ): String = when {
        isArrived -> "You're here — ready to scan!"
        !gpsReliable -> "Finding GPS signal…"
        previousDistanceM == null -> "Start walking — watch the rings fill"
        distanceM < previousDistanceM - 1.5 -> "Getting closer"
        distanceM > previousDistanceM + 1.5 -> "Getting further"
        else -> "Hold steady"
    }

    fun hotCold(distanceM: Double, previousDistanceM: Double?, gpsReliable: Boolean): HotColdState {
        val level = when {
            gpsReliable && distanceM <= ARRIVED_RADIUS_M -> HotColdLevel.ARRIVED
            distanceM <= 25 -> HotColdLevel.HOT
            distanceM <= 55 -> HotColdLevel.WARM
            distanceM <= 120 -> HotColdLevel.COOL
            distanceM <= 250 -> HotColdLevel.COLD
            else -> HotColdLevel.FREEZING
        }
        val trendLabel = closerFurtherLabel(
            distanceM,
            previousDistanceM,
            gpsReliable,
            level == HotColdLevel.ARRIVED
        )
        return HotColdState(distanceM, level, trendLabel)
    }

    fun buildNavigator(
        fix: LocationFix,
        targetLat: Double,
        targetLon: Double,
        previousDistanceM: Double?
    ): NavigatorState {
        val dist = distanceMeters(fix.lat, fix.lon, targetLat, targetLon)
        val reliable = fix.accuracyM <= PLAYER_RELIABLE_ACCURACY_M
        val bearing = bearingDegrees(fix.lat, fix.lon, targetLat, targetLon)
        val hotCold = hotCold(dist, previousDistanceM, reliable)
        return NavigatorState(
            distanceM = dist,
            accuracyM = fix.accuracyM,
            targetBearingDeg = bearing,
            hotCold = hotCold,
            cardinal = bearingToCardinal(bearing),
            gpsReliable = reliable
        )
    }

    fun formatDistance(m: Double): String =
        if (m >= 1000) "${"%.1f".format(m / 1000)} km" else "${m.roundToInt()} m"

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): LocationFix? = suspendCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource().token
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token)
            .addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    cont.resume(LocationFix(loc.latitude, loc.longitude, loc.accuracy.coerceAtLeast(1f)))
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }

    /**
     * Waits for a good GPS fix before saving a checkpoint pin.
     * Returns the best fix found (prefer one accurate enough for [HOST_PIN_MAX_ACCURACY_M]).
     */
    @SuppressLint("MissingPermission")
    suspend fun capturePinLocation(context: Context): LocationFix? {
        var best: LocationFix? = null
        repeat(25) {
            val fix = currentLocation(context)
            if (fix != null) {
                if (best == null || fix.accuracyM < best!!.accuracyM) best = fix
                if (fix.accuracyM <= HOST_PIN_IDEAL_ACCURACY_M) return fix
            }
            kotlinx.coroutines.delay(800)
        }
        return best?.takeIf { it.accuracyM <= HOST_PIN_MAX_ACCURACY_M }
    }

    fun isPinAccurateEnough(accuracyM: Float): Boolean = accuracyM <= HOST_PIN_MAX_ACCURACY_M

    fun isPinIdeal(accuracyM: Float): Boolean = accuracyM <= HOST_PIN_IDEAL_ACCURACY_M

    /** Player GPS bar: 0 = weak, 1 = strong (±12 m or better). */
    fun playerGpsStrength(accuracyM: Float): Float {
        if (accuracyM <= HOST_PIN_IDEAL_ACCURACY_M) return 1f
        if (accuracyM >= PLAYER_RELIABLE_ACCURACY_M) return 0.08f
        val span = PLAYER_RELIABLE_ACCURACY_M - HOST_PIN_IDEAL_ACCURACY_M
        return (1f - (accuracyM - HOST_PIN_IDEAL_ACCURACY_M) / span).coerceIn(0.08f, 1f)
    }
}
