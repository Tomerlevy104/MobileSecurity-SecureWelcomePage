package com.example.securewelcomepage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Barcode scanning activity
 * Displays a camera preview and searches for barcodes in real time
 */
class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var cancelButton: MaterialButton
    private lateinit var cameraExecutor: ExecutorService

    // Listens for permission request
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission accepted
            startCamera()
        } else {
            // Permission deny
            Toast.makeText(
                this,
                "Camera permission is required for barcode scanning",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        cameraPreview = findViewById(R.id.cameraPreview)
        cancelButton = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            finish()
        }

        // Camera operation startup
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // Camera provider
                val cameraProvider = cameraProviderFuture.get()

                // Define the preview use case
                val preview = Preview.Builder().build()
                preview.surfaceProvider = cameraPreview.surfaceProvider

                // Image analysis use case definition
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                // Setting up the image analyzer
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }

                // Select rear camera as default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                Toast.makeText(this, "Could not initialize camera", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            // Image processing
            val scanner = BarcodeScanning.getClient()
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    // Processing recognized barcodes
                    if (barcodes.isNotEmpty()) {
                        for (barcode in barcodes) {
                            // Get the barcode value
                            val barcodeValue = barcode.rawValue
                            if (barcodeValue != null) {
                                // Return the scanned barcode to MainActivity
                                val resultIntent = Intent().apply {
                                    putExtra("BARCODE_RESULT", barcodeValue)
                                }
                                setResult(RESULT_OK, resultIntent)

                                // Display a message with the scanned value
                                runOnUiThread {
                                    Toast.makeText(
                                        this,
                                        "Scanned: $barcodeValue",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                finish()
                                break
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "BarcodeScannerActivity"
    }
}