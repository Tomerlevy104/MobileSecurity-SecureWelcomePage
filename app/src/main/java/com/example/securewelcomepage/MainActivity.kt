package com.example.securewelcomepage

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), SensorEventListener {

    // UI Elements
    private lateinit var proximityLockIcon: AppCompatImageView
    private lateinit var timeLockIcon: AppCompatImageView
    private lateinit var bluetoothLockIcon: AppCompatImageView
    private lateinit var brightnessLockIcon: AppCompatImageView
    private lateinit var barcodeLockIcon: AppCompatImageView
    private lateinit var statusTextView: MaterialTextView
    private lateinit var unlockButton: MaterialButton
    private lateinit var scanBarcodeButton: MaterialButton

    // Sensors and Managers
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    // Challenge Status
    private var isProximityChallengeCompleted = false
    private var isTimeChallengeCompleted = false
    private var isBluetoothChallengeCompleted = false
    private var isBrightnessChallengeCompleted = false
    private var isBarcodeChallengeCompleted = false

    // Constants
    // Replace to your Bluetooth device MAC address
    private val TARGET_DEVICE_ADDRESS =
        "DC:53:92:70:04:04" // My specific Bluetooth device MAC address
    private val TARGET_BARCODE = "7290002331124" // Barcode of water taste apple
    private val PROXIMITY_THRESHOLD = 0f // Sensor returns 0 when object is near
    private val PROXIMITY_DURATION_MS = 10000L // 10 seconds

    // Timer for proximity sensor challenge
    private val isProximityNear = AtomicBoolean(false)
    private var proximityTimer: CountDownTimer? = null

    // Handler for periodic time check
    private val handler = Handler(Looper.getMainLooper())
    private val timeCheckRunnable = object : Runnable {
        override fun run() {
            checkTimeChallenge()
            handler.postDelayed(this, 1000) // Check every second
        }
    }

    /*
     Bluetooth permission request launcher
     Asks the user for permission to use Bluetooth
     This pops up the window asking "Do you want to allow the app to use Bluetooth?"
     */
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false) &&
                    permissions.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false) -> {
                // Permissions granted, start discovery
                Log.d("Bluetooth", "Permissions granted, starting discovery")
                startBluetoothDiscovery()
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
    }

    /*
     Bluetooth enable request launcher
     Asks the user to turn on Bluetooth
     */
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth is now enabled, start discovery
            Log.d("Bluetooth", "Bluetooth enabled, starting discovery")
            startBluetoothDiscovery()
        } else {
            // User refused to enable Bluetooth
            Toast.makeText(
                this,
                "Bluetooth must be enabled for the Bluetooth challenge",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // This receiver listens for different Bluetooth actions and responds accordingly
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Extract the action type from the received intent
            val action = intent.action
            // Log the received action for debugging purposes
            Log.d("Bluetooth", "Received action: $action")

            // Handle different Bluetooth actions based on the action type
            when (action) {
                // ** When a Bluetooth device is connected to this device **
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    // Check for permission before accessing Bluetooth device information
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        // Exit if permission is not granted
                        return
                    }

                    // Get the connected device from the intent extras
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    // Process the device if it's not null
                    device?.let {
                        // Get the device MAC address
                        val deviceAddress = it.address
                        // Log the connection event
                        Log.d("Bluetooth", "Device CONNECTED: $deviceAddress")

                        // Check if the connected device is our target device
                        if (deviceAddress == TARGET_DEVICE_ADDRESS) {
                            // Log success and complete the Bluetooth challenge
                            Log.d("Bluetooth", "Target device connected! Completing challenge.")
                            completeBluetoothChallenge()
                        }
                    }
                }

                // ** When the Bluetooth adapter state changes (e.g., on/off) **
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    // Get the new state of the Bluetooth adapter
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    // Log the state change
                    Log.d("Bluetooth", "Bluetooth state changed to: $state")

                    // If Bluetooth has just been turned on
                    if (state == BluetoothAdapter.STATE_ON) {
                        // Log that Bluetooth is now on
                        Log.d("Bluetooth", "Bluetooth turned ON")
                        // Start scanning for devices
                        startBluetoothDiscovery()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        // Log that Bluetooth is now off
                        Log.d("Bluetooth", "Bluetooth turned OFF")
                    }
                }
            }
        }
    }

    // Activity result launcher for barcode scanning
    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedBarcode = result.data?.getStringExtra("BARCODE_RESULT")
            if (scannedBarcode == TARGET_BARCODE) {
                completeBarcodeScanChallenge()
            } else {
                Toast.makeText(this, "Wrong barcode scanned", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        initializeViews()

        // Initialize sensors and managers
        initializeSensors()

        // Set up button listeners
        setupButtonListeners()

        // Start checking challenges
        startChallengeChecking()
    }

    private fun initializeViews() {
        // Find views
        proximityLockIcon = findViewById(R.id.proximityLockIcon)
        timeLockIcon = findViewById(R.id.timeLockIcon)
        bluetoothLockIcon = findViewById(R.id.bluetoothLockIcon)
        brightnessLockIcon = findViewById(R.id.brightnessLockIcon)
        barcodeLockIcon = findViewById(R.id.barcodeLockIcon)
        statusTextView = findViewById(R.id.statusTextView)
        unlockButton = findViewById(R.id.unlockButton)
        scanBarcodeButton = findViewById(R.id.scanBarcodeButton)
    }

    private fun initializeSensors() {
        // Initialize sensor manager and get proximity sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        // Check if device has proximity sensor
        if (proximitySensor == null) {
            Toast.makeText(this, "Device doesn't have proximity sensor", Toast.LENGTH_LONG).show()
        }

        // Initialize Bluetooth
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
        }
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
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeLauncher.launch(intent)
        }
    }

    private fun startChallengeChecking() {
        // Register proximity sensor listener
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Start time challenge checking
        handler.post(timeCheckRunnable)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)

        // Setup Bluetooth and start discovery if enabled
        setupBluetooth()

        // Check brightness immediately and periodically
        checkBrightnessChallenge()
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkBrightnessChallenge()
                handler.postDelayed(this, 1000) // Check every second
            }
        }, 1000)
    }

    // Setup Bluetooth and handle permissions
    private fun setupBluetooth() {
        Log.d("Bluetooth", "Setting up Bluetooth")

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Bluetooth not supported on this device")
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Check if Bluetooth permissions are granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.d("Bluetooth", "Requesting Bluetooth permissions")
            // Request Bluetooth permissions
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
            return
        }

        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled == true) {
            // Start discovery directly
            Log.d("Bluetooth", "Bluetooth is enabled, starting discovery")
            startBluetoothDiscovery()
        } else {
            // Request to enable Bluetooth
            Log.d("Bluetooth", "Bluetooth is disabled, requesting to enable")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    // Starts the Bluetooth discovery process to scan for nearby devices
    private fun startBluetoothDiscovery() {
        try {
            // Check if the app has the necessary Bluetooth scan permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("Bluetooth", "Missing BLUETOOTH_SCAN permission")
                return
            }

            // Create a delayed operation to start the discovery
            Handler(Looper.getMainLooper()).postDelayed({
                // Check the permission again in case it was revoked during the delay
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Attempt to start the discovery process
                    // Returns true if discovery starts successfully, false otherwise
                    val started = bluetoothAdapter?.startDiscovery() ?: false

                    if (started) {
                        // Log success and notify the user that scanning has begun
                        Log.d("Bluetooth", "Discovery started successfully")
                        Toast.makeText(
                            this,
                            "Scanning for Bluetooth devices...",
                            Toast.LENGTH_SHORT
                        ).show()

                        checkPairedDevices()
                    } else {
                        // Log failure and notify the user if discovery couldn't start
                        // This could happen if Bluetooth hardware is busy or malfunctioning
                        Log.e("Bluetooth", "Failed to start discovery")
//                        Toast.makeText(
//                            this,
//                            "Failed to start Bluetooth scanning",
//                            Toast.LENGTH_SHORT
//                        ).show()
                    }
                }
            }, 1000) // Wait for 1 second before starting discovery

        } catch (e: Exception) {
            // Catch any unexpected exceptions that might occur during the process
            // This is a general safety net for any unforeseen errors
            Log.e("Bluetooth", "Error starting discovery: ${e.message}")
            e.printStackTrace()
        }
    }

    // ADDED: New function to check already paired devices
    private fun checkPairedDevices() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("Bluetooth", "Missing BLUETOOTH_CONNECT permission")
            return
        }

        try {
            val pairedDevices = bluetoothAdapter?.bondedDevices
            Log.d("Bluetooth", "Checking ${pairedDevices?.size ?: 0} paired devices")

            pairedDevices?.forEach { device ->
                val deviceName = device.name ?: "Unknown"
                val deviceAddress = device.address

                Log.d("Bluetooth", "Paired device: $deviceName, $deviceAddress")

                if (deviceAddress == TARGET_DEVICE_ADDRESS) {
                    Log.d(
                        "Bluetooth",
                        "Target device found in paired devices! Completing challenge."
                    )
                    completeBluetoothChallenge()
                }
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error checking paired devices: ${e.message}")
            e.printStackTrace()
        }
    }

    // Check if minutes are divisible by 2
    private fun checkTimeChallenge() {
        val calendar = Calendar.getInstance()
        val minutes = calendar.get(Calendar.MINUTE)

        if (minutes % 2 == 0) {
            completeTimeChallenge()
        } else if (isTimeChallengeCompleted) {
            // Reset if the time is no longer valid
            isTimeChallengeCompleted = false
            timeLockIcon.setImageResource(R.drawable.lock_icon)
            updateStatus()
        }
    }

    // Check if screen brightness is at maximum
    private fun checkBrightnessChallenge() {
        try {
            val brightness =
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val maxBrightness = 255 // Maximum brightness value on most Android devices

            if (brightness == maxBrightness) {
                completeBrightnessChallenge()
            } else if (isBrightnessChallengeCompleted) {
                // Reset if brightness is no longer at maximum
                isBrightnessChallengeCompleted = false
                brightnessLockIcon.setImageResource(R.drawable.lock_icon)
                updateStatus()
            }
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }
    }

    // Mark Proximity Challenge as completed and update UI
    private fun completeProximityChallenge() {
        if (!isProximityChallengeCompleted) {
            isProximityChallengeCompleted = true
            proximityLockIcon.setImageResource(R.drawable.unlocked_icon)
            updateStatus()
        }
    }

    // Mark Time Challenge as completed and update UI
    private fun completeTimeChallenge() {
        if (!isTimeChallengeCompleted) {
            isTimeChallengeCompleted = true
            timeLockIcon.setImageResource(R.drawable.unlocked_icon)
            updateStatus()
        }
    }

    // Mark Bluetooth Challenge as completed and update UI
    private fun completeBluetoothChallenge() {
        Log.d("Bluetooth", "Attempting to complete Bluetooth challenge")

        if (!isBluetoothChallengeCompleted) {
            isBluetoothChallengeCompleted = true

            // Ensure UI updates happen on the UI thread
            runOnUiThread {
                bluetoothLockIcon.setImageResource(R.drawable.unlocked_icon)
                Toast.makeText(this, "Bluetooth challenge completed!", Toast.LENGTH_SHORT).show()
                updateStatus()
            }

            Log.d("Bluetooth", "Bluetooth challenge completed successfully")

            // Cancel discovery to save battery after challenge is completed
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } else {
            Log.d("Bluetooth", "Bluetooth challenge already completed")
        }
    }

    // Mark Brightness Challenge as completed and update UI
    private fun completeBrightnessChallenge() {
        if (!isBrightnessChallengeCompleted) {
            isBrightnessChallengeCompleted = true
            brightnessLockIcon.setImageResource(R.drawable.unlocked_icon)
            updateStatus()
        }
    }

    // Mark Barcode Scan Challenge as completed and update UI
    private fun completeBarcodeScanChallenge() {
        if (!isBarcodeChallengeCompleted) {
            isBarcodeChallengeCompleted = true
            barcodeLockIcon.setImageResource(R.drawable.unlocked_icon)
            updateStatus()
        }
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
        if (isProximityChallengeCompleted)
            count++
        if (isTimeChallengeCompleted)
            count++
        if (isBluetoothChallengeCompleted)
            count++
        if (isBrightnessChallengeCompleted)
            count++
        if (isBarcodeChallengeCompleted)
            count++
        return count
    }

    private fun areAllChallengesCompleted(): Boolean {
        return isProximityChallengeCompleted &&
                isTimeChallengeCompleted &&
                isBluetoothChallengeCompleted &&
                isBrightnessChallengeCompleted &&
                isBarcodeChallengeCompleted
    }

    // SensorEventListener implementations
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            Log.d("ProximitySensor", "Raw Distance: $distance, Threshold: $PROXIMITY_THRESHOLD")

            // Object is near
            if (distance < 4f) {
                if (!isProximityNear.getAndSet(true)) {
                    // Only show toast and start timer when state changes from far to near
                    Toast.makeText(this, "Object detected near sensor!", Toast.LENGTH_SHORT).show()
                    Log.d("ProximitySensor", "Object is NEAR")

                    // Start a more robust timer
                    proximityTimer?.cancel()
                    proximityTimer = object : CountDownTimer(PROXIMITY_DURATION_MS, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val secondsLeft = millisUntilFinished / 1000
                            Log.d("ProximitySensor", "Keep covered: $secondsLeft seconds left")
                        }

                        override fun onFinish() {
                            if (isProximityNear.get()) {
                                Log.d(
                                    "ProximitySensor",
                                    "Timer completed while near! Completing challenge."
                                )
                                runOnUiThread {
                                    completeProximityChallenge()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Proximity challenge completed!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }.start()
                }
            } else {
                // Object is far
                if (isProximityNear.getAndSet(false)) {
                    // Only take action when state changes from near to far
                    Log.d("ProximitySensor", "Object is FAR, canceling timer")
                    proximityTimer?.cancel()
                    if (!isProximityChallengeCompleted) {
                        Toast.makeText(this, "Sensor uncovered too soon.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBluetoothDiscovery()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permission denied. Cannot complete Bluetooth challenge.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (bluetoothAdapter?.isEnabled == true) {
                // Check if we need to restart discovery
                startBluetoothDiscovery()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensor listener to save battery
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister sensor listener
        sensorManager.unregisterListener(this)

        // Unregister Bluetooth receiver
        unregisterReceiver(bluetoothReceiver)

        // Remove callbacks
        handler.removeCallbacks(timeCheckRunnable)

        // Cancel proximity timer
        proximityTimer?.cancel()
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    }
}