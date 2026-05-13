package co.monveri.register.feature.catalog

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen barcode scanner — opens as its own route, dismisses back to the calling screen
 * with the raw barcode string handed to [onScanResult].
 *
 * Why not a ModalBottomSheet: full-screen camera + permission flow plays nicer as a route. The
 * iOS app uses a sheet because UIKit / SwiftUI sheets cover the full safe area on iPhone; on
 * Android the equivalent ergonomics come from the navigation graph.
 *
 * Debounce: the scanner waits for the same code to be read 3 frames in a row before firing
 * [onScanResult], then hands back exactly once — duplicate fires are squashed by the
 * `hasFired` guard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun BarcodeScannerScreen(
    onScanResult: (String) -> Unit,
    onCancel: () -> Unit,
    viewModel: BarcodeScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan barcode") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {

            if (hasCameraPermission) {
                CameraPreviewWithAnalyzer(
                    onBarcodeDetected = { code ->
                        viewModel.onBarcodeDetected(code) { stable ->
                            onScanResult(stable)
                        }
                    },
                )
                ScanFrameOverlay()
            } else {
                CameraPermissionRationale(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCancel = onCancel,
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { viewModel.reset() }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithAnalyzer(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { ia ->
                        ia.setAnalyzer(executor) { proxy ->
                            processFrame(proxy, scanner, onBarcodeDetected)
                        }
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processFrame(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            // Skip QR/data-matrix unless their raw text is a digit/SKU-looking string. We rely on
            // ML Kit's value-type heuristic to avoid spurious matches on dense QR-encoded payloads
            // (vCards, URLs) that aren't product identifiers.
            val first = barcodes.firstOrNull { it.isLikelyProductBarcode() }
            val raw = first?.rawValue
            if (!raw.isNullOrBlank()) {
                onBarcodeDetected(raw.trim())
            }
        }
        .addOnFailureListener { e -> Log.w("BarcodeScanner", "ML Kit failure", e) }
        .addOnCompleteListener { proxy.close() }
}

private fun Barcode.isLikelyProductBarcode(): Boolean = when (format) {
    Barcode.FORMAT_UPC_A,
    Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_CODE_39,
    Barcode.FORMAT_CODE_93,
    Barcode.FORMAT_CODE_128,
    Barcode.FORMAT_ITF -> true
    // QR is allowed only when the raw value looks like a SKU/UPC (digits/letters, no schema prefix).
    Barcode.FORMAT_QR_CODE -> rawValue?.let { !it.startsWith("http") && !it.contains('\n') } ?: false
    else -> false
}

@Composable
private fun ScanFrameOverlay() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .padding(MonveriSpacing.Xl)
                .size(SCAN_FRAME_SIZE)
                .clip(RoundedCornerShape(MonveriSpacing.Md))
                .background(Color(0x00FFFFFF)),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Point the camera at a barcode",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun CameraPermissionRationale(onRequest: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MonveriSpacing.Xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission needed",
            style = MaterialTheme.typography.titleLarge,
        )
        Box(modifier = Modifier
            .padding(top = MonveriSpacing.Md, bottom = MonveriSpacing.Xl)
            .fillMaxWidth(),
        ) {
            Text(
                text = "Grant camera access to scan product barcodes at checkout.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MonveriButton(
            text = "Grant access",
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.padding(top = MonveriSpacing.Md)) {
            MonveriButton(
                text = "Not now",
                onClick = onCancel,
                variant = MonveriButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val SCAN_FRAME_SIZE = 240.dp
