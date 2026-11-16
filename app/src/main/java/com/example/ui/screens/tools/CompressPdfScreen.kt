package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.CompressionLevel
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
import kotlin.math.roundToInt

@Composable
fun CompressPdfScreen(
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
    var level by remember { mutableStateOf(CompressionLevel.BALANCED) }
    var outputName by remember { mutableStateOf("Compressed_${System.currentTimeMillis() % 10000}") }
    var showPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var savingsNote by remember { mutableStateOf<String?>(null) }
    var createdDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { dest ->
        val doc = createdDoc
        if (dest != null && doc != null) {
            scope.launch { repository.exportPdfToUri(doc, dest) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("compress_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolCompressPdf,
            subtitle = strings.toolCompressPdfDesc,
            icon = Icons.Rounded.Compress,
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
                        subtitle = "Select a PDF to reduce file size with smart DPI and image recompression.",
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
                                Text(
                                    text = "Original size: ${PdfRepository.formatBytes(sourceFile?.length() ?: 0L)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                            }
                            FilterChip(selected = true, onClick = { showPicker = true }, label = { Text("Change") })
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("Compression Preset", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompressionLevel.entries.forEach { preset ->
                                FilterChip(
                                    selected = level == preset,
                                    onClick = { level = preset },
                                    label = { Text(preset.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { outputName = it },
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
                    }
                }
            }

            if (isProcessing) {
                item { PdfProcessingBanner(label = strings.processing, progress = progress) }
            }

            errorMsg?.let { err ->
                item { GlassCard { Text(err, color = CoralError) } }
            }

            createdDoc?.let { doc ->
                item {
                    SuccessResultCard(
                        document = doc,
                        extraNote = savingsNote,
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
                    text = strings.toolCompressPdf,
                    icon = Icons.Rounded.Compress,
                    isLoading = isProcessing,
                    onClick = {
                        val input = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            progress = 0f
                            errorMsg = null
                            runCatching {
                                val (outFile, clean) = repository.createUniqueVaultFile(outputName)
                                val report = repository.engine.compressPdf(input, outFile, level) { progress = it }
                                val savedPct = if (report.originalBytes > 0) {
                                    (((report.originalBytes - report.compressedBytes).toFloat() / report.originalBytes) * 100f).roundToInt()
                                } else 0
                                savingsNote = if (savedPct >= 0) {
                                    "Reduced ${PdfRepository.formatBytes(report.originalBytes)} → ${PdfRepository.formatBytes(report.compressedBytes)} ($savedPct% smaller)"
                                } else {
                                    "Output size: ${PdfRepository.formatBytes(report.compressedBytes)} (already tightly optimized)"
                                }
                                repository.registerCreatedPdf(outFile, clean, "COMPRESSED")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Compression failed" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "compress_execute_btn"
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
                    outputName = "${doc.displayName}_compressed"
                    createdDoc = null
                }
            },
            onSelectExternalUri = { uri ->
                scope.launch {
                    runCatching {
                        val (file, name) = repository.copyUriToTempFile(uri)
                        sourceFile = file
                        sourceName = name
                        outputName = "${name}_compressed"
                        createdDoc = null
                    }
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}
