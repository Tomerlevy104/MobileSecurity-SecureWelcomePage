package com.example.securewelcomepage

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.managers.*
import com.example.securewelcomepage.utilities.ChallengeType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

class MainActivity : AppCompatActivity(), IOnChallengeCompletedListener {

    // UI Elements
    private lateinit var proximityLockIcon: AppCompatImageView
    private lateinit var timeLockIcon: AppCompatImageView
    private lateinit var bluetoothLockIcon: AppCompatImageView
    private lateinit var brightnessLockIcon: AppCompatImageView
    private lateinit var barcodeLockIcon: AppCompatImageView
    private lateinit var statusTextView: MaterialTextView
    private lateinit var unlockButton: MaterialButton
    private lateinit var scanBarcodeButton: MaterialButton

    // Challenge Managers
    private lateinit var proximityManager: ProximityManager
    private lateinit var timeManager: TimeManager
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var brightnessManager: BrightnessManager
    private lateinit var barcodeManager: BarcodeManager

    // Bluetooth permission request launcher
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12+
            when {
                permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false) &&
                        permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false) -> {
                    // Permissions granted, start discovery
                    Log.d("Bluetooth", "Permissions granted, starting discovery")
                    bluetoothManager.startBluetoothDiscovery()
                }
                else -> {
                    // Permissions denied, show message
                    Toast.makeText(
                        this,
                        "Bluetooth permissions are required for the Bluetooth challenge",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            // For older Android versions
            when {
                permissions.getOrDefault(Manifest.permission.BLUETOOTH, false) &&
                        permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // Permissions granted, start discovery
                    Log.d("Bluetooth", "Permissions granted, starting discovery")
                    bluetoothManager.startBluetoothDiscovery()
                }
                else -> {
                    // Permissions denied, show message
                    Toast.makeText(
                        this,
                        "Bluetooth and Location permissions are required for the Bluetooth challenge",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Bluetooth enable request launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth is now enabled, start discovery
            Log.d("Bluetooth", "Bluetooth enabled, starting discovery")
            bluetoothManager.startBluetoothDiscovery()
        } else {
            // User refused to enable Bluetooth
            Toast.makeText(
                this,
                "Bluetooth must be enabled for the Bluetooth challenge",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Activity result launcher for barcode scanning
    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        barcodeManager.processBarcodeResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeViews()

        // Initialize managers
        initializeManagers()

        // Set up button listeners
        setupButtonListeners()

        // Start challenge monitoring
        startChallenges()
    }

    private fun initializeViews() {
        proximityLockIcon = findViewById(R.id.proximityLockIcon)
        timeLockIcon = findViewById(R.id.timeLockIcon)
        bluetoothLockIcon = findViewById(R.id.bluetoothLockIcon)
        brightnessLockIcon = findViewById(R.id.brightnessLockIcon)
        barcodeLockIcon = findViewById(R.id.barcodeLockIcon)
        statusTextView = findViewById(R.id.statusTextView)
        unlockButton = findViewById(R.id.unlockButton)
        scanBarcodeButton = findViewById(R.id.scanBarcodeButton)
    }

    private fun initializeManagers() {
        proximityManager = ProximityManager(this)
        timeManager = TimeManager(context = this)
        bluetoothManager = BluetoothManager(this, bluetoothPermissionLauncher, bluetoothEnableLauncher)
        brightnessManager = BrightnessManager(this)
        barcodeManager = BarcodeManager(this, barcodeLauncher)

        proximityManager.registerCompletionListener(this)
        timeManager.registerCompletionListener(this)
        bluetoothManager.registerCompletionListener(this)
        brightnessManager.registerCompletionListener(this)
        barcodeManager.registerCompletionListener(this)
    }

    private fun setupButtonListeners() {
        // Set up the unlock button
        unlockButton.setOnClickListener {
            if (areAllChallengesCompleted()) {
                // Navigate to success screen
                val intent = Intent(this, SuccessActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Complete all challenges first!", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up the scan barcode button
        scanBarcodeButton.setOnClickListener {
            barcodeManager.launchBarcodeScanner()
        }
    }

    private fun startChallenges() {
        proximityManager.startChallenge()
        timeManager.startChallenge()
        bluetoothManager.startChallenge()
        brightnessManager.startChallenge()
        barcodeManager.startChallenge()
    }

    override fun onChallengeCompleted(challengeType: ChallengeType) {
        // Update UI based on challenge type
        when (challengeType) {
            //Proximity
            ChallengeType.PROXIMITY -> proximityLockIcon.setImageResource(
                if (proximityManager.isCompleted()) {
                    Toast.makeText(this, "Proximity challenge completed", Toast.LENGTH_SHORT).show()

                    R.drawable.unlocked_icon
                }
                else
                    R.drawable.lock_icon)

            // Time
            ChallengeType.TIME -> timeLockIcon.setImageResource(
                if (timeManager.isCompleted()){
                    Toast.makeText(this, "Time challenge completed", Toast.LENGTH_SHORT).show()
                    R.drawable.unlocked_icon
                }
                else
                    R.drawable.lock_icon)

            // Bluetooth
            ChallengeType.BLUETOOTH -> bluetoothLockIcon.setImageResource(
                if (bluetoothManager.isCompleted()) {
                    Toast.makeText(this, "Bluetooth challenge completed", Toast.LENGTH_SHORT).show()
                    R.drawable.unlocked_icon
                }
                else
                    R.drawable.lock_icon)

            // Brightness
            ChallengeType.BRIGHTNESS -> brightnessLockIcon.setImageResource(
                if (brightnessManager.isCompleted()){
                    Toast.makeText(this, "Brightness challenge completed", Toast.LENGTH_SHORT).show()
                    R.drawable.unlocked_icon
                }
                else
                    R.drawable.lock_icon)

            // Barcode
            ChallengeType.BARCODE -> barcodeLockIcon.setImageResource(
                if (barcodeManager.isCompleted()) {
                    Toast.makeText(this, "Barcode challenge completed", Toast.LENGTH_SHORT).show()
                    R.drawable.unlocked_icon
                }
                else
                    R.drawable.lock_icon)
        }
        updateStatus()
    }

    // Update status text and unlock button state
    private fun updateStatus() {
        val completedCount = countCompletedChallenges()
        statusTextView.text = getString(R.string.challenges_status, completedCount)

        // Enable unlock button if all challenges are completed
        unlockButton.isEnabled = areAllChallengesCompleted()
    }

    private fun countCompletedChallenges(): Int {
        var count = 0
        if (proximityManager.isCompleted())
            count++
        if (timeManager.isCompleted())
            count++
        if (bluetoothManager.isCompleted())
            count++
        if (brightnessManager.isCompleted())
            count++
        if (barcodeManager.isCompleted())
            count++
        return count
    }

    private fun areAllChallengesCompleted(): Boolean {
        return proximityManager.isCompleted() &&
                timeManager.isCompleted() &&
                bluetoothManager.isCompleted() &&
                brightnessManager.isCompleted() &&
                barcodeManager.isCompleted()
    }

    override fun onResume() {
        super.onResume()
        // Restart challenges when activity resumes
        startChallenges()
    }

    override fun onPause() {
        super.onPause()
        // Stop challenges when activity pauses
        proximityManager.stopChallenge()
        timeManager.stopChallenge()
        bluetoothManager.stopChallenge()
        brightnessManager.stopChallenge()
        barcodeManager.stopChallenge()
    }

    override fun onDestroy() {
        super.onDestroy()
        proximityManager.stopChallenge()
        timeManager.stopChallenge()
        bluetoothManager.stopChallenge()
        brightnessManager.stopChallenge()
        barcodeManager.stopChallenge()
    }
}