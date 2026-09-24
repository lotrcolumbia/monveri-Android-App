package co.monveri.register.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.monveri.register.data.repository.ProductImage
import co.monveri.register.design.components.EmptyState
import co.monveri.register.design.tokens.MonveriCornerRadius
import co.monveri.register.design.tokens.MonveriSpacing
import coil.compose.AsyncImage

/**
 * Product photos — grid with a "Primary" badge, matching iOS's `ProductImagesTab`. Upload/delete/
 * set-primary each take effect immediately (see [BackOfficeProductEditorViewModel]'s doc); there's
 * no "Save" step for this tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackOfficeProductImagesTab(
    state: BackOfficeProductEditorUiState,
    viewModel: BackOfficeProductEditorViewModel,
) {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<ProductImage?>(null) }

    val picker = rememberProductImagePicker { uri ->
        viewModel.onImagePicked(context.contentResolver, uri)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.images.isEmpty() && !state.isUploadingImage) {
            EmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Filled.Photo,
                title = "No photos yet",
                message = "Take a photo or choose one from your library.",
                actionLabel = "Add photo",
                onAction = { showChooser = true },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(IMAGE_GRID_COLUMNS),
                contentPadding = PaddingValues(MonveriSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(MonveriSpacing.Sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.images, key = { it.imageId }) { image ->
                    ProductImageTile(
                        image = image,
                        url = viewModel.resolveImageUrl(image.filename),
                        onClick = { menuTarget = image },
                    )
                }
                if (state.isUploadingImage) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showChooser = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(MonveriSpacing.Lg),
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo")
        }
    }

    if (showChooser) {
        CameraOrLibrarySheet(
            onCamera = {
                showChooser = false
                picker.launchCamera()
            },
            onLibrary = {
                showChooser = false
                picker.launchGallery()
            },
            onDismiss = { showChooser = false },
        )
    }

    menuTarget?.let { image ->
        ImageActionMenu(
            image = image,
            onSetPrimary = {
                viewModel.onSetPrimaryImage(image.imageId)
                menuTarget = null
            },
            onDelete = {
                viewModel.onDeleteImage(image.imageId)
                menuTarget = null
            },
            onDismiss = { menuTarget = null },
        )
    }
}

@Composable
private fun ProductImageTile(image: ProductImage, url: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(MonveriCornerRadius.Md))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = url,
            contentDescription = image.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (image.isPrimary) {
            Text(
                text = "Primary",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(MonveriSpacing.Xs)
                    .clip(RoundedCornerShape(MonveriCornerRadius.Sm))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = MonveriSpacing.Sm, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ImageActionMenu(
    image: ProductImage,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        if (!image.isPrimary) {
            DropdownMenuItem(text = { Text("Set as primary") }, onClick = onSetPrimary)
        }
        DropdownMenuItem(text = { Text("Delete") }, onClick = onDelete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraOrLibrarySheet(onCamera: () -> Unit, onLibrary: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
            text = { Text("Take Photo") },
            onClick = onCamera,
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
            text = { Text("Choose from Library") },
            onClick = onLibrary,
        )
    }
}

private const val IMAGE_GRID_COLUMNS = 3
