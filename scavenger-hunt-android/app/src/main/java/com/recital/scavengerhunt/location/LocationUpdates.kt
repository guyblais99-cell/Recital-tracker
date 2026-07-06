package com.recital.scavengerhunt.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private fun Location.toFix(): LocationFix =
    LocationFix(latitude, longitude, accuracy.coerceAtLeast(1f))

/** Fast GPS stream for proximity radar — updates ~3–4× per second. */
@SuppressLint("MissingPermission")
fun locationUpdates(context: Context): Flow<LocationFix> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 280L)
        .setMinUpdateIntervalMillis(150L)
        .setMinUpdateDistanceMeters(0.15f)
        .setMaxUpdateDelayMillis(600L)
        .build()

    client.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) trySend(loc.toFix())
    }

    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(it.toFix()) }
        }
    }
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose { client.removeLocationUpdates(callback) }
}
