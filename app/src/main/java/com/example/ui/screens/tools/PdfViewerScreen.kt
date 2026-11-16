package com.example.ui.screens.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.ToolHeader
import com.example.ui.components.VaultPdfPickerSheet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    initialDocumentId: Long,
    repository: PdfRepository,
    vaultDocuments: List<PdfDocumentEntity>,
    onBack: () -> Unit,
    onOpenEditor: (Long) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }
    var activeName by remember { mutableStateOf(strings.toolPdfViewer) }
    var totalPages by remember { mutableIntStateOf(0) }
    var isNightInvert by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val visiblePageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        val doc = activeDoc
        if (dest != null && doc != null) {
            scope.launch {
                runCatching { repository.exportPdfToUri(doc, dest) }
                    .onSuccess { snackbarHostState.showSnackbar("Saved ${doc.displayName}.pdf") }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Save failed") }
            }
        }
    }

    LaunchedEffect(initialDocumentId) {
        if (initialDocumentId > 0L) {
            val doc = repository.getById(initialDocumentId)
            if (doc != null) {
                repository.touchOpened(doc.id)
                activeDoc = doc
                activeName = "${doc.displayName}.pdf"
                activeFile = File(doc.filePath)
            }
        }
    }

    LaunchedEffect(activeFile) {
        val f = activeFile ?: return@LaunchedEffect
        totalPages = repository.engine.getPageCount(f)
        zoomScale = 1f
        panOffsetX = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("pdf_viewer_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ToolHeader(
                title = activeName,
                subtitle = if (totalPages > 0) {
                    "Page ${visiblePageIndex + 1} of $totalPages • ${(zoomScale * 100).roundToInt()}%"
                } else {
                    strings.toolPdfViewerDesc
                },
                icon = Icons.Rounded.PictureAsPdf,
                onBack = onBack,
                trailingContent = {
                    Row {
                        if (activeFile != null) {
                            IconButton(
                                onClick = { zoomScale = (zoomScale - 0.35f).coerceAtLeast(1f) },
                                enabled = zoomScale > 1f
                            ) {
                                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out", tint = tokens.textPrimary)
                            }
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.35f).coerceAtMost(3.5f) },
                                enabled = zoomScale < 3.5f
                            ) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in", tint = tokens.textPrimary)
                            }
                            IconButton(onClick = { isNightInvert = !isNightInvert }) {
                                Icon(
                                    Icons.Rounded.DarkMode,
                                    contentDescription = "Night reading",
                                    tint = if (isNightInvert) ChampagneGold else tokens.textPrimary
                                )
                            }
                        }
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = strings.selectPdf, tint = tokens.textPrimary)
                        }
                    }
                }
            )

            if (activeFile == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStatePanel(
                        title = strings.selectPdf,
                        subtitle = "Scroll pages smoothly, pinch to zoom in/out, save, share, rename, or open externally.",
                        ctaLabel = strings.selectPdf,
                        onCtaClick = { showPicker = true }
                    )
                }
            } else {
                val file = activeFile!!
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isNightInvert) Color.Black else Color(0xFF0F111A))
                        .border(1.dp, tokens.glassBorder, RoundedCornerShape(22.dp))
                        .pointerInput(file.absolutePath) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                zoomScale = nextScale
                                panOffsetX = if (nextScale > 1f) (panOffsetX + pan.x).coerceIn(-600f, 600f) else 0f
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                            },
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(totalPages, key = { it }) { pageIdx ->
                            PdfPageItemCard(
                                file = file,
                                pageIndex = pageIdx,
                                totalPages = totalPages,
                                invertNightMode = isNightInvert,
                                repository = repository
                            )
                        }
                    }

                    // Page count pill overlay
                    if (totalPages > 0) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .border(1.dp, ChampagneGold.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        ) {
                            Text(
                                text = "${visiblePageIndex + 1} / $totalPages",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneGold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Bottom Action Bar: Save PDF, Share PDF, Rename PDF, Open PDF
                activeDoc?.let { doc ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PreviewActionItem(
                                icon = Icons.Rounded.SaveAlt,
                                label = strings.savePdf,
                                tint = LuminousCyan,
                                onClick = { exportLauncher.launch("${doc.displayName}.pdf") },
                                testTag = "preview_save_pdf_btn"
                            )
                            PreviewActionItem(
                                icon = Icons.Rounded.Share,
                                label = strings.share,
                                tint = tokens.primaryGlow,
                                onClick = { repository.sharePdf(doc) },
                                testTag = "preview_share_pdf_btn"
                            )
                            PreviewActionItem(
                                icon = Icons.Rounded.DriveFileRenameOutline,
                                label = strings.rename,
                                tint = ChampagneGold,
                                onClick = { showRenameDialog = true },
                                testTag = "preview_rename_pdf_btn"
                            )
                            PreviewActionItem(
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                label = strings.openInViewer,
                                tint = Color.White,
                                highlight = true,
                                onClick = {
                                    val opened = repository.openPdfExternally(doc)
                                    if (!opened) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("No external PDF app found. Saved in My Files.")
                                        }
                                    }
                                },
                                testTag = "preview_open_pdf_btn"
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 86.dp)
        )
    }

    if (showRenameDialog && activeDoc != null) {
        val current = activeDoc!!
        RenameDocumentDialog(
            currentName = current.displayName,
            onConfirm = { newName ->
                scope.launch {
                    val ok = repository.renameDocument(current, newName)
                    if (ok) {
                        val updated = repository.getById(current.id)
                        if (updated != null) {
                            activeDoc = updated
                            activeName = "${updated.displayName}.pdf"
                            activeFile = File(updated.filePath)
                        }
                        snackbarHostState.showSnackbar("Renamed to $newName.pdf")
                    } else {
                        snackbarHostState.showSnackbar("Could not rename PDF")
                    }
                }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showPicker) {
        VaultPdfPickerSheet(
            documents = vaultDocuments,
            onSelectVaultDoc = { doc ->
                activeDoc = doc
                activeName = "${doc.displayName}.pdf"
                activeFile = File(doc.filePath)
            },
            onSelectExternalUri = { uri ->
                scope.launch {
                    runCatching {
                        val entity = repository.importPdfFromUri(uri)
                        activeDoc = entity
                        activeName = "${entity.displayName}.pdf"
                        activeFile = File(entity.filePath)
                    }
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun PdfPageItemCard(
    file: File,
    pageIndex: Int,
    totalPages: Int,
    invertNightMode: Boolean,
    repository: PdfRepository
) {
    val tokens = LocalPdfPlusTokens.current
    var bitmap by remember(file.absolutePath, file.length(), pageIndex, invertNightMode) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(file.absolutePath, file.length(), pageIndex, invertNightMode) {
        bitmap = repository.engine.renderPageBitmap(
            file = file,
            pageIndex = pageIndex,
            maxWidthPx = 1150,
            invertNightMode = invertNightMode
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (invertNightMode) Color(0xFF0A0A0F) else Color.White)
            .border(1.dp, tokens.glassBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null && !bmp.isRecycled) {
            val ratio = bmp.width.toFloat() / maxOf(1, bmp.height)
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1} of $totalPages",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = tokens.tertiaryCyan)
            }
        }
    }
}

@Composable
private fun PreviewActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    highlight: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val tokens = LocalPdfPlusTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (highlight) {
                    Modifier.background(
                        Brush.linearGradient(listOf(tokens.primaryGlow, tokens.secondaryGlow))
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) Color.White else tokens.textPrimary,
            maxLines = 1
        )
    }
}
