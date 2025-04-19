package com.example.securewelcomepage.managers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.securewelcomepage.interfaces.IChallengeManager
import com.example.securewelcomepage.interfaces.IOnChallengeCompletedListener
import com.example.securewelcomepage.utilities.ChallengeType

class BluetoothManager(
    private val context: Context,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val enableLauncher: ActivityResultLauncher<Intent>
) : IChallengeManager {

    private val TAG = "BluetoothManager"
    // Enter Your Specific Device
//    private val TARGET_DEVICE_ADDRESS = "XX:XX:XX:XX:XX:XX"
    private val TARGET_DEVICE_ADDRESS = "DC:53:92:70:04:04"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var completed = false
    private var listener: IOnChallengeCompletedListener? = null
    private var isDiscoveryActive = false // Flag to track discovery status

    // BroadcastReceiver for Bluetooth connections and discovery
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // When a Bluetooth device physically connects to our device
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val deviceAddress = it.address
                        Log.d(TAG, "Device CONNECTED: $deviceAddress")

                        if (deviceAddress == TARGET_DEVICE_ADDRESS) {
                            Log.d(TAG, "Target device connected! Completing challenge.")
                            setCompleted(true)
                            notifyListener()
                        }
                    }
                }

                // When a Bluetooth device disconnects
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val deviceAddress = it.address
                        Log.d(TAG, "Device DISCONNECTED: $deviceAddress")

                        if (deviceAddress == TARGET_DEVICE_ADDRESS) {
                            setCompleted(false)
                            notifyListener()
                        }
                    }
                }

                BluetoothDevice.ACTION_FOUND -> {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        device?.let {
                            val deviceName = it.name ?: "Unknown"
                            val deviceAddress = it.address
                            Log.d(TAG, "Device found: $deviceName - $deviceAddress")

                            if (deviceAddress == TARGET_DEVICE_ADDRESS) {
                                Log.d(TAG, "Target device found in scan but waiting for connection")
                            }
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Scan finished - starts a new scan if the challenge is not completed
                    if (!isCompleted()) {
                        Log.d(
                            TAG,
                            "Discovery finished without finding connected target device, restarting..."
                        )
                        startBluetoothDiscovery()
                    } else {
                        isDiscoveryActive = false
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    // Bluetooth state changed (up/down)
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                    if (state == BluetoothAdapter.STATE_ON) {
                        Log.d(TAG, "Bluetooth turned ON")
                        startBluetoothDiscovery()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        Log.d(TAG, "Bluetooth turned OFF")
                    }
                }
            }
        }
    }

    init {
        // Get Bluetooth adapter
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        // Register required broadcast receivers with ALL needed actions
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
    }

    override fun startChallenge() {
        Log.d(TAG, "Starting Bluetooth challenge")
        if (isCompleted()) {
            // Already completed, notify listener
            notifyListener()
            return
        }

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device")
            return
        }
        // Request permissions if necessary
        requestBluetoothPermissions()
        checkForActiveConnectionWithTargetDevice() // Check if the device is already connected
    }

    override fun stopChallenge() {
        Log.d(TAG, "Stopping Bluetooth challenge")

        // Cancel discovery if active
        if (isDiscoveryActive && bluetoothAdapter?.isDiscovering == true) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter?.cancelDiscovery()
                    }
                } else {
                    bluetoothAdapter?.cancelDiscovery()
                }
                isDiscoveryActive = false
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Bluetooth discovery: ${e.message}")
            }
        }
    }

    override fun isCompleted(): Boolean {
        return completed
    }

    override fun registerCompletionListener(listener: IOnChallengeCompletedListener) {
        this.listener = listener
    }

    // Check if there is already an active connection to the target device
    private fun checkForActiveConnectionWithTargetDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission")
                    return
                }
            }

            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    ?: return

            val connectedDevices = mutableListOf<BluetoothDevice>()

            try {
                connectedDevices.addAll(bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET))
            } catch (e: Exception) {
                Log.e(TAG, "Error checking HEADSET profile: ${e.message}")
            }

            try {
                connectedDevices.addAll(bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP))
            } catch (e: Exception) {
                Log.e(TAG, "Error checking A2DP profile: ${e.message}")
            }

            // Check if one of the connected devices is the target device
            for (device in connectedDevices) {
                if (device.address == TARGET_DEVICE_ADDRESS) {
                    Log.d(TAG, "Target device already connected! Completing challenge.")
                    setCompleted(true)
                    Toast.makeText(context, "Bluetooth device connected!", Toast.LENGTH_SHORT)
                        .show()
                    notifyListener()
                    return
                }
            }

            Log.d(TAG, "Target device not currently connected, waiting for connection...")

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for active connection: ${e.message}")
        }
    }

    private fun requestBluetoothPermissions() {
        // Check and request permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ permissions
            val bluetoothPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )

            val allGranted = bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                // Permissions already granted, check if Bluetooth is enabled
                checkBluetoothEnabled()
            } else {
                // Request permissions
                permissionLauncher.launch(bluetoothPermissions)
            }
        } else {
            // Pre-Android 12 permissions
            val oldBluetoothPermissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            val allGranted = oldBluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                // Permissions already granted, check if Bluetooth is enabled
                checkBluetoothEnabled()
            } else {
                // Request permissions
                permissionLauncher.launch(oldBluetoothPermissions)
            }
        }
    }

    private fun checkBluetoothEnabled() {
        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled == true) {
            // Bluetooth is enabled, start discovery
            startBluetoothDiscovery()
        } else {
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableLauncher.launch(enableBtIntent)
        }
    }

    fun startBluetoothDiscovery() {
        Log.d(TAG, "Starting Bluetooth discovery")

        // Check if already completed
        if (isCompleted()) {
            Log.d(TAG, "Challenge already completed")
            notifyListener()
            return
        }

        checkForActiveConnectionWithTargetDevice()

        try {
            // Check if already discovering
            if (bluetoothAdapter?.isDiscovering == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothAdapter?.cancelDiscovery()
                    }
                } else {
                    bluetoothAdapter?.cancelDiscovery()
                }
            }

            // Start a new discovery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (bluetoothAdapter?.startDiscovery() == true) {
                        isDiscoveryActive = true
                        Log.d(TAG, "Bluetooth discovery started")
                    }
                }
            } else {
                if (bluetoothAdapter?.startDiscovery() == true) {
                    isDiscoveryActive = true
                    Log.d(TAG, "Bluetooth discovery started")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bluetooth discovery: ${e.message}")
        }
    }

    private fun notifyListener() {
        Log.d(TAG, "Notifying listener, challenge completed: ${isCompleted()}")
        listener?.onChallengeCompleted(ChallengeType.BLUETOOTH)
    }

    private fun setCompleted(completed: Boolean) {
        this.completed = completed
        Log.d(TAG, "Challenge completion set to: $completed")
    }
}