package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.PdfPageSize
import com.example.data.pdf.TextPdfTheme
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.SuccessResultCard
import com.example.ui.components.ToolHeader
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalPdfPlusTokens
import kotlinx.coroutines.launch

@Composable
fun WordToPdfScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var outputFileName by remember { mutableStateOf("Document_${System.currentTimeMillis() % 10000}") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var createdDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                errorMsg = null
                createdDoc = null
                val name = repository.resolveFileName(uri) ?: "Document.docx"
                runCatching {
                    repository.engine.extractWordOrTextContent(uri, name)
                }.onSuccess { text ->
                    selectedFileName = name
                    extractedText = text
                    outputFileName = name.substringBeforeLast(".")
                }.onFailure { err ->
                    errorMsg = err.message ?: "Unable to parse selected document"
                }
                isProcessing = false
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
            .testTag("word_to_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolWordToPdf,
            subtitle = strings.toolWordToPdfDesc,
            icon = Icons.Rounded.Description,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlowButton(
                    text = "Select .docx or .txt File",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = {
                        docPicker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                                "text/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "word2pdf_pick_btn"
                )
            }

            if (selectedFileName == null) {
                item {
                    EmptyStatePanel(
                        title = "Convert Word (.docx) & Text Files",
                        subtitle = "Extracts paragraphs from .docx, .txt, and .md documents (English & Bengali) and renders clean multi-page PDFs."
                    )
                }
            } else {
                item {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Text(
                            text = "Loaded: $selectedFileName",
                            style = MaterialTheme.typography.titleMedium,
                            color = ChampagneGold
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = outputFileName,
                            onValueChange = { outputFileName = it },
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
                        OutlinedTextField(
                            value = extractedText,
                            onValueChange = { extractedText = it },
                            label = { Text("Extracted Document Preview (Editable)") },
                            minLines = 7,
                            maxLines = 14,
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

        if (extractedText.isNotBlank()) {
            Box(modifier = Modifier.padding(16.dp)) {
                GlowButton(
                    text = strings.convertToPdf,
                    icon = Icons.Rounded.PictureAsPdf,
                    isLoading = isProcessing,
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            errorMsg = null
                            runCatching {
                                val (outFile, cleanName) = repository.createUniqueVaultFile(outputFileName)
                                repository.engine.textToPdf(
                                    title = outputFileName,
                                    body = extractedText,
                                    outputFile = outFile,
                                    fontSizePt = 12.5f,
                                    pageSize = PdfPageSize.A4,
                                    theme = TextPdfTheme.PAPER_WHITE
                                )
                                repository.registerCreatedPdf(outFile, cleanName, "WORD2PDF")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Conversion failed" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "word2pdf_convert_btn"
                )
            }
        }
    }
}
