package com.recital.scavengerhunt.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/** Device heading in degrees (0 = north, clockwise). */
@Composable
fun rememberDeviceHeading(): Float {
    val context = LocalContext.current
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (sensor == null) {
            onDispose {}
        } else {
            val rotMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                    SensorManager.getOrientation(rotMatrix, orientation)
                    var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (az < 0f) az += 360f
                    heading = az
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sm.unregisterListener(listener) }
        }
    }

    return heading
}

fun walkHint(relativeBearingDeg: Float): String {
    val abs = kotlin.math.abs(relativeBearingDeg)
    return when {
        abs <= 18f -> "Walk straight ahead"
        abs >= 162f -> "Turn around"
        relativeBearingDeg > 0 -> "Turn right ${abs.roundToInt()}°"
        else -> "Turn left ${abs.roundToInt()}°"
    }
}
