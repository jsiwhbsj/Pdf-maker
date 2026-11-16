package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.WatermarkPosition
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
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AddWatermarkScreen(
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
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var rotation by remember { mutableFloatStateOf(-35f) }
    var fontSize by remember { mutableFloatStateOf(42f) }
    var position by remember { mutableStateOf(WatermarkPosition.DIAGONAL_CENTER) }
    var selectedColor by remember { mutableStateOf(ElectricViolet) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("add_watermark_screen")
    ) {
        ToolHeader(
            title = strings.toolAddWatermark,
            subtitle = strings.toolAddWatermarkDesc,
            icon = Icons.Rounded.BrandingWatermark,
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
                        subtitle = "Stamp custom English or Bengali watermarks diagonally, tiled, or as a footer across your PDF.",
                        ctaLabel = strings.selectPdf,
                        onCtaClick = { showPicker = true }
                    )
                }
            } else {
                item {
                    GlassCard(contentPadding = PaddingValues(14.dp)) {
                        Text("$sourceName.pdf", style = MaterialTheme.typography.titleMedium, color = ChampagneGold)
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Watermark Text (English / বাংলা)") },
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
                                .testTag("watermark_text_input")
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("Style", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                WatermarkPosition.DIAGONAL_CENTER to "Center",
                                WatermarkPosition.TILED to "Tiled",
                                WatermarkPosition.FOOTER to "Footer"
                            ).forEach { (pos, label) ->
                                FilterChip(
                                    selected = position == pos,
                                    onClick = { position = pos },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tokens.primaryGlow,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Color:", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                            listOf(ElectricViolet, LuminousCyan, ChampagneGold, CoralError, Color(0xFF475569)).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(
                                            width = if (selectedColor == c) 3.dp else 1.dp,
                                            color = if (selectedColor == c) Color.White else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = c }
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("Opacity: ${(opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.10f..0.85f)

                        Text("Angle: ${rotation.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
                        Slider(value = rotation, onValueChange = { rotation = it }, valueRange = -60f..60f)

                        Text("Text Size: ${fontSize.roundToInt()} pt", style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary)
                        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 20f..72f)
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
                    text = strings.toolAddWatermark,
                    icon = Icons.Rounded.Save,
                    enabled = watermarkText.isNotBlank(),
                    isLoading = isProcessing,
                    onClick = {
                        val f = sourceFile ?: return@GlowButton
                        scope.launch {
                            isProcessing = true
                            errorMsg = null
                            runCatching {
                                val (outFile, cleanName) = repository.createUniqueVaultFile("${sourceName}_watermarked")
                                repository.engine.addWatermarkToPdf(
                                    inputFile = f,
                                    outputFile = outFile,
                                    watermarkText = watermarkText,
                                    textColorInt = selectedColor.toArgb(),
                                    opacity = opacity,
                                    rotationDegrees = rotation,
                                    fontSizeSp = fontSize,
                                    position = position
                                )
                                repository.registerCreatedPdf(outFile, cleanName, "WATERMARK")
                            }.onSuccess { createdDoc = it }
                                .onFailure { errorMsg = it.message ?: "Failed to add watermark" }
                            isProcessing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "watermark_apply_btn"
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
