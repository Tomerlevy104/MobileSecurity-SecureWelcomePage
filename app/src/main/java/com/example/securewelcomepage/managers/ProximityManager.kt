package com.example.securewelcomepage.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import com.example.securewelcomepage.interfaces.IChallengeManager
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.utilities.ChallengeType
import java.util.concurrent.atomic.AtomicBoolean

class ProximityManager(private val context: Context) : IChallengeManager, SensorEventListener {

    private val TAG = "ProximityManager"
    private val PROXIMITY_THRESHOLD = 4f
    private val PROXIMITY_DURATION_MS = 10000L // 10 seconds
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val isProximityNear = AtomicBoolean(false)
    private var timer: CountDownTimer? = null
    private var isProximityChallengeCompleted = false
    private var completionListener: IOnChallengeCompletedListener? = null
    private val hasProximitySensor: Boolean
        get() = proximitySensor != null

    init {
        if (!hasProximitySensor) {
            Toast.makeText(context, "Device doesn't have proximity sensor", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun startChallenge() {
        if (hasProximitySensor) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun stopChallenge() {
        sensorManager.unregisterListener(this)
        timer?.cancel()
    }

    override fun isCompleted(): Boolean {
        return isProximityChallengeCompleted
    }

    override fun registerCompletionListener(listener: IOnChallengeCompletedListener) {
        completionListener = listener
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            // `event.values[0]` contains the distance in centimeters between the sensor and the nearest object
            val distance = event.values[0]
            Log.d(TAG, "Raw Distance: $distance, Threshold: $PROXIMITY_THRESHOLD")

            // Object is near
            if (distance < PROXIMITY_THRESHOLD) {
                if (!isProximityNear.getAndSet(true)) {
                    // Show toast and start timer when state changes from far to near
                    Toast.makeText(context, "Object detected near sensor!", Toast.LENGTH_SHORT)
                        .show()
                    Log.d(TAG, "Object is NEAR")

                    // Start timer
                    timer?.cancel()
                    timer = object : CountDownTimer(PROXIMITY_DURATION_MS, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val secondsLeft = millisUntilFinished / 1000
                            Log.d(TAG, "Keep covered: $secondsLeft seconds left")
                        }

                        override fun onFinish() {
                            if (isProximityNear.get()) {
                                Log.d(TAG, "Timer completed while near! Completing challenge.")
                                completeChallenge()
                            }
                        }
                    }.start()
                }
            } else {
                // Object is far
                if (isProximityNear.getAndSet(false)) {
                    // Take action when state changes from near to far
                    Log.d(TAG, "Object is FAR, canceling timer")
                    timer?.cancel()
                    if (!isProximityChallengeCompleted) {
                        Toast.makeText(context, "Sensor uncovered too soon.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun completeChallenge() {
        if (!isProximityChallengeCompleted) {
            isProximityChallengeCompleted = true
            completionListener?.onChallengeCompleted(ChallengeType.PROXIMITY)
        }
    }
}