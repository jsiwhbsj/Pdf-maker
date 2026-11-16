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
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.example.data.pdf.PdfPageSize
import com.example.data.pdf.TextPdfTheme
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.PdfProcessingBanner
import com.example.ui.components.SuccessResultCard
import com.example.ui.components.ToolHeader
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalPdfPlusTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TextToPdfScreen(
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenViewer: (PdfDocumentEntity) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val scope = rememberCoroutineScope()

    var docTitle by remember { mutableStateOf("") }
    var docBody by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("Note_${System.currentTimeMillis() % 10000}") }
    var fontSize by remember { mutableFloatStateOf(13f) }
    var selectedTheme by remember { mutableStateOf(TextPdfTheme.PAPER_WHITE) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var useMono by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("text_to_pdf_screen")
    ) {
        ToolHeader(
            title = strings.toolTextToPdf,
            subtitle = strings.toolTextToPdfDesc,
            icon = Icons.Rounded.TextFields,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("Document Heading (English / বাংলা)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primaryGlow,
                            unfocusedBorderColor = tokens.glassBorder,
                            focusedTextColor = tokens.textPrimary,
                            unfocusedTextColor = tokens.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text2pdf_title_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = docBody,
                        onValueChange = { docBody = it },
                        label = { Text("Write or paste your text...") },
                        minLines = 7,
                        maxLines = 14,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primaryGlow,
                            unfocusedBorderColor = tokens.glassBorder,
                            focusedTextColor = tokens.textPrimary,
                            unfocusedTextColor = tokens.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text2pdf_body_input")
                    )

                    Spacer(Modifier.height(6.dp))
                    val words = docBody.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    Text(
                        text = "$words words • ${docBody.length} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                }
            }

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

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Font Size: ${fontSize.roundToInt()} pt",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.textPrimary
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 10f..22f
                    )

                    Text("Paper Style", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextPdfTheme.entries.forEach { t ->
                            FilterChip(
                                selected = selectedTheme == t,
                                onClick = { selectedTheme = t },
                                label = { Text(t.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tokens.primaryGlow,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(PdfPageSize.A4, PdfPageSize.LETTER).forEach { s ->
                                FilterChip(
                                    selected = pageSize == s,
                                    onClick = { pageSize = s },
                                    label = { Text(s.label) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mono", style = MaterialTheme.typography.labelMedium, color = tokens.textSecondary)
                            Switch(checked = useMono, onCheckedChange = { useMono = it })
                        }
                    }
                }
            }

            if (isProcessing) {
                item { PdfProcessingBanner(label = strings.processing) }
            }

            errorMsg?.let { err ->
                item {
                    GlassCard { Text(err, color = CoralError) }
                }
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

        Box(modifier = Modifier.padding(16.dp)) {
            GlowButton(
                text = strings.convertToPdf,
                icon = Icons.Rounded.PictureAsPdf,
                enabled = docTitle.isNotBlank() || docBody.isNotBlank(),
                isLoading = isProcessing,
                onClick = {
                    scope.launch {
                        isProcessing = true
                        errorMsg = null
                        runCatching {
                            val (outFile, cleanName) = repository.createUniqueVaultFile(fileName)
                            repository.engine.textToPdf(
                                title = docTitle,
                                body = docBody,
                                outputFile = outFile,
                                fontSizePt = fontSize,
                                pageSize = pageSize,
                                theme = selectedTheme,
                                useMonoFont = useMono
                            )
                            repository.registerCreatedPdf(outFile, cleanName, "TEXT2PDF")
                        }.onSuccess { createdDoc = it }
                            .onFailure { errorMsg = it.message ?: "Failed to create PDF" }
                        isProcessing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                testTag = "text2pdf_convert_btn"
            )
        }
    }
}
