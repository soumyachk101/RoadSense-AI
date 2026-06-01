package com.roadsense.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlin.math.*

class SensorEngine(
    private val context: Context,
    private val tripId: String,
    private val vehicleType: String,
    private val placement: String,
    private val onPocDetected: (Map<String, Any>) -> Unit,
    private val onFlush: suspend (List<Map<String, Any>>) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val pocBuffer = mutableListOf<Map<String, Any>>()
    private var currentSpeed = 0.0
    private var currentLat = 0.0
    private var currentLng = 0.0
    
    private var lpfValue = 0.0
    private val LPF_ALPHA = 0.8
    private var prevZ: Double? = null
    
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun start() {
        sensorManager.registerListener(this, accelerometer, 10000) // ~100Hz
        startLocationUpdates()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        scope.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            processSample(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processSample(ax: Float, ay: Float, az: Float) {
        if (currentSpeed < 5) return

        val (axV, ayV, azV) = autoOrient(ax.toDouble(), ay.toDouble(), az.toDouble())
        
        lpfValue = LPF_ALPHA * lpfValue + (1 - LPF_ALPHA) * azV
        val zFiltered = azV - lpfValue

        // Dynamic threshold logic (simplified for Kotlin port)
        val threshold = if (vehicleType == "two_wheeler") 0.714 else 1.08
        
        if (abs(zFiltered) > threshold) {
            val poc = mapOf(
                "trip_id" to tripId,
                "lat" to currentLat,
                "lng" to currentLng,
                "z_value" to zFiltered,
                "z_prev" to (prevZ ?: 0.0),
                "speed_kmh" to currentSpeed,
                "recorded_at" to System.currentTimeMillis().toString()
            )
            pocBuffer.add(poc)
            onPocDetected(poc)
            
            if (pocBuffer.size >= 50) {
                val batch = pocBuffer.toList()
                pocBuffer.clear()
                scope.launch { onFlush(batch) }
            }
        }
        prevZ = zFiltered
    }

    private fun autoOrient(ax: Double, ay: Double, az: Double): Triple<Double, Double, Double> {
        val theta = atan2(ay, az)
        val beta = atan2(-ax, sqrt(ay * ay + az * az))

        val axV = ax * cos(beta) + ay * sin(beta) * sin(theta) + az * cos(theta) * sin(beta)
        val ayV = ay * cos(theta) - az * sin(theta)
        val azV = -ax * sin(beta) + ay * cos(beta) * sin(theta) + az * cos(beta) * cos(theta)

        return Triple(axV, ayV, azV)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                    currentSpeed = it.speed * 3.6 // m/s to km/h
                }
            }
        }, null)
    }
}
