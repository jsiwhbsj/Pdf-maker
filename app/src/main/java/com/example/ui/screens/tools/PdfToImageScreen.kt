package com.example.ui.screens.tools

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.ToolHeader
import com.example.ui.components.VaultPdfPickerSheet
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalPdfPlusTokens
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfToImageScreen(
    initialDocumentId: Long,
    repository: PdfRepository,
    vaultDocuments: List<PdfDocumentEntity>,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()

    var sourceFile by remember { mutableStateOf<File?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isPng by remember { mutableStateOf(true) }
    var dpi by remember { mutableIntStateOf(150) }
    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var exportedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
        thumbnails = repository.engine.renderThumbnails(f, thumbWidthPx = 220)
        selectedPages = thumbnails.indices.toSet()
        exportedUris = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("pdf_to_image_screen")
    ) {
        ToolHeader(
            title = strings.toolPdfToImage,
            subtitle = strings.toolPdfToImageDesc,
            icon = Icons.Rounded.PhotoLibrary,
            onBack = onBack
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
                        subtitle = "Export crisp PNG or JPEG images of every page directly to Pictures/PDF PLUS.",
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$sourceName.pdf", style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                Text("${selectedPages.size} / ${thumbnails.size} selected", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                            }
                            FilterChip(selected = true, onClick = { showPicker = true }, label = { Text("Change") })
                        }

                        Spacer(Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(thumbnails) { idx, bmp ->
                                val isSelected = idx in selectedPages
                                Box(
                                    modifier = Modifier
                                        .height(130.dp)
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) EmeraldSuccess else tokens.glassBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            selectedPages = if (isSelected) selectedPages - idx else selectedPages + idx
                                        }
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = EmeraldSuccess,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(99.dp))
                                        )
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = "P.${idx + 1}",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Image Format & Quality", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isPng,
                                onClick = { isPng = true },
                                label = { Text("PNG (Lossless)") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tokens.primaryGlow, selectedLabelColor = Color.White)
                            )
                            FilterChip(
                                selected = !isPng,
                                onClick = { isPng = false },
                                label = { Text("JPEG") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tokens.primaryGlow, selectedLabelColor = Color.White)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = dpi == 150,
                                onClick = { dpi = 150 },
                                label = { Text("150 DPI") }
                            )
                            FilterChip(
                                selected = dpi == 300,
                                onClick = { dpi = 300 },
                                label = { Text("300 DPI HD") }
                            )
                        }
                    }
                }
            }

            if (isProcessing) {
                item { PdfProcessingBanner(label = strings.processing, progress = progress) }
            }

            errorMsg?.let { err ->
                item { GlassCard { Text(err, color = CoralError) } }
            }

            if (exportedUris.isNotEmpty()) {
                item {
                    GlassCard(contentPadding = PaddingValues(16.dp)) {
                        Text(
                            text = "Saved ${exportedUris.size} images to Pictures/PDF PLUS",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldSuccess
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(exportedUris) { idx, uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Exported ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(110.dp)
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, EmeraldSuccess.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sourceFile != null && selectedPages.isNotEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = "Export ${selectedPages.size} Images",
                    icon = Icons.Rounded.PhotoLibrary,
                    isLoading = isProcessing,
                    onClick = {
                        val f = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            progress = 0f
                            errorMsg = null
                            runCatching {
                                repository.engine.pdfToImagesToGallery(
                                    inputFile = f,
                                    pageIndices = selectedPages.sorted(),
                                    isPng = isPng,
                                    dpi = dpi,
                                    baseName = sourceName.ifBlank { "Page" },
                                    onProgress = { progress = it }
                                )
                            }.onSuccess { exportedUris = it }
                                .onFailure { errorMsg = it.message ?: "Export failed" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "pdf2img_export_btn"
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
