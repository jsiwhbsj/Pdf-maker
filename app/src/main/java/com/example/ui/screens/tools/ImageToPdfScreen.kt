package com.example.ui.screens.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.GalleryImageItem
import com.example.data.pdf.PageMargin
import com.example.data.pdf.PageOrientation
import com.example.data.pdf.PdfPageSize
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.ToolHeader
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBlack
import kotlinx.coroutines.launch

private data class SelectedGalleryPhoto(
    val uid: String,
    val uri: Uri,
    val previewBitmap: Bitmap,
    val rotationDegrees: Int = 0
)

private val SUPPORTED_IMAGE_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
    "image/*"
)

@Composable
fun ImageToPdfScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Saveable backing list of "uriString|rotationDegrees" survives Activity recreation during external Gallery picking
    var savedEntries by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var photos by remember { mutableStateOf<List<SelectedGalleryPhoto>>(emptyList()) }
    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    var fileName by rememberSaveable { mutableStateOf("Gallery_${System.currentTimeMillis() % 10000}") }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var isImportingImages by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPreviewAllDialog by remember { mutableStateOf(false) }
    var hasAutoOpenedGallery by rememberSaveable { mutableStateOf(false) }

    fun syncSavedEntries(list: List<SelectedGalleryPhoto>) {
        photos = list
        savedEntries = list.map { "${it.uri}|${it.rotationDegrees}" }
    }

    // Rehydrate preview bitmaps from persistent staged URIs if Compose recreated the screen
    LaunchedEffect(savedEntries) {
        if (photos.isEmpty() && savedEntries.isNotEmpty()) {
            isImportingImages = true
            val restored = mutableListOf<SelectedGalleryPhoto>()
            savedEntries.forEachIndexed { idx, entry ->
                val parts = entry.split("|")
                val uriStr = parts.firstOrNull() ?: return@forEachIndexed
                val rot = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val uri = Uri.parse(uriStr)
                repository.engine.loadAndStageGalleryImage(uri).onSuccess { (safeUri, bmp) ->
                    restored.add(
                        SelectedGalleryPhoto(
                            uid = "${safeUri}_${System.nanoTime()}_$idx",
                            uri = safeUri,
                            previewBitmap = bmp,
                            rotationDegrees = rot
                        )
                    )
                }
            }
            photos = restored
            isImportingImages = false
        }
    }

    fun handlePickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isImportingImages = true
            errorMsg = null
            val staged = mutableListOf<SelectedGalleryPhoto>()
            var failedCount = 0

            uris.forEachIndexed { idx, rawUri ->
                repository.engine.loadAndStageGalleryImage(rawUri)
                    .onSuccess { (safeUri, previewBmp) ->
                        staged.add(
                            SelectedGalleryPhoto(
                                uid = "${safeUri}_${System.nanoTime()}_$idx",
                                uri = safeUri,
                                previewBitmap = previewBmp,
                                rotationDegrees = 0
                            )
                        )
                    }
                    .onFailure {
                        failedCount++
                    }
            }

            if (staged.isNotEmpty()) {
                val wasEmpty = photos.isEmpty()
                syncSavedEntries(photos + staged)
                if (wasEmpty) activeIndex = 0
            }
            if (failedCount > 0) {
                errorMsg = if (staged.isEmpty()) {
                    "Could not open the selected image(s). Try choosing JPG, PNG, or WEBP via System Gallery or Folders."
                } else {
                    "$failedCount selected image(s) could not be decoded and were skipped."
                }
            }
            isImportingImages = false
        }
    }

    // 1) Android Photo Picker (multi-select)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        handlePickedUris(uris)
    }

    // 2) Device System Gallery chooser (ACTION_GET_CONTENT - works directly with ColorOS / Xiaomi / Samsung Gallery apps)
    val systemGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        handlePickedUris(uris)
    }

    // 3) Android Document / Folder picker fallback (ACTION_OPEN_DOCUMENT)
    val documentGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        handlePickedUris(uris)
    }

    fun launchSystemGallery() {
        runCatching { systemGalleryPicker.launch("image/*") }
            .recoverCatching { documentGalleryPicker.launch(SUPPORTED_IMAGE_MIME_TYPES) }
            .onFailure { errorMsg = it.message ?: "Unable to open system gallery." }
    }

    fun launchDeviceFoldersPicker() {
        runCatching { documentGalleryPicker.launch(SUPPORTED_IMAGE_MIME_TYPES) }
            .onFailure { errorMsg = it.message ?: "Unable to open device folders." }
    }

    fun launchPhotoPickerWithFallback() {
        val canUsePhotoPicker = runCatching {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        }.getOrDefault(false)

        if (canUsePhotoPicker) {
            val launched = runCatching {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }.isSuccess
            if (!launched) {
                launchSystemGallery()
            }
        } else {
            launchSystemGallery()
        }
    }

    // Auto-open picker once on fresh entry
    LaunchedEffect(Unit) {
        if (!hasAutoOpenedGallery && savedEntries.isEmpty() && photos.isEmpty()) {
            hasAutoOpenedGallery = true
            launchPhotoPickerWithFallback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("image_to_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolImageToPdf,
            subtitle = if (photos.isNotEmpty()) "${photos.size} photos ready" else strings.toolImageToPdfDesc,
            icon = Icons.Rounded.Image,
            onBack = onBack,
            trailingContent = {
                Row {
                    if (photos.isNotEmpty()) {
                        IconButton(
                            onClick = { showPreviewAllDialog = true },
                            modifier = Modifier.testTag("gallery_preview_all_icon")
                        ) {
                            Icon(
                                Icons.Rounded.GridView,
                                contentDescription = "Preview all images",
                                tint = ChampagneGold
                            )
                        }
                    }
                    IconButton(
                        onClick = { launchSystemGallery() },
                        modifier = Modifier.testTag("gallery_system_chooser_icon")
                    ) {
                        Icon(
                            Icons.Rounded.Collections,
                            contentDescription = "System Gallery",
                            tint = tokens.primaryGlow
                        )
                    }
                    IconButton(
                        onClick = { launchPhotoPickerWithFallback() },
                        modifier = Modifier.testTag("gallery_add_more_icon")
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = strings.addMore,
                            tint = tokens.tertiaryCyan
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isImportingImages) {
                item { PdfProcessingBanner(label = "Loading selected photos...", progress = null) }
            }

            if (photos.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = strings.selectImages,
                        subtitle = "Select JPG, PNG, or WEBP photos from Photo Picker, System Gallery, or Device Folders.",
                        ctaLabel = strings.selectImages,
                        onCtaClick = { launchPhotoPickerWithFallback() },
                        secondaryCtaLabel = "System Gallery",
                        onSecondaryClick = { launchSystemGallery() }
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { launchDeviceFoldersPicker() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, tokens.glassBorder)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Browse Folders (DCIM / Downloads)", color = tokens.textPrimary)
                    }
                }
            } else {
                val safeActiveIdx = activeIndex.coerceIn(0, photos.lastIndex)
                val activePhoto = photos[safeActiveIdx]

                item {
                    val animatedRotation by animateFloatAsState(
                        targetValue = activePhoto.rotationDegrees.toFloat(),
                        label = "photo_rot"
                    )

                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${safeActiveIdx + 1} of ${photos.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = ChampagneGold,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showPreviewAllDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, tokens.glassBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Preview All", color = tokens.textPrimary)
                                }
                                OutlinedButton(
                                    onClick = { launchDeviceFoldersPicker() },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, tokens.glassBorder),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Folders", color = tokens.textPrimary)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF0A0B12))
                                .border(1.dp, tokens.glassBorder, RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = activePhoto.previewBitmap.asImageBitmap(),
                                contentDescription = "Selected photo ${safeActiveIdx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .graphicsLayer { rotationZ = animatedRotation }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Per-photo quick actions: Move Left, Rotate 90°, Move Right, Remove
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = false,
                                enabled = safeActiveIdx > 0,
                                onClick = {
                                    val updated = photos.toMutableList().apply {
                                        val moved = removeAt(safeActiveIdx)
                                        add(safeActiveIdx - 1, moved)
                                    }
                                    syncSavedEntries(updated)
                                    activeIndex = safeActiveIdx - 1
                                },
                                label = { Text("Left") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move earlier", modifier = Modifier.size(16.dp))
                                }
                            )

                            FilterChip(
                                selected = true,
                                onClick = {
                                    val updated = photos.toMutableList().also {
                                        it[safeActiveIdx] = activePhoto.copy(
                                            rotationDegrees = (activePhoto.rotationDegrees + 90) % 360
                                        )
                                    }
                                    syncSavedEntries(updated)
                                },
                                label = { Text("Rotate") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees", modifier = Modifier.size(16.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tokens.primaryGlow,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.testTag("gallery_rotate_active_btn")
                            )

                            FilterChip(
                                selected = false,
                                enabled = safeActiveIdx < photos.lastIndex,
                                onClick = {
                                    val updated = photos.toMutableList().apply {
                                        val moved = removeAt(safeActiveIdx)
                                        add(safeActiveIdx + 1, moved)
                                    }
                                    syncSavedEntries(updated)
                                    activeIndex = safeActiveIdx + 1
                                },
                                label = { Text("Right") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move later", modifier = Modifier.size(16.dp))
                                }
                            )

                            IconButton(
                                onClick = {
                                    val updated = photos.toMutableList().also { it.removeAt(safeActiveIdx) }
                                    syncSavedEntries(updated)
                                    activeIndex = safeActiveIdx.coerceAtMost((updated.size - 1).coerceAtLeast(0))
                                },
                                modifier = Modifier.testTag("gallery_remove_active_btn")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove image", tint = CoralError)
                            }
                        }
                    }
                }

                // Selected Photos Thumbnail Filmstrip + Add More card
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(photos, key = { _, item -> item.uid }) { idx, item ->
                            val isSelected = idx == safeActiveIdx
                            Box(
                                modifier = Modifier
                                    .size(width = 92.dp, height = 122.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) LuminousCyan else tokens.glassBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { activeIndex = idx }
                            ) {
                                Image(
                                    bitmap = item.previewBitmap.asImageBitmap(),
                                    contentDescription = "Thumbnail ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = item.rotationDegrees.toFloat() }
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(5.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = photos.toMutableList().also { it.removeAt(idx) }
                                        syncSavedEntries(updated)
                                        activeIndex = activeIndex.coerceAtMost((updated.size - 1).coerceAtLeast(0))
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .padding(3.dp)
                                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        item {
                            Surface(
                                onClick = { launchPhotoPickerWithFallback() },
                                shape = RoundedCornerShape(16.dp),
                                color = tokens.glassSurface,
                                border = BorderStroke(1.dp, LuminousCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.size(width = 92.dp, height = 122.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = strings.addMore, tint = LuminousCyan)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = strings.addMore,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Output File Name + Page Size
                item {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text(strings.outputFileName) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.primaryGlow,
                                unfocusedBorderColor = tokens.glassBorder,
                                focusedTextColor = tokens.textPrimary,
                                unfocusedTextColor = tokens.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfPageSize.entries.forEach { sizeOption ->
                                FilterChip(
                                    selected = pageSize == sizeOption,
                                    onClick = { pageSize = sizeOption },
                                    label = { Text(sizeOption.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                item { PdfProcessingBanner(label = strings.processing, progress = progress) }
            }

            errorMsg?.let { err ->
                item {
                    GlassCard {
                        Text(err, color = CoralError, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Large CREATE PDF button -> creates real multi-page PDF and opens PDF Preview screen
        if (photos.isNotEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = "${strings.convertToPdf} (${photos.size})",
                    icon = Icons.Rounded.PictureAsPdf,
                    isLoading = isProcessing,
                    enabled = !isImportingImages && !isProcessing,
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            progress = 0f
                            errorMsg = null
                            runCatching {
                                val (outFile, cleanName) = repository.createUniqueVaultFile(fileName)
                                repository.engine.imagesToPdfWithRotations(
                                    images = photos.map { GalleryImageItem(it.uri, it.rotationDegrees) },
                                    outputFile = outFile,
                                    pageSize = pageSize,
                                    margin = PageMargin.SMALL,
                                    orientation = PageOrientation.AUTO,
                                    onProgress = { progress = it }
                                )
                                repository.registerCreatedPdf(outFile, cleanName, "GALLERY")
                            }.onSuccess { entity ->
                                isProcessing = false
                                onOpenViewer(entity)
                            }.onFailure { err ->
                                errorMsg = err.message ?: "Failed to create PDF from selected photos."
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "img2pdf_convert_btn"
                )
            }
        }
    }

    if (showPreviewAllDialog && photos.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showPreviewAllDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = ObsidianBlack
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Selected Photos (${photos.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showPreviewAllDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.done, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(photos, key = { _, item -> item.uid }) { idx, item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.78f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF121420))
                                    .border(1.dp, tokens.glassBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        activeIndex = idx
                                        showPreviewAllDialog = false
                                    }
                            ) {
                                Image(
                                    bitmap = item.previewBitmap.asImageBitmap(),
                                    contentDescription = "Photo ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = item.rotationDegrees.toFloat() }
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Page ${idx + 1}",
                                        color = ChampagneGold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
