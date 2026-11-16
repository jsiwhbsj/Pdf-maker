package com.example.ui.screens.tools

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalPdfPlusTokens
import kotlinx.coroutines.launch
import java.io.File

private enum class SplitMode { SELECT_PAGES, RANGE_TEXT, EVERY_N }

@Composable
fun SplitPdfScreen(
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
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var mode by remember { mutableStateOf(SplitMode.SELECT_PAGES) }
    var rangeText by remember { mutableStateOf("1-2") }
    var everyNText by remember { mutableStateOf("2") }
    var outputName by remember { mutableStateOf("Split_${System.currentTimeMillis() % 10000}") }
    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var createdDocs by remember { mutableStateOf<List<PdfDocumentEntity>>(emptyList()) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        val doc = createdDocs.firstOrNull()
        if (dest != null && doc != null) {
            scope.launch { repository.exportPdfToUri(doc, dest) }
        }
    }

    LaunchedEffect(sourceFile) {
        val f = sourceFile ?: return@LaunchedEffect
        thumbnails = repository.engine.renderThumbnails(f, thumbWidthPx = 240)
        selectedPages = if (thumbnails.isNotEmpty()) setOf(0) else emptySet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("split_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolSplitPdf,
            subtitle = strings.toolSplitPdfDesc,
            icon = Icons.Rounded.CallSplit,
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
                        subtitle = "Pick a PDF from your vault or device storage to extract or split pages.",
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
                                Text("${thumbnails.size} ${strings.pagesLabel}", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                            }
                            FilterChip(
                                selected = true,
                                onClick = { showPicker = true },
                                label = { Text("Change") }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                SplitMode.SELECT_PAGES to "Tap Pages",
                                SplitMode.RANGE_TEXT to "Range",
                                SplitMode.EVERY_N to "Every N"
                            ).forEach { (m, label) ->
                                FilterChip(
                                    selected = mode == m,
                                    onClick = { mode = m },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        when (mode) {
                            SplitMode.SELECT_PAGES -> {
                                Text(
                                    text = "Tap pages to extract (${selectedPages.size} selected)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.textSecondary
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(thumbnails) { idx, bmp ->
                                        val isSelected = idx in selectedPages
                                        Box(
                                            modifier = Modifier
                                                .height(140.dp)
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
                                        }
                                    }
                                }
                            }
                            SplitMode.RANGE_TEXT -> {
                                OutlinedTextField(
                                    value = rangeText,
                                    onValueChange = { rangeText = it },
                                    label = { Text("Page range (e.g. 1-3, 5)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = tokens.primaryGlow,
                                        unfocusedBorderColor = tokens.glassBorder,
                                        focusedTextColor = tokens.textPrimary,
                                        unfocusedTextColor = tokens.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            SplitMode.EVERY_N -> {
                                OutlinedTextField(
                                    value = everyNText,
                                    onValueChange = { everyNText = it.filter(Char::isDigit) },
                                    label = { Text("Pages per split file") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = tokens.primaryGlow,
                                        unfocusedBorderColor = tokens.glassBorder,
                                        focusedTextColor = tokens.textPrimary,
                                        unfocusedTextColor = tokens.textPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { outputName = it },
                            label = { Text(strings.outputFileName) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = tokens.primaryGlow,
                                unfocusedBorderColor = tokens.glassBorder,
                                focusedTextColor = tokens.textPrimary,
                                unfocusedTextColor = tokens.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
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

            itemsIndexed(createdDocs) { _, doc ->
                SuccessResultCard(
                    document = doc,
                    onPreview = { onOpenViewer(doc) },
                    onShare = { repository.sharePdf(doc) },
                    onDownload = { exportLauncher.launch("${doc.displayName}.pdf") }
                )
            }
        }

        if (sourceFile != null) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = strings.toolSplitPdf,
                    icon = Icons.Rounded.CallSplit,
                    isLoading = isProcessing,
                    onClick = {
                        val input = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            errorMsg = null
                            createdDocs = emptyList()
                            runCatching {
                                when (mode) {
                                    SplitMode.SELECT_PAGES -> {
                                        val (outFile, clean) = repository.createUniqueVaultFile(outputName)
                                        repository.engine.splitPdfBySelection(input, selectedPages.sorted(), outFile)
                                        listOf(repository.registerCreatedPdf(outFile, clean, "SPLIT"))
                                    }
                                    SplitMode.RANGE_TEXT -> {
                                        val indices = parsePageRanges(rangeText, thumbnails.size)
                                        val (outFile, clean) = repository.createUniqueVaultFile(outputName)
                                        repository.engine.splitPdfBySelection(input, indices, outFile)
                                        listOf(repository.registerCreatedPdf(outFile, clean, "SPLIT"))
                                    }
                                    SplitMode.EVERY_N -> {
                                        val n = everyNText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                        val files = repository.engine.splitPdfEveryNPages(
                                            inputFile = input,
                                            chunkSize = n,
                                            outputDir = repository.vaultDir,
                                            baseName = outputName.ifBlank { "Split" }
                                        )
                                        files.map { f ->
                                            repository.registerCreatedPdf(f, f.nameWithoutExtension, "SPLIT")
                                        }
                                    }
                                }
                            }.onSuccess { createdDocs = it }
                                .onFailure { errorMsg = it.message ?: "Split failed" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "split_execute_btn"
                )
            }
        }
    }

    if (showPicker) {
        VaultPdfPickerSheet(
            documents = vaultDocuments,
            onSelectVaultDoc = { doc ->
                val f = File(doc.filePath)
                if (f.exists()) {
                    sourceFile = f
                    sourceName = doc.displayName
                    createdDocs = emptyList()
                }
            },
            onSelectExternalUri = { uri ->
                scope.launch {
                    runCatching {
                        val (file, name) = repository.copyUriToTempFile(uri)
                        sourceFile = file
                        sourceName = name
                        createdDocs = emptyList()
                    }
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}

private fun parsePageRanges(spec: String, maxPages: Int): List<Int> {
    val result = mutableListOf<Int>()
    spec.split(",").forEach { token ->
        val trimmed = token.trim()
        if ("-" in trimmed) {
            val parts = trimmed.split("-")
            val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (start != null && end != null) {
                for (p in minOf(start, end)..maxOf(start, end)) {
                    if (p in 1..maxPages) result.add(p - 1)
                }
            }
        } else {
            val single = trimmed.toIntOrNull()
            if (single != null && single in 1..maxPages) result.add(single - 1)
        }
    }
    if (result.isEmpty()) throw IllegalArgumentException("Enter valid page numbers between 1 and $maxPages.")
    return result.distinct()
}
