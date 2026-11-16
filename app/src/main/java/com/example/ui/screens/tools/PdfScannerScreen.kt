package com.example.ui.screens.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.view.MotionEvent
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.DocumentQuad
import com.example.data.pdf.DocumentScannerProcessor
import com.example.data.pdf.ScanEnhanceMode
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.ToolHeader
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBlack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot
import kotlin.math.max

private enum class ScannerStage { CAMERA, PREVIEW, CROP_ADJUST }

private data class ScannedDocPage(
    val uid: Long,
    val rawBitmap: Bitmap,
    val quad: DocumentQuad,
    val rotationDegrees: Int,
    val enhanceMode: ScanEnhanceMode,
    val enhancedBitmap: Bitmap
)

@Composable
fun PdfScannerScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var stage by remember { mutableStateOf(ScannerStage.CAMERA) }
    var replaceIndex by remember { mutableIntStateOf(-1) }
    var autoCropEnabled by remember { mutableStateOf(true) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var retrySeed by remember { mutableIntStateOf(0) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var isCapturingOrEnhancing by remember { mutableStateOf(false) }

    var scannedPages by remember { mutableStateOf<List<ScannedDocPage>>(emptyList()) }
    var activePageIdx by remember { mutableIntStateOf(0) }

    var isCreatingPdf by remember { mutableStateOf(false) }
    var pdfProgress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun importGalleryUriIntoScanner(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            isCapturingOrEnhancing = true
            errorMsg = null
            runCatching {
                val (safeUri, loadedBmp) = repository.engine.loadAndStageGalleryImage(uri, previewMaxDimPx = 1800).getOrThrow()
                withContext(Dispatchers.IO) {
                    val detectedQuad = if (autoCropEnabled) {
                        DocumentScannerProcessor.detectDocumentQuad(loadedBmp)
                    } else {
                        DocumentQuad(0.03f, 0.03f, 0.97f, 0.03f, 0.97f, 0.97f, 0.03f, 0.97f)
                    }
                    val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(loadedBmp, detectedQuad, 0)
                    val enhanced = DocumentScannerProcessor.enhanceScannedDocument(warped, ScanEnhanceMode.COLOR)
                    if (warped !== enhanced) warped.recycle()
                    ScannedDocPage(
                        uid = System.nanoTime(),
                        rawBitmap = loadedBmp,
                        quad = detectedQuad,
                        rotationDegrees = 0,
                        enhanceMode = ScanEnhanceMode.COLOR,
                        enhancedBitmap = enhanced
                    )
                }
            }.onSuccess { page ->
                if (replaceIndex in scannedPages.indices) {
                    scannedPages = scannedPages.toMutableList().also { it[replaceIndex] = page }
                    activePageIdx = replaceIndex
                    replaceIndex = -1
                } else {
                    scannedPages = scannedPages + page
                    activePageIdx = scannedPages.lastIndex
                }
                stage = if (autoCropEnabled) ScannerStage.PREVIEW else ScannerStage.CROP_ADJUST
            }.onFailure { err ->
                errorMsg = err.message ?: "Failed to load selected photo from gallery."
            }
            isCapturingOrEnhancing = false
        }
    }

    val scannerPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        importGalleryUriIntoScanner(uri)
    }

    val scannerGalleryFallback = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        importGalleryUriIntoScanner(uri)
    }

    fun openGalleryForScanner() {
        val available = runCatching {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        }.getOrDefault(false)
        if (available) {
            val launched = runCatching {
                scannerPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.isSuccess
            if (!launched) {
                runCatching { scannerGalleryFallback.launch("image/*") }
            }
        } else {
            runCatching { scannerGalleryFallback.launch("image/*") }
        }
    }

    // Clear tap-to-focus indicator automatically after a short fade period
    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(950)
            focusTapOffset = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .navigationBarsPadding()
            .testTag("pdf_scanner_screen")
    ) {
        ToolHeader(
            title = strings.toolPdfScanner,
            subtitle = when (stage) {
                ScannerStage.CAMERA -> "Align document inside frame • Tap to focus"
                ScannerStage.PREVIEW -> if (scannedPages.isNotEmpty()) "Page ${activePageIdx + 1} of ${scannedPages.size} • Ready" else strings.toolPdfScannerDesc
                ScannerStage.CROP_ADJUST -> "Drag corners to adjust document edges"
            },
            icon = Icons.Rounded.DocumentScanner,
            onBack = {
                if (stage == ScannerStage.CROP_ADJUST) {
                    stage = ScannerStage.PREVIEW
                } else {
                    onBack()
                }
            },
            trailingContent = {
                if (stage == ScannerStage.CAMERA && hasCameraPermission) {
                    Row {
                        if (hasFlashUnit) {
                            IconButton(
                                onClick = {
                                    val next = !flashEnabled
                                    flashEnabled = next
                                    activeCamera?.cameraControl?.enableTorch(next)
                                    imageCapture?.flashMode = if (next) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                },
                                modifier = Modifier.testTag("scanner_flash_btn")
                            ) {
                                Icon(
                                    imageVector = if (flashEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                    contentDescription = "Flashlight",
                                    tint = if (flashEnabled) ChampagneGold else Color.White
                                )
                            }
                        }
                        if (canSwitchCamera) {
                            IconButton(
                                onClick = {
                                    flashEnabled = false
                                    activeCamera?.cameraControl?.enableTorch(false)
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                },
                                modifier = Modifier.testTag("scanner_switch_camera_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cameraswitch,
                                    contentDescription = "Switch camera",
                                    tint = LuminousCyan
                                )
                            }
                        }
                    }
                }
            }
        )

        when (stage) {
            // STEP 1 & 2: Full-bleed unclipped TextureView CameraX feed (avoids SurfaceView clip/buffer corruption)
            ScannerStage.CAMERA -> {
                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(contentPadding = PaddingValues(22.dp)) {
                            Text(
                                text = "Camera Access Required",
                                style = MaterialTheme.typography.titleLarge,
                                color = tokens.textPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Grant camera permission to stream the live viewfinder, or import an existing document photo from your gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            GlowButton(
                                text = "Allow Camera",
                                icon = Icons.Rounded.CameraAlt,
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "scanner_allow_camera_btn"
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { openGalleryForScanner() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import from Gallery", color = tokens.textPrimary)
                            }
                        }
                    }
                } else {
                    val previewView = remember(context) {
                        PreviewView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }

                    DisposableEffect(lifecycleOwner, lensFacing, hasCameraPermission, retrySeed) {
                        var disposed = false
                        var boundProvider: ProcessCameraProvider? = null

                        val job = scope.launch {
                            runCatching {
                                errorMsg = null
                                val provider = awaitCameraProvider(context)
                                if (disposed) return@runCatching
                                boundProvider = provider
                                cameraProvider = provider

                                val hasBack = runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
                                val hasFront = runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                                canSwitchCamera = hasBack && hasFront

                                val effectiveFacing = when {
                                    lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> CameraSelector.LENS_FACING_BACK
                                    lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.LENS_FACING_FRONT
                                    hasBack -> CameraSelector.LENS_FACING_BACK
                                    hasFront -> CameraSelector.LENS_FACING_FRONT
                                    else -> throw IllegalStateException("No available camera found on this device.")
                                }
                                val selector = CameraSelector.Builder()
                                    .requireLensFacing(effectiveFacing)
                                    .build()

                                // Wait until AndroidView has attached & laid out previewView before binding surfaceProvider
                                awaitViewLaidOut(previewView)
                                if (disposed) return@runCatching

                                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                val resolutionSelector = ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                    .build()

                                val preview = Preview.Builder()
                                    .setResolutionSelector(resolutionSelector)
                                    .setTargetRotation(rotation)
                                    .build()

                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .setResolutionSelector(resolutionSelector)
                                    .setTargetRotation(rotation)
                                    .setFlashMode(
                                        if (flashEnabled && effectiveFacing == CameraSelector.LENS_FACING_BACK) {
                                            ImageCapture.FLASH_MODE_ON
                                        } else {
                                            ImageCapture.FLASH_MODE_OFF
                                        }
                                    )
                                    .build()

                                provider.unbindAll()
                                val camera = try {
                                    preview.setSurfaceProvider(previewView.surfaceProvider)
                                    imageCapture = capture
                                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                } catch (_: Throwable) {
                                    // Guaranteed universal Camera2 fallback without ResolutionSelector constraints
                                    val fallbackPreview = Preview.Builder().build().apply {
                                        setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val fallbackCapture = ImageCapture.Builder().build()
                                    imageCapture = fallbackCapture
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, selector, fallbackPreview, fallbackCapture)
                                }

                                activeCamera = camera
                                hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                                if (hasFlashUnit && flashEnabled) {
                                    camera.cameraControl.enableTorch(true)
                                }

                                // Lock initial center auto-focus & auto-exposure
                                previewView.post {
                                    if (!disposed && previewView.width > 0 && previewView.height > 0) {
                                        val centerPt = previewView.meteringPointFactory.createPoint(
                                            previewView.width / 2f,
                                            previewView.height / 2f
                                        )
                                        val action = FocusMeteringAction.Builder(
                                            centerPt,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        camera.cameraControl.startFocusAndMetering(action)
                                    }
                                }

                                previewView.setOnTouchListener { view, event ->
                                    if (event.action == MotionEvent.ACTION_UP) {
                                        focusTapOffset = Offset(event.x, event.y)
                                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                        val action = FocusMeteringAction.Builder(
                                            point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        camera.cameraControl.startFocusAndMetering(action)
                                        view.performClick()
                                    }
                                    true
                                }
                            }.onFailure { err ->
                                if (!disposed) {
                                    errorMsg = err.message ?: "Unable to start live camera feed."
                                }
                            }
                        }

                        onDispose {
                            disposed = true
                            job.cancel()
                            previewView.setOnTouchListener(null)
                            runCatching { activeCamera?.cameraControl?.enableTorch(false) }
                            runCatching { boundProvider?.unbindAll() }
                            runCatching { cameraProvider?.unbindAll() }
                            activeCamera = null
                            imageCapture = null
                        }
                    }

                    // Viewfinder viewport (full-bleed unclipped black container for reliable TextureView rendering)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Document alignment rectangle outline & optional tap-to-focus ring (strictly stroke-only, no solid fill)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val padX = size.width * 0.08f
                            val padY = size.height * 0.08f
                            drawRect(
                                color = EmeraldSuccess.copy(alpha = 0.88f),
                                topLeft = Offset(padX, padY),
                                size = Size(size.width - padX * 2, size.height - padY * 2),
                                style = Stroke(width = 2.5.dp.toPx())
                            )

                            focusTapOffset?.let { tap ->
                                drawCircle(
                                    color = ChampagneGold,
                                    radius = 30.dp.toPx(),
                                    center = tap,
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }

                        if (isCapturingOrEnhancing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = LuminousCyan)
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = "Processing document...",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Scanner Controls (Auto / Manual + Gallery Picker + Capture Shutter + Preview)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { autoCropEnabled = true },
                                shape = RoundedCornerShape(999.dp),
                                color = if (autoCropEnabled) EmeraldSuccess.copy(alpha = 0.2f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (autoCropEnabled) EmeraldSuccess else Color.White.copy(alpha = 0.25f)
                                )
                            ) {
                                Text(
                                    text = "Auto",
                                    color = if (autoCropEnabled) EmeraldSuccess else Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                onClick = { autoCropEnabled = false },
                                shape = RoundedCornerShape(999.dp),
                                color = if (!autoCropEnabled) ChampagneGold.copy(alpha = 0.22f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (!autoCropEnabled) ChampagneGold else Color.White.copy(alpha = 0.25f)
                                )
                            ) {
                                Text(
                                    text = "Manual",
                                    color = if (!autoCropEnabled) ChampagneGold else Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bottom-left Gallery button to import a pic into the document scanner
                            Surface(
                                onClick = { openGalleryForScanner() },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("scanner_gallery_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PhotoLibrary,
                                        contentDescription = "Pick from Gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Center Capture Shutter button
                            Surface(
                                onClick = {
                                    val cap = imageCapture ?: return@Surface
                                    if (isCapturingOrEnhancing) return@Surface
                                    isCapturingOrEnhancing = true
                                    errorMsg = null
                                    cap.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                    val scansDir = File(context.cacheDir, "scans").apply { mkdirs() }
                                    val outFile = File(scansDir, "raw_scan_${System.currentTimeMillis()}.jpg")
                                    val outputOpts = ImageCapture.OutputFileOptions.Builder(outFile).build()

                                    cap.takePicture(
                                        outputOpts,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                                scope.launch {
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            processCapturedScanFile(outFile, autoCropEnabled)
                                                        }
                                                    }.onSuccess { scannedPage ->
                                                        if (replaceIndex in scannedPages.indices) {
                                                            scannedPages = scannedPages.toMutableList().also {
                                                                it[replaceIndex] = scannedPage
                                                            }
                                                            activePageIdx = replaceIndex
                                                            replaceIndex = -1
                                                        } else {
                                                            scannedPages = scannedPages + scannedPage
                                                            activePageIdx = scannedPages.lastIndex
                                                        }
                                                        stage = if (autoCropEnabled) {
                                                            ScannerStage.PREVIEW
                                                        } else {
                                                            ScannerStage.CROP_ADJUST
                                                        }
                                                    }.onFailure { err ->
                                                        errorMsg = err.message ?: "Scan processing failed"
                                                    }
                                                    isCapturingOrEnhancing = false
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                errorMsg = exception.message ?: "Capture failed"
                                                isCapturingOrEnhancing = false
                                            }
                                        }
                                    )
                                },
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(4.dp, EmeraldSuccess, CircleShape)
                                    .testTag("scanner_capture_btn")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.CameraAlt,
                                        contentDescription = "Capture document",
                                        tint = Color.Black,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Bottom-right Preview button or page badge
                            if (scannedPages.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        replaceIndex = -1
                                        stage = ScannerStage.PREVIEW
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text("Preview (${scannedPages.size})", color = ChampagneGold)
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ChampagneGold.copy(alpha = 0.2f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "0p",
                                            color = ChampagneGold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    errorMsg?.let { err ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            GlassCard(contentPadding = PaddingValues(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = err,
                                        color = CoralError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(onClick = { retrySeed++ }) {
                                        Text("Retry", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 6, 7, 8: Enhanced Scan Preview with Color / Grayscale / B&W / Original + Retake, Rotate, Crop, + Page, CREATE PDF
            ScannerStage.PREVIEW -> {
                if (scannedPages.isEmpty()) {
                    stage = ScannerStage.CAMERA
                } else {
                    val safeIdx = activePageIdx.coerceIn(0, scannedPages.lastIndex)
                    val activePage = scannedPages[safeIdx]

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF0A0B12))
                                .border(1.dp, tokens.glassBorder, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = activePage.enhancedBitmap.asImageBitmap(),
                                contentDescription = "Enhanced scan page ${safeIdx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )

                            if (isCapturingOrEnhancing) {
                                CircularProgressIndicator(color = LuminousCyan)
                            }
                        }

                        // Enhancement Modes: Color, Grayscale, B&W, Original
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ScanEnhanceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = activePage.enhanceMode == mode,
                                    onClick = {
                                        scope.launch {
                                            isCapturingOrEnhancing = true
                                            val updated = withContext(Dispatchers.IO) {
                                                val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(
                                                    activePage.rawBitmap,
                                                    activePage.quad,
                                                    activePage.rotationDegrees
                                                )
                                                val enh = DocumentScannerProcessor.enhanceScannedDocument(warped, mode)
                                                if (warped !== enh) warped.recycle()
                                                activePage.copy(enhanceMode = mode, enhancedBitmap = enh)
                                            }
                                            scannedPages = scannedPages.toMutableList().also { it[safeIdx] = updated }
                                            isCapturingOrEnhancing = false
                                        }
                                    },
                                    label = { Text(mode.label, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Actions: Retake, Rotate, Crop, Add More Pages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    replaceIndex = safeIdx
                                    stage = ScannerStage.CAMERA
                                },
                                label = { Text("Retake") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("scanner_retake_btn")
                            )

                            FilterChip(
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        isCapturingOrEnhancing = true
                                        val nextRot = (activePage.rotationDegrees + 90) % 360
                                        val updated = withContext(Dispatchers.IO) {
                                            val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(
                                                activePage.rawBitmap,
                                                activePage.quad,
                                                nextRot
                                            )
                                            val enh = DocumentScannerProcessor.enhanceScannedDocument(warped, activePage.enhanceMode)
                                            if (warped !== enh) warped.recycle()
                                            activePage.copy(rotationDegrees = nextRot, enhancedBitmap = enh)
                                        }
                                        scannedPages = scannedPages.toMutableList().also { it[safeIdx] = updated }
                                        isCapturingOrEnhancing = false
                                    }
                                },
                                label = { Text("Rotate") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("scanner_rotate_btn")
                            )

                            FilterChip(
                                selected = false,
                                onClick = { stage = ScannerStage.CROP_ADJUST },
                                label = { Text("Crop") },
                                leadingIcon = { Icon(Icons.Rounded.Crop, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("scanner_crop_btn")
                            )

                            FilterChip(
                                selected = true,
                                onClick = {
                                    replaceIndex = -1
                                    stage = ScannerStage.CAMERA
                                },
                                label = { Text("+ Page") },
                                leadingIcon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tokens.secondaryGlow,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.testTag("scanner_add_page_btn")
                            )
                        }

                        // Multi-page thumbnail strip
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(scannedPages, key = { _, item -> item.uid }) { idx, item ->
                                val selected = idx == safeIdx
                                Box(
                                    modifier = Modifier
                                        .height(82.dp)
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) LuminousCyan else tokens.glassBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { activePageIdx = idx }
                                ) {
                                    Image(
                                        bitmap = item.enhancedBitmap.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (scannedPages.size > 1) {
                                        IconButton(
                                            onClick = {
                                                scannedPages = scannedPages.toMutableList().also { it.removeAt(idx) }
                                                activePageIdx = activePageIdx.coerceAtMost((scannedPages.size - 1).coerceAtLeast(0))
                                            },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .padding(2.dp)
                                                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (isCreatingPdf) {
                            PdfProcessingBanner(label = strings.processing, progress = pdfProgress)
                        }

                        errorMsg?.let { err ->
                            GlassCard { Text(err, color = CoralError) }
                        }

                        GlowButton(
                            text = "${strings.convertToPdf} (${scannedPages.size})",
                            icon = Icons.Rounded.PictureAsPdf,
                            isLoading = isCreatingPdf,
                            onClick = {
                                scope.launch {
                                    isCreatingPdf = true
                                    pdfProgress = 0f
                                    errorMsg = null
                                    runCatching {
                                        val (outFile, cleanName) = repository.createUniqueVaultFile("Scan_${System.currentTimeMillis() % 10000}")
                                        repository.engine.scannedBitmapsToPdf(
                                            bitmaps = scannedPages.map { it.enhancedBitmap },
                                            outputFile = outFile,
                                            onProgress = { pdfProgress = it }
                                        )
                                        repository.registerCreatedPdf(outFile, cleanName, "SCANNED")
                                    }.onSuccess { entity ->
                                        isCreatingPdf = false
                                        onOpenViewer(entity)
                                    }.onFailure { err ->
                                        errorMsg = err.message ?: "Failed to create PDF"
                                        isCreatingPdf = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            testTag = "scanner_create_pdf_btn"
                        )
                    }
                }
            }

            // Manual 4-Corner Crop & Perspective Adjuster
            ScannerStage.CROP_ADJUST -> {
                val safeIdx = activePageIdx.coerceIn(0, scannedPages.lastIndex)
                val activePage = scannedPages[safeIdx]
                var editQuad by remember(activePage.uid) { mutableStateOf(activePage.quad) }
                var activeHandle by remember { mutableIntStateOf(-1) }
                var boxSize by remember { mutableStateOf(IntSize(1, 1)) }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black)
                            .onSizeChanged { boxSize = it }
                            .pointerInput(activePage.uid) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val nx = start.x / max(1, boxSize.width)
                                        val ny = start.y / max(1, boxSize.height)
                                        val corners = listOf(
                                            editQuad.topLeftX to editQuad.topLeftY,
                                            editQuad.topRightX to editQuad.topRightY,
                                            editQuad.bottomRightX to editQuad.bottomRightY,
                                            editQuad.bottomLeftX to editQuad.bottomLeftY
                                        )
                                        activeHandle = corners.indices.minByOrNull { i ->
                                            hypot(corners[i].first - nx, corners[i].second - ny)
                                        } ?: -1
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / max(1, boxSize.width)
                                        val dy = dragAmount.y / max(1, boxSize.height)
                                        editQuad = when (activeHandle) {
                                            0 -> editQuad.copy(
                                                topLeftX = (editQuad.topLeftX + dx).coerceIn(0.01f, 0.85f),
                                                topLeftY = (editQuad.topLeftY + dy).coerceIn(0.01f, 0.85f)
                                            )
                                            1 -> editQuad.copy(
                                                topRightX = (editQuad.topRightX + dx).coerceIn(0.15f, 0.99f),
                                                topRightY = (editQuad.topRightY + dy).coerceIn(0.01f, 0.85f)
                                            )
                                            2 -> editQuad.copy(
                                                bottomRightX = (editQuad.bottomRightX + dx).coerceIn(0.15f, 0.99f),
                                                bottomRightY = (editQuad.bottomRightY + dy).coerceIn(0.15f, 0.99f)
                                            )
                                            3 -> editQuad.copy(
                                                bottomLeftX = (editQuad.bottomLeftX + dx).coerceIn(0.01f, 0.85f),
                                                bottomLeftY = (editQuad.bottomLeftY + dy).coerceIn(0.15f, 0.99f)
                                            )
                                            else -> editQuad
                                        }
                                    },
                                    onDragEnd = { activeHandle = -1 }
                                )
                            }
                    ) {
                        Image(
                            bitmap = activePage.rawBitmap.asImageBitmap(),
                            contentDescription = "Raw document for cropping",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val tl = Offset(editQuad.topLeftX * w, editQuad.topLeftY * h)
                            val tr = Offset(editQuad.topRightX * w, editQuad.topRightY * h)
                            val br = Offset(editQuad.bottomRightX * w, editQuad.bottomRightY * h)
                            val bl = Offset(editQuad.bottomLeftX * w, editQuad.bottomLeftY * h)

                            val quadPath = Path().apply {
                                moveTo(tl.x, tl.y)
                                lineTo(tr.x, tr.y)
                                lineTo(br.x, br.y)
                                lineTo(bl.x, bl.y)
                                close()
                            }
                            drawPath(quadPath, color = LuminousCyan, style = Stroke(width = 4f))
                            listOf(tl, tr, br, bl).forEach { pt ->
                                drawCircle(color = ChampagneGold, radius = 18f, center = pt)
                                drawCircle(color = Color.Black, radius = 8f, center = pt)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { stage = ScannerStage.PREVIEW },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(strings.cancel, color = tokens.textPrimary)
                        }
                        GlowButton(
                            text = "Apply Crop",
                            icon = Icons.Rounded.Check,
                            onClick = {
                                scope.launch {
                                    isCapturingOrEnhancing = true
                                    val updated = withContext(Dispatchers.IO) {
                                        val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(
                                            activePage.rawBitmap,
                                            editQuad,
                                            activePage.rotationDegrees
                                        )
                                        val enh = DocumentScannerProcessor.enhanceScannedDocument(warped, activePage.enhanceMode)
                                        if (warped !== enh) warped.recycle()
                                        activePage.copy(quad = editQuad, enhancedBitmap = enh)
                                    }
                                    scannedPages = scannedPages.toMutableList().also { it[safeIdx] = updated }
                                    isCapturingOrEnhancing = false
                                    stage = ScannerStage.PREVIEW
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

private suspend fun awaitViewLaidOut(view: PreviewView) {
    if (view.isAttachedToWindow && view.width > 0 && view.height > 0) return
    suspendCancellableCoroutine { cont ->
        view.post {
            if (cont.isActive) cont.resume(Unit)
        }
    }
}

private fun processCapturedScanFile(file: File, autoDetectEdges: Boolean): ScannedDocPage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var inSample = 1
    while (bounds.outWidth / inSample > 2000 || bounds.outHeight / inSample > 2000) {
        inSample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = inSample }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, opts)
        ?: throw IllegalStateException("Failed to decode captured photo.")

    val exifOrient = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val exifRot = when (exifOrient) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    val raw = if (exifRot != 0f) {
        val m = Matrix().apply { postRotate(exifRot) }
        val r = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (r !== decoded) decoded.recycle()
        r
    } else {
        decoded
    }

    val detectedQuad = if (autoDetectEdges) {
        DocumentScannerProcessor.detectDocumentQuad(raw)
    } else {
        DocumentQuad(0.03f, 0.03f, 0.97f, 0.03f, 0.97f, 0.97f, 0.03f, 0.97f)
    }
    val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(raw, detectedQuad, 0)
    val enhanced = DocumentScannerProcessor.enhanceScannedDocument(warped, ScanEnhanceMode.COLOR)
    if (warped !== enhanced) warped.recycle()

    return ScannedDocPage(
        uid = System.nanoTime(),
        rawBitmap = raw,
        quad = detectedQuad,
        rotationDegrees = 0,
        enhanceMode = ScanEnhanceMode.COLOR,
        enhancedBitmap = enhanced
    )
}
