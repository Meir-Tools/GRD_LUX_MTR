package com.example.grd_lux_mtr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magnetoSensor: Sensor? = null
    private lateinit var luxValueTextView: TextView
    private lateinit var bubble: View
    private lateinit var levelContainer: View
    private lateinit var compassDisk: ImageView
    private lateinit var mapArrow: ImageView
    private lateinit var gpsInfo: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapWebView: WebView
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        luxValueTextView = findViewById(R.id.luxValue)
        bubble = findViewById(R.id.bubble)
        levelContainer = findViewById(R.id.levelContainer)
        compassDisk = findViewById(R.id.compassDisk)
        mapArrow = findViewById(R.id.mapArrow)
        gpsInfo = findViewById(R.id.gpsInfo)
        
        mapWebView = findViewById(R.id.mapWebView)
        mapWebView.settings.javaScriptEnabled = true
        mapWebView.loadUrl("file:///android_asset/map.html")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetoSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (lightSensor == null) {
            luxValueTextView.text = getString(R.string.sensor_not_available)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize Location Request
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        // Initialize Location Callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val lat = location.latitude
                    val lng = location.longitude
                    gpsInfo.text = String.format(Locale.getDefault(), "GPS: %.4f, %.4f", lat, lng)
                    mapWebView.evaluateJavascript("updateLocation($lat, $lng)", null)
                }
            }
        }
        
        getDeviceLocation()
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetoSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val luxValue = event.values[0]
                luxValueTextView.text = String.format(Locale.getDefault(), "%.2f lx", luxValue)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravity = event.values.clone()
                updateBubble(event.values[0], event.values[1])
                updateCompass()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = event.values.clone()
                updateCompass()
            }
        }
    }

    private fun updateBubble(x: Float, y: Float) {
        val maxTranslation = (levelContainer.width - bubble.width) / 2f
        if (maxTranslation <= 0) return

        var tx = -x / 9.8f * maxTranslation
        var ty = y / 9.8f * maxTranslation

        val distance = Math.sqrt((tx * tx + ty * ty).toDouble()).toFloat()
        if (distance > maxTranslation) {
            val ratio = maxTranslation / distance
            tx *= ratio
            ty *= ratio
        }

        bubble.translationX = tx
        bubble.translationY = ty
    }

    private fun updateCompass() {
        if (gravity != null && geomagnetic != null) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                compassDisk.rotation = -azimuth
                // Rotate mapArrow to point in the device's heading (azimuth)
                mapArrow.rotation = azimuth
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this app
    }

    private fun getDeviceLocation() {
        try {
            if (hasLocationPermission()) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            // Initial centering
                            mapWebView.evaluateJavascript("updateLocation(${lastKnownLocation.latitude}, ${lastKnownLocation.longitude})", null)
                        } else {
                            if (!isLocationEnabled()) {
                                Toast.makeText(this, "Please enable GPS/Location services", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                requestLocationPermission()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 
            LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getDeviceLocation()
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
}
