package com.example.securewelcomepage.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.securewelcomepage.interfaces.IChallengeManager
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.utilities.ChallengeType

class BrightnessManager(private val context: Context) : IChallengeManager {

    private val TAG = "BrightnessManager"
    private var isBrightnessChallengeCompleted = false
    private var toastShown = false
    private var completionListener: IOnChallengeCompletedListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val brightnessCheckRunnable = object : Runnable {
        override fun run() {
            checkBrightnessChallenge()
            handler.postDelayed(this, 1000) // Check every second
        }
    }

    override fun startChallenge() {
        toastShown = false
        checkBrightnessChallenge()
        handler.post(brightnessCheckRunnable)
    }

    override fun stopChallenge() {
        handler.removeCallbacks(brightnessCheckRunnable)
    }

    override fun isCompleted(): Boolean {
        return isBrightnessChallengeCompleted
    }

    override fun registerCompletionListener(listener: IOnChallengeCompletedListener) {
        completionListener = listener
    }

    // Check if screen brightness is at maximum
    private fun checkBrightnessChallenge() {
        try {
            val brightness =
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val maxBrightness = 255 // Maximum brightness value

            if (brightness == maxBrightness) {
                if (!toastShown) {
                    toastShown = true
                }
                completeChallenge()
            } else {
                // Reset value if brightness is no longer at maximum
                if (isBrightnessChallengeCompleted == true) {
                    isBrightnessChallengeCompleted = false
                    toastShown = false
                    completionListener?.onChallengeCompleted(ChallengeType.BRIGHTNESS)
                }
            }
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error getting brightness: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun completeChallenge() {
        if (!isBrightnessChallengeCompleted) {
            isBrightnessChallengeCompleted = true
            completionListener?.onChallengeCompleted(ChallengeType.BRIGHTNESS)
        }
    }
}