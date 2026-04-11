package com.example.butlermanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.butlermanager.data.QrData
import com.example.butlermanager.data.QrDataDatabase
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private const val TAG = "QrScannerScreen"

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(navController: NavController) {
    Log.d(TAG, "QrScannerScreen composable started")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { QrDataDatabase.getDatabase(context) }
    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        navController.popBackStack()
    }

    val cameraProviderFuture = remember {
        Log.d(TAG, "Getting camera provider instance")
        ProcessCameraProvider.getInstance(context)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            Log.d(TAG, "Camera permission granted: $granted")
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        Log.d(TAG, "Checking camera permission. Has permission: $hasCameraPermission")
        if (!hasCameraPermission) {
            Log.d(TAG, "Requesting camera permission")
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasCameraPermission) {
                Log.d(TAG, "Camera permission is granted, showing camera preview.")
                
                Text(
                    text = "Scan a device QR code to connect",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Box(
                    modifier = Modifier
                        .size(240.dp, 240.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            Log.d(TAG, "AndroidView factory called")
                            val previewView = PreviewView(ctx).apply {
                                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                                this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            val cameraExecutor = Executors.newSingleThreadExecutor()

                            cameraProviderFuture.addListener({
                                Log.d(TAG, "cameraProviderFuture listener triggered")
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = androidx.camera.core.Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val image = imageProxy.image
                                    if (image != null) {
                                        val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                                        val scanner = BarcodeScanning.getClient()
                                        scanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                if (barcodes.isNotEmpty()) {
                                                    barcodes.first().rawValue?.let { rawValue ->
                                                        Log.d(TAG, "QR Code detected: $rawValue")
                                                        try {
                                                            val qrData = Gson().fromJson(rawValue, QrData::class.java)
                                                            Log.d(TAG, "Parsed QR data: $qrData")

                                                            coroutineScope.launch {
                                                                db.qrDataDao().insert(qrData)
                                                            }

                                                            imageAnalysis.clearAnalyzer()
                                                            cameraProvider.unbindAll()
                                                            Log.d(TAG, "Navigating to connectProgress screen")
                                                            val qrDataJson = Gson().toJson(qrData)
                                                            val encodedQrData = URLEncoder.encode(qrDataJson, StandardCharsets.UTF_8.toString())
                                                            navController.navigate("connectProgress/$encodedQrData")
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "Error parsing QR code JSON", e)
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e -> Log.e(TAG, "Barcode scanning failed", e) }
                                            .addOnCompleteListener { imageProxy.close() }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                    .build()

                                previewView.post {
                                    Log.d(TAG, "Setting up camera use cases")
                                    val useCaseGroup = UseCaseGroup.Builder()
                                        .addUseCase(preview)
                                        .addUseCase(imageAnalysis)
                                        .build()
                                    try {
                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            selector,
                                            useCaseGroup
                                        )
                                        camera.cameraControl.setZoomRatio(2.0f)
                                        Log.d(TAG, "Camera bound to lifecycle")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to bind camera", e)
                                    }
                                }
                            }, ContextCompat.getMainExecutor(ctx))

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text("Camera permission is required to scan QR codes.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { navController.navigate("networkModeHome") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("View Devices in Network Mode")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing QrScannerScreen, unbinding camera.")
            cameraProviderFuture.get().unbindAll()
        }
    }
}

@Preview
@Composable
fun QrScannerScreenPreview() {
    QrScannerScreen(navController = rememberNavController())
}
