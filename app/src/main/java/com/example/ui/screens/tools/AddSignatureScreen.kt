package com.example.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.SuccessResultCard
import com.example.ui.components.ToolHeader
import com.example.ui.components.VaultPdfPickerSheet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalPdfPlusTokens
import kotlinx.coroutines.launch
import java.io.File

private enum class SignaturePlacement(val nx: Float, val ny: Float, val label: String) {
    BOTTOM_RIGHT(0.86f, 0.88f, "Bottom Right"),
    BOTTOM_CENTER(0.5f, 0.88f, "Bottom Center"),
    BOTTOM_LEFT(0.14f, 0.88f, "Bottom Left"),
    CENTER(0.5f, 0.5f, "Center")
}

@Composable
fun AddSignatureScreen(
    initialDocumentId: Long,
    repository: PdfRepository,
    vaultDocuments: List<PdfDocumentEntity>,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var totalPages by remember { mutableIntStateOf(0) }
    var targetPage by remember { mutableIntStateOf(0) }
    var previewPageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(600, 240)) }
    var inkColor by remember { mutableStateOf(Color(0xFF111827)) }
    var placement by remember { mutableStateOf(SignaturePlacement.BOTTOM_RIGHT) }
    var widthFraction by remember { mutableFloatStateOf(0.32f) }

    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var createdDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        val doc = createdDoc
        if (dest != null && doc != null) {
            scope.launch { repository.exportPdfToUri(doc, dest) }
        }
    }

    LaunchedEffect(initialDocumentId) {
        if (initialDocumentId > 0L) {
            repository.getById(initialDocumentId)?.let { doc ->
                sourceFile = File(doc.filePath)
                sourceName = doc.displayName
            }
        }
    }

    LaunchedEffect(sourceFile) {
        val f = sourceFile ?: return@LaunchedEffect
        totalPages = repository.engine.getPageCount(f)
        targetPage = 0
        createdDoc = null
    }

    LaunchedEffect(sourceFile, targetPage) {
        val f = sourceFile ?: return@LaunchedEffect
        if (totalPages > 0) {
            previewPageBitmap = repository.engine.renderPageBitmap(f, targetPage, maxWidthPx = 640)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("add_signature_screen")
    ) {
        ToolHeader(
            title = strings.toolAddSignature,
            subtitle = strings.toolAddSignatureDesc,
            icon = Icons.Rounded.Draw,
            onBack = onBack,
            trailingContent = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = strings.selectPdf, tint = tokens.textPrimary)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sourceFile == null) {
                item {
                    EmptyStatePanel(
                        title = strings.selectPdf,
                        subtitle = "Draw your handwritten signature and embed it cleanly onto any PDF page.",
                        ctaLabel = strings.selectPdf,
                        onCtaClick = { showPicker = true }
                    )
                }
            } else {
                item {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Draw Signature Below", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            TextButton(
                                onClick = {
                                    strokes.clear()
                                    activeStroke = emptyList()
                                }
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Clear")
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .border(1.5.dp, tokens.primaryGlow, RoundedCornerShape(18.dp))
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { start -> activeStroke = listOf(start) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStroke = activeStroke + change.position
                                        },
                                        onDragEnd = {
                                            if (activeStroke.size > 1) strokes.add(activeStroke)
                                            activeStroke = emptyList()
                                        }
                                    )
                                }
                                .testTag("signature_pad_canvas")
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val allStrokes = strokes + listOf(activeStroke)
                                allStrokes.forEach { pts ->
                                    if (pts.size > 1) {
                                        val p = Path().apply {
                                            moveTo(pts.first().x, pts.first().y)
                                            for (i in 1 until pts.size) {
                                                lineTo(pts[i].x, pts[i].y)
                                            }
                                        }
                                        drawPath(
                                            path = p,
                                            color = inkColor,
                                            style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ink:", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                            listOf(
                                Color(0xFF111827),
                                Color(0xFF1D4ED8),
                                Color(0xFFB91C1C),
                                Color(0xFFB45309)
                            ).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (inkColor == c) 3.dp else 1.dp,
                                            color = if (inkColor == c) ChampagneGold else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { inkColor = c }
                                )
                            }
                        }
                    }
                }

                item {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target Page: ${targetPage + 1} / $totalPages", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = false,
                                    onClick = { if (targetPage > 0) targetPage-- },
                                    enabled = targetPage > 0,
                                    label = { Text("Prev") }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = { if (targetPage < totalPages - 1) targetPage++ },
                                    enabled = targetPage < totalPages - 1,
                                    label = { Text("Next") }
                                )
                            }
                        }

                        previewPageBitmap?.let { bmp ->
                            Spacer(Modifier.height(8.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Target page preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Placement", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SignaturePlacement.entries.forEach { p ->
                                FilterChip(
                                    selected = placement == p,
                                    onClick = { placement = p },
                                    label = { Text(p.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Text("Size", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        Slider(
                            value = widthFraction,
                            onValueChange = { widthFraction = it },
                            valueRange = 0.16f..0.60f
                        )
                    }
                }
            }

            if (isProcessing) {
                item { PdfProcessingBanner(label = strings.processing) }
            }

            errorMsg?.let { err ->
                item { GlassCard { Text(err, color = CoralError) } }
            }

            createdDoc?.let { doc ->
                item {
                    SuccessResultCard(
                        document = doc,
                        onPreview = { onOpenViewer(doc) },
                        onShare = { repository.sharePdf(doc) },
                        onDownload = { exportLauncher.launch("${doc.displayName}.pdf") }
                    )
                }
            }
        }

        if (sourceFile != null) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = strings.savePdf,
                    icon = Icons.Rounded.Save,
                    enabled = strokes.isNotEmpty(),
                    isLoading = isProcessing,
                    onClick = {
                        val f = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            errorMsg = null
                            runCatching {
                                val sigBmp = renderStrokesToBitmap(strokes, canvasSize.width.coerceAtLeast(300), canvasSize.height.coerceAtLeast(150), inkColor.toArgb())
                                val (outFile, cleanName) = repository.createUniqueVaultFile("${sourceName}_signed")
                                repository.engine.addSignatureToPdf(
                                    inputFile = f,
                                    outputFile = outFile,
                                    signatureBitmap = sigBmp,
                                    targetPageZeroBased = targetPage,
                                    normalizedX = placement.nx,
                                    normalizedY = placement.ny,
                                    normalizedWidth = widthFraction
                                )
                                sigBmp.recycle()
                                repository.registerCreatedPdf(outFile, cleanName, "SIGNED")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Failed to embed signature" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "signature_save_btn"
                )
            }
        }
    }

    if (showPicker) {
        VaultPdfPickerSheet(
            documents = vaultDocuments,
            onSelectVaultDoc = { doc ->
                sourceFile = File(doc.filePath)
                sourceName = doc.displayName
            },
            onSelectExternalUri = { uri ->
                scope.launch {
                    runCatching {
                        val (file, name) = repository.copyUriToTempFile(uri)
                        sourceFile = file
                        sourceName = name
                    }
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}

private fun renderStrokesToBitmap(
    strokes: List<List<Offset>>,
    width: Int,
    height: Int,
    inkColorInt: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.TRANSPARENT)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = inkColorInt
        style = AndroidPaint.Style.STROKE
        strokeWidth = 7f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { pts ->
        if (pts.size > 1) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x, pts[i].y)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bmp
}
