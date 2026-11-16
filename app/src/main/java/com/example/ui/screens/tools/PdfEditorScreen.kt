package com.example.ui.screens.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.EditablePageSpec
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

private data class EditorPageUi(
    val uid: Long,
    val originalIndex: Int,
    val rotationDegrees: Int,
    val thumbnail: Bitmap
)

@Composable
fun PdfEditorScreen(
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
    var pages by remember { mutableStateOf<List<EditorPageUi>>(emptyList()) }
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
        val thumbs = repository.engine.renderThumbnails(f, thumbWidthPx = 280)
        pages = thumbs.mapIndexed { idx, bmp ->
            EditorPageUi(uid = idx.toLong() + System.nanoTime(), originalIndex = idx, rotationDegrees = 0, thumbnail = bmp)
        }
        createdDoc = null
        errorMsg = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("pdf_editor_screen")
    ) {
        ToolHeader(
            title = strings.toolPdfEditor,
            subtitle = if (pages.isNotEmpty()) "$sourceName.pdf • ${pages.size} ${strings.pagesLabel}" else strings.toolPdfEditorDesc,
            icon = Icons.Rounded.EditNote,
            onBack = onBack,
            trailingContent = {
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = strings.selectPdf, tint = tokens.textPrimary)
                }
            }
        )

        if (sourceFile == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePanel(
                    title = strings.selectPdf,
                    subtitle = "Rotate individual pages 90°, reorder pages, or delete unwanted pages cleanly.",
                    ctaLabel = strings.selectPdf,
                    onCtaClick = { showPicker = true }
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(pages, key = { _, item -> item.uid }) { idx, page ->
                    GlassCard(contentPadding = PaddingValues(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, tokens.glassBorder, RoundedCornerShape(14.dp))
                        ) {
                            Image(
                                bitmap = page.thumbnail.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = page.rotationDegrees.toFloat() }
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "P.${idx + 1}" + if (page.rotationDegrees != 0) " (${page.rotationDegrees}°)" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        pages = pages.toMutableList().apply {
                                            val item = removeAt(idx)
                                            add(idx - 1, item)
                                        }
                                    }
                                },
                                enabled = idx > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move earlier", tint = tokens.textPrimary)
                            }
                            IconButton(
                                onClick = {
                                    pages = pages.toMutableList().also {
                                        it[idx] = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90", tint = tokens.tertiaryCyan)
                            }
                            IconButton(
                                onClick = {
                                    if (idx < pages.lastIndex) {
                                        pages = pages.toMutableList().apply {
                                            val item = removeAt(idx)
                                            add(idx + 1, item)
                                        }
                                    }
                                },
                                enabled = idx < pages.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move later", tint = tokens.textPrimary)
                            }
                            IconButton(
                                onClick = {
                                    if (pages.size > 1) {
                                        pages = pages.toMutableList().also { it.removeAt(idx) }
                                    } else {
                                        errorMsg = "Cannot delete the last remaining page."
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete page", tint = CoralError)
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    PdfProcessingBanner(label = strings.processing)
                }
            }

            errorMsg?.let { err ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassCard { Text(err, color = CoralError) }
                }
            }

            createdDoc?.let { doc ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SuccessResultCard(
                        document = doc,
                        onPreview = { onOpenViewer(doc) },
                        onShare = { repository.sharePdf(doc) },
                        onDownload = { exportLauncher.launch("${doc.displayName}.pdf") }
                    )
                }
            }

            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = strings.savePdf,
                    icon = Icons.Rounded.Save,
                    isLoading = isProcessing,
                    enabled = pages.isNotEmpty(),
                    onClick = {
                        val f = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            errorMsg = null
                            runCatching {
                                val (outFile, cleanName) = repository.createUniqueVaultFile("${sourceName}_edited")
                                repository.engine.editPdfPages(
                                    inputFile = f,
                                    pageSpecs = pages.map { EditablePageSpec(it.originalIndex, it.rotationDegrees) },
                                    outputFile = outFile
                                )
                                repository.registerCreatedPdf(outFile, cleanName, "EDITED")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Failed to save edited PDF" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "editor_save_btn"
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
