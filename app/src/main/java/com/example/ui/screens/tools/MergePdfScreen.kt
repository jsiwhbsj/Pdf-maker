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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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

private data class MergeSourceItem(
    val uid: String,
    val name: String,
    val file: File,
    val pageCount: Int
)

@Composable
fun MergePdfScreen(
    repository: PdfRepository,
    vaultDocuments: List<PdfDocumentEntity>,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<MergeSourceItem>>(emptyList()) }
    var fileName by remember { mutableStateOf("Merged_${System.currentTimeMillis() % 10000}") }
    var showVaultPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var createdDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val multiOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = uris.mapNotNull { uri ->
                    runCatching {
                        val (file, name) = repository.copyUriToTempFile(uri)
                        val pages = repository.engine.getPageCount(file)
                        if (pages > 0) MergeSourceItem("${file.name}_${System.nanoTime()}", name, file, pages) else null
                    }.getOrNull()
                }
                sources = sources + added
                createdDoc = null
            }
        }
    }

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
            .testTag("merge_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolMergePdf,
            subtitle = strings.toolMergePdfDesc,
            icon = Icons.Rounded.MergeType,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlowButton(
                        text = strings.selectFromVault,
                        icon = Icons.Rounded.Add,
                        onClick = { showVaultPicker = true },
                        modifier = Modifier.weight(1f),
                        testTag = "merge_add_vault_btn"
                    )
                    OutlinedButton(
                        onClick = { multiOpenLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("merge_add_device_btn"),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(strings.browseDevice, color = tokens.textPrimary)
                    }
                }
            }

            if (sources.isEmpty()) {
                item {
                    EmptyStatePanel(
                        title = strings.selectPdf,
                        subtitle = "Add 2 or more PDF files and arrange them in the exact merge order."
                    )
                }
            } else {
                itemsIndexed(sources, key = { _, item -> item.uid }) { idx, item ->
                    GlassCard(contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = tokens.primaryGlow)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${item.name}.pdf",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.pageCount} ${strings.pagesLabel} • ${PdfRepository.formatBytes(item.file.length())}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        sources = sources.toMutableList().apply {
                                            val moved = removeAt(idx)
                                            add(idx - 1, moved)
                                        }
                                    }
                                },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", tint = tokens.textPrimary)
                            }
                            IconButton(
                                onClick = {
                                    if (idx < sources.lastIndex) {
                                        sources = sources.toMutableList().apply {
                                            val moved = removeAt(idx)
                                            add(idx + 1, moved)
                                        }
                                    }
                                },
                                enabled = idx < sources.lastIndex
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", tint = tokens.textPrimary)
                            }
                            IconButton(
                                onClick = {
                                    sources = sources.toMutableList().also { it.removeAt(idx) }
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = CoralError)
                            }
                        }
                    }
                }

                item {
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
                        onPreview = { onOpenViewer(doc) },
                        onShare = { repository.sharePdf(doc) },
                        onDownload = { exportLauncher.launch("${doc.displayName}.pdf") }
                    )
                }
            }
        }

        if (sources.size >= 2) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = "${strings.toolMergePdf} (${sources.size})",
                    icon = Icons.Rounded.MergeType,
                    isLoading = isProcessing,
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            progress = 0f
                            errorMsg = null
                            runCatching {
                                val (outFile, cleanName) = repository.createUniqueVaultFile(fileName)
                                repository.engine.mergePdfs(
                                    inputFiles = sources.map { it.file },
                                    outputFile = outFile,
                                    onProgress = { progress = it }
                                )
                                repository.registerCreatedPdf(outFile, cleanName, "MERGED")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Merge failed" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "merge_execute_btn"
                )
            }
        }
    }

    if (showVaultPicker) {
        VaultPdfPickerSheet(
            documents = vaultDocuments,
            onSelectVaultDoc = { doc ->
                val f = File(doc.filePath)
                if (f.exists()) {
                    sources = sources + MergeSourceItem("${doc.id}_${System.nanoTime()}", doc.displayName, f, doc.pageCount)
                    createdDoc = null
                }
            },
            onSelectExternalUri = { uri ->
                scope.launch {
                    runCatching {
                        val (file, name) = repository.copyUriToTempFile(uri)
                        val pages = repository.engine.getPageCount(file)
                        if (pages > 0) {
                            sources = sources + MergeSourceItem("${file.name}_${System.nanoTime()}", name, file, pages)
                        }
                    }
                }
            },
            onDismiss = { showVaultPicker = false }
        )
    }
}
