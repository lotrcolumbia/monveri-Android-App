package co.monveri.register.feature.auth

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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.monveri.register.design.components.MonveriButton
import co.monveri.register.design.components.MonveriButtonVariant
import co.monveri.register.design.tokens.MonveriSpacing
import co.monveri.register.model.PairingPayload
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen QR scanner for first-run device pairing. Mirrors iOS's `PairingScannerView`:
 * scoped to QR only (unlike feature/catalog's multi-format product scanner) so retail barcodes
 * never trigger it, keeps the camera live and shows a banner on an unrecognized QR, and fires
 * [onScanned] exactly once — with the still-raw pairing string — on the first frame that decodes
 * to a valid [PairingPayload]. The caller re-parses it; [PairingScannerScreen] only needs the
 * model to gate what counts as "valid" here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGetImage::class)
@Composable
fun PairingScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
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

    var fired by remember { mutableStateOf(false) }
    var invalidScan by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan pairing QR") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (hasCameraPermission) {
                QrCameraPreview(
                    onQrDetected = { raw ->
                        if (fired) return@QrCameraPreview
                        if (PairingPayload.parse(raw) != null) {
                            fired = true
                            onScanned(raw)
                        } else {
                            invalidScan = true
                        }
                    },
                )
                ScanReticle(invalidScan = invalidScan)
            } else {
                CameraPermissionRationale(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCancel = onCancel,
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(onQrDetected: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

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
                // Wrap provider + analyzer + bindToLifecycle: any of them can throw if the camera
                // is held by another process or the permission was revoked between the launcher
                // callback and binding. We'd rather log and show the (empty) preview than crash.
                runCatching {
                    val cameraProvider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { ia ->
                            ia.setAnalyzer(executor) { proxy ->
                                processFrame(proxy, scanner, onQrDetected)
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { t -> Log.e("PairingScanner", "Camera bind failed", t) }
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
    onQrDetected: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (!raw.isNullOrBlank()) {
                onQrDetected(raw.trim())
            }
        }
        .addOnFailureListener { e -> Log.w("PairingScanner", "ML Kit failure", e) }
        .addOnCompleteListener { proxy.close() }
}

@Composable
private fun ScanReticle(invalidScan: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .padding(MonveriSpacing.Xl)
                .size(SCAN_FRAME_SIZE)
                .clip(RoundedCornerShape(MonveriSpacing.Md))
                .border(
                    width = SCAN_FRAME_BORDER,
                    color = Color.White.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(MonveriSpacing.Md),
                ),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonveriSpacing.Lg),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (invalidScan) {
            Box(
                modifier = Modifier
                    .padding(bottom = MonveriSpacing.Md)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = MonveriSpacing.Md, vertical = MonveriSpacing.Sm),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Text(
                        text = "Not a Monveri pairing code",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Text(
            text = "Scan the QR from your suite admin panel",
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
        Box(
            modifier = Modifier
                .padding(top = MonveriSpacing.Md, bottom = MonveriSpacing.Xl)
                .fillMaxWidth(),
        ) {
            Text(
                text = "Grant camera access to scan the pairing QR from your suite admin panel.",
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
                text = "Enter manually instead",
                onClick = onCancel,
                variant = MonveriButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val SCAN_FRAME_SIZE = 240.dp
private val SCAN_FRAME_BORDER = 2.dp
