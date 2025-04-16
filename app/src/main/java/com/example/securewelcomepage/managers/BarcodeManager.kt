package com.example.securewelcomepage.managers

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.example.securewelcomepage.BarcodeScannerActivity
import com.example.securewelcomepage.interfaces.IChallengeManager
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.utilities.ChallengeType

class BarcodeManager(
    private val activity: AppCompatActivity,
    private val barcodeLauncher: ActivityResultLauncher<Intent>
) : IChallengeManager {

    private val TAG = "BarcodeManager"
    private val TARGET_BARCODE = "7290002331124" // Barcode of water taste apple
    private var isBarcodeChallengeCompleted = false
    private var completionListener: IOnChallengeCompletedListener? = null

    override fun startChallenge() {
        // Nothing to start automatically - barcode scanning is triggered by user
    }

    override fun stopChallenge() {
        // Nothing to stop
    }

    override fun isCompleted(): Boolean {
        return isBarcodeChallengeCompleted
    }

    override fun registerCompletionListener(listener: IOnChallengeCompletedListener) {
        completionListener = listener
    }

    // Launch barcode scanner
    fun launchBarcodeScanner() {
        val intent = Intent(activity, BarcodeScannerActivity::class.java)
        barcodeLauncher.launch(intent)
    }

    // Process barcode scan result
    fun processBarcodeResult(resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val scannedBarcode = intent?.getStringExtra("BARCODE_RESULT")
            Log.d(TAG, "Scanned barcode: $scannedBarcode")

            if (scannedBarcode == TARGET_BARCODE) {
                completeChallenge()
            } else {
                Toast.makeText(activity, "Wrong barcode scanned", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun completeChallenge() {
        if (!isBarcodeChallengeCompleted) {
            isBarcodeChallengeCompleted = true
            completionListener?.onChallengeCompleted(ChallengeType.BARCODE)
        }
    }
}