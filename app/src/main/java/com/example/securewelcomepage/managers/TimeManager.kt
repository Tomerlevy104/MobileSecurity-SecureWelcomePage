package com.example.securewelcomepage.managers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.securewelcomepage.interfaces.IChallengeManager
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.utilities.ChallengeType
import java.util.Calendar

class TimeManager(private val context: Context) : IChallengeManager {

    private var toastShown = false
    private var isTimeChallengeCompleted = false
    private var completionListener: IOnChallengeCompletedListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeCheckRunnable = object : Runnable {
        override fun run() {
            checkTimeChallenge()
            handler.postDelayed(this, 1000) // Check every second
        }
    }

    override fun startChallenge() {
        toastShown = false
        handler.post(timeCheckRunnable)
    }

    override fun stopChallenge() {
        handler.removeCallbacks(timeCheckRunnable)
    }

    override fun isCompleted(): Boolean {
        return isTimeChallengeCompleted
    }

    override fun registerCompletionListener(listener: IOnChallengeCompletedListener) {
        completionListener = listener
    }

    // Check if minutes are divisible by 2
    private fun checkTimeChallenge() {
        val calendar = Calendar.getInstance()
        val minutes = calendar.get(Calendar.MINUTE)

        if (minutes % 2 == 0) {
            if (!toastShown) {
                toastShown = true
            }
            completeChallenge()
        } else if (isTimeChallengeCompleted) {
            // Reset if the time is no longer valid
            isTimeChallengeCompleted = false
            toastShown = false
            completionListener?.onChallengeCompleted(ChallengeType.TIME)
        }
    }

    private fun completeChallenge() {
        if (!isTimeChallengeCompleted) {
            isTimeChallengeCompleted = true
            completionListener?.onChallengeCompleted(ChallengeType.TIME)
        }
    }
}