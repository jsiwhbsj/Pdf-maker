package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.localization.LocalAppStrings
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentFileCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlowButton
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.viewmodel.FileFilter

@Composable
fun FilesScreen(
    favoritesOnly: Boolean,
    documents: List<PdfDocumentEntity>,
    searchQuery: String,
    activeFilter: FileFilter,
    onSearchChange: (String) -> Unit,
    onFilterChange: (FileFilter) -> Unit,
    onImportPdf: (android.net.Uri) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onToggleFavorite: (PdfDocumentEntity) -> Unit,
    onShareDocument: (PdfDocumentEntity) -> Unit,
    onExportDocument: (PdfDocumentEntity, android.net.Uri) -> Unit,
    onRenameDocument: (PdfDocumentEntity, String) -> Unit,
    onDeleteDocument: (PdfDocumentEntity) -> Unit,
    onCreatePdfClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current

    var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var exportTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportPdf(uri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val doc = exportTarget
        if (destUri != null && doc != null) {
            onExportDocument(doc, destUri)
        }
        exportTarget = null
    }

    val displayDocs = remember(documents, searchQuery, favoritesOnly) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) documents else documents.filter { it.displayName.lowercase().contains(q) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag(if (favoritesOnly) "favorites_screen" else "files_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (favoritesOnly) strings.favoritesTitle else strings.allDocuments,
                        style = MaterialTheme.typography.headlineLarge,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = "${displayDocs.size} PDFs",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textSecondary
                    )
                }

                if (!favoritesOnly) {
                    GlowButton(
                        text = strings.importPdf,
                        icon = Icons.Rounded.UploadFile,
                        onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                        testTag = "files_import_button"
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchPlaceholder, color = tokens.textSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.tertiaryCyan) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.glassSurface,
                    unfocusedContainerColor = tokens.glassSurface,
                    focusedBorderColor = tokens.primaryGlow,
                    unfocusedBorderColor = tokens.glassBorder,
                    focusedTextColor = tokens.textPrimary,
                    unfocusedTextColor = tokens.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("files_search_input")
            )
        }

        if (!favoritesOnly) {
            item {
                val chips = listOf(
                    FileFilter.ALL to strings.filterAll,
                    FileFilter.RECENT to strings.filterRecent,
                    FileFilter.CREATED to strings.filterCreated,
                    FileFilter.IMPORTED to strings.filterImported
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips) { (filter, label) ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primaryGlow,
                                selectedLabelColor = Color.White,
                                containerColor = tokens.glassSurface,
                                labelColor = tokens.textSecondary
                            )
                        )
                    }
                }
            }
        }

        if (displayDocs.isEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                EmptyStatePanel(
                    title = if (favoritesOnly) strings.noFavoritesTitle else strings.noDocsYetTitle,
                    subtitle = if (favoritesOnly) strings.noFavoritesSubtitle else strings.noDocsYetSubtitle,
                    ctaLabel = if (favoritesOnly) null else strings.createFirstPdf,
                    onCtaClick = if (favoritesOnly) null else onCreatePdfClick,
                    secondaryCtaLabel = if (favoritesOnly) null else strings.importPdf,
                    onSecondaryClick = if (favoritesOnly) null else {
                        { importLauncher.launch(arrayOf("application/pdf")) }
                    }
                )
            }
        } else {
            items(displayDocs, key = { it.id }) { doc ->
                DocumentFileCard(
                    doc = doc,
                    onOpen = { onOpenDocument(doc) },
                    onToggleFavorite = { onToggleFavorite(doc) },
                    onShare = { onShareDocument(doc) },
                    onDownload = {
                        exportTarget = doc
                        exportLauncher.launch("${doc.displayName}.pdf")
                    },
                    onRename = { renameTarget = doc },
                    onDelete = { deleteTarget = doc }
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameDocumentDialog(
            currentName = target.displayName,
            onConfirm = { newName ->
                onRenameDocument(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            docName = target.displayName,
            onConfirm = {
                onDeleteDocument(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
