package co.monveri.register.feature.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/** Launchers the Images tab's "+" chooser needs — camera capture or a gallery pick. */
data class ProductImagePicker(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit,
)

/**
 * Wires up camera-capture (with a runtime permission check + [FileProvider] temp file, matching
 * iOS's `UIImagePickerController` camera path) and the system Photo Picker (matching iOS's
 * `PhotosPicker` gallery path). [onImagePicked] receives the resulting content Uri either way —
 * the caller runs it through [ImageCompressor] before uploading.
 */
@Composable
fun rememberProductImagePicker(onImagePicked: (Uri) -> Unit): ProductImagePicker {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) onImagePicked(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createTempImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onImagePicked)
    }

    return remember(context) {
        ProductImagePicker(
            launchCamera = {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val uri = createTempImageUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            launchGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
    }
}

private fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "product_photos").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
