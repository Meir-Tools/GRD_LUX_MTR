package com.example.grd_lux_mtr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        luxValueTextView = findViewById(R.id.luxValue)
        bubble = findViewById(R.id.bubble)
        levelContainer = findViewById(R.id.levelContainer)
        compassDisk = findViewById(R.id.compassDisk)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetoSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (lightSensor == null) {
            luxValueTextView.text = getString(R.string.sensor_not_available)
        }
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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            val luxValue = event.values[0]
            luxValueTextView.text = String.format(Locale.getDefault(), "%.2f lx", luxValue)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone()
            updateBubble(event.values[0], event.values[1])
            updateCompass()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone()
            updateCompass()
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
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this app
    }
}
