package com.example.grd_lux_mtr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
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
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var map: MapView
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Set a very specific User-Agent before anything else
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = "GrdLuxMtrApp/1.1 (Android; Meir-Tools-Project)"
        
        // 2. Load configuration
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        osmConfig.load(this, sharedPrefs)
        
        // 3. Set cache location to internal to avoid permission issues
        osmConfig.osmdroidTileCache = java.io.File(cacheDir, "osm_tiles")
        
        setContentView(R.layout.activity_main)

        luxValueTextView = findViewById(R.id.luxValue)
        bubble = findViewById(R.id.bubble)
        levelContainer = findViewById(R.id.levelContainer)
        compassDisk = findViewById(R.id.compassDisk)
        mapArrow = findViewById(R.id.mapArrow)
        map = findViewById(R.id.map)

        // 4. Initialize Map with a different, more reliable tile source
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.WIKIMEDIA)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)
        
        // 5. Force clear cache for this run to remove "403" tiles
        Thread {
            map.tileProvider.tileCache.clear()
        }.start()
        
        // Clear cache to remove blocked tiles
        map.tileProvider.tileCache.clear()
        map.invalidate()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetoSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (lightSensor == null) {
            luxValueTextView.text = getString(R.string.sensor_not_available)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        getDeviceLocation()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetoSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        sensorManager.unregisterListener(this)
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
                            val geoPoint = GeoPoint(lastKnownLocation.latitude, lastKnownLocation.longitude)
                            map.controller.setCenter(geoPoint)
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
