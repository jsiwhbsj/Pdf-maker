package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CoralError
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.RoseMagenta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbnailMemoryCache {
    val cache = LruCache<String, Bitmap>(40)
}

@Composable
fun PdfThumbnailPreview(
    filePath: String,
    fileSizeBytes: Long,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPdfPlusTokens.current
    val cacheKey = "$filePath@$fileSizeBytes"
    var bitmap by remember(cacheKey) { mutableStateOf(ThumbnailMemoryCache.cache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(filePath)
                    if (!file.exists()) return@runCatching null
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            if (renderer.pageCount <= 0) return@runCatching null
                            renderer.openPage(0).use { page ->
                                val targetW = 160
                                val scale = targetW.toFloat() / maxOf(1, page.width)
                                val targetH = maxOf(1, (page.height * scale).toInt())
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ThumbnailMemoryCache.cache.put(cacheKey, bmp)
                                bmp
                            }
                        }
                    }
                }.getOrNull()
            }
            bitmap = loaded
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(tokens.primaryGlow.copy(alpha = 0.25f), tokens.secondaryGlow.copy(alpha = 0.2f))
                )
            )
            .border(1.dp, tokens.glassBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "PDF preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PictureAsPdf,
                contentDescription = "PDF icon",
                tint = tokens.primaryGlow,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun DocumentFileCard(
    doc: PdfDocumentEntity,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}"),
        onClick = onOpen,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PdfThumbnailPreview(
                filePath = doc.filePath,
                fileSizeBytes = doc.fileSizeBytes,
                modifier = Modifier.size(width = 56.dp, height = 72.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tokens.primaryGlow.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = doc.sourceTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.tertiaryCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${doc.pageCount} ${strings.pagesLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChampagneGold
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${doc.displayName}.pdf",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${PdfRepository.formatBytes(doc.fileSizeBytes)}  •  ${PdfRepository.formatDate(doc.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("doc_fav_${doc.id}")
            ) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = strings.favorite,
                    tint = if (doc.isFavorite) RoseMagenta else tokens.textSecondary
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("doc_menu_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Document actions",
                        tint = tokens.textSecondary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(strings.share) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.exportDownload) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.rename) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(strings.delete, color = CoralError) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = CoralError) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentDocMiniCard(
    doc: PdfDocumentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    GlassCard(
        modifier = modifier
            .width(152.dp)
            .testTag("recent_doc_${doc.id}"),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(10.dp)
    ) {
        PdfThumbnailPreview(
            filePath = doc.filePath,
            fileSizeBytes = doc.fileSizeBytes,
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = doc.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${doc.pageCount} ${strings.pagesLabel} • ${PdfRepository.formatBytes(doc.fileSizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultPdfPickerSheet(
    documents: List<PdfDocumentEntity>,
    onSelectVaultDoc: (PdfDocumentEntity) -> Unit,
    onSelectExternalUri: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSelectExternalUri(uri)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.glassSurfaceStrong,
        contentColor = tokens.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = strings.selectPdf,
                style = MaterialTheme.typography.headlineMedium,
                color = tokens.textPrimary
            )
            Spacer(Modifier.height(12.dp))

            GlowButton(
                text = strings.browseDevice,
                icon = Icons.Rounded.FolderOpen,
                onClick = { openDocLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "picker_browse_device"
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = strings.selectFromVault,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textSecondary
            )
            Spacer(Modifier.height(8.dp))

            if (documents.isEmpty()) {
                Text(
                    text = strings.noDocsYetSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(documents, key = { it.id }) { doc ->
                        GlassCard(
                            onClick = {
                                onSelectVaultDoc(doc)
                                onDismiss()
                            },
                            contentPadding = PaddingValues(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PdfThumbnailPreview(
                                    filePath = doc.filePath,
                                    fileSizeBytes = doc.fileSizeBytes,
                                    modifier = Modifier.size(width = 40.dp, height = 52.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${doc.displayName}.pdf",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.pageCount} ${strings.pagesLabel} • ${PdfRepository.formatBytes(doc.fileSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDocumentDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    var text by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(strings.outputFileName) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    docName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.deleteConfirmTitle) },
        text = { Text("${strings.deleteConfirmBody}\n\n\"$docName.pdf\"") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.delete, color = CoralError, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
