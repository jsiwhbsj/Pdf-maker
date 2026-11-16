package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.PdfDocumentDao
import com.example.data.local.PdfDocumentEntity
import com.example.data.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfRepository(
    private val context: Context,
    private val dao: PdfDocumentDao,
    val engine: PdfEngine
) {
    val vaultDir: File = File(context.filesDir, "pdf_vault").apply { mkdirs() }
    val tempDir: File = File(context.cacheDir, "pdf_temp").apply { mkdirs() }
    val sharedDir: File = File(context.cacheDir, "shared").apply { mkdirs() }

    val allDocuments: Flow<List<PdfDocumentEntity>> = dao.observeAll()
    val recentDocuments: Flow<List<PdfDocumentEntity>> = dao.observeRecent()
    val favoriteDocuments: Flow<List<PdfDocumentEntity>> = dao.observeFavorites()

    suspend fun getById(id: Long): PdfDocumentEntity? = dao.getById(id)

    fun createUniqueVaultFile(requestedBaseName: String): Pair<File, String> {
        val sanitized = requestedBaseName
            .trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "PDF_PLUS_${System.currentTimeMillis()}" }
        var candidate = File(vaultDir, "$sanitized.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(vaultDir, "${sanitized}_$counter.pdf")
            counter++
        }
        return candidate to candidate.nameWithoutExtension
    }

    suspend fun registerCreatedPdf(
        file: File,
        displayName: String,
        sourceTag: String
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val pages = engine.getPageCount(file)
        val cleanName = displayName.removeSuffix(".pdf").removeSuffix(".PDF")
        val entity = PdfDocumentEntity(
            displayName = cleanName,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            pageCount = pages,
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            isFavorite = false,
            sourceTag = sourceTag
        )
        val id = dao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun importPdfFromUri(uri: Uri): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val rawName = resolveFileName(uri) ?: "Imported_${System.currentTimeMillis()}.pdf"
        val (destFile, finalName) = createUniqueVaultFile(rawName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Cannot open selected PDF file.")
        val pages = engine.getPageCount(destFile)
        if (pages <= 0) {
            destFile.delete()
            throw IllegalArgumentException("Selected file is not a valid PDF document.")
        }
        val entity = PdfDocumentEntity(
            displayName = finalName,
            filePath = destFile.absolutePath,
            fileSizeBytes = destFile.length(),
            pageCount = pages,
            sourceTag = "IMPORTED"
        )
        val id = dao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun copyUriToTempFile(uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val rawName = resolveFileName(uri) ?: "doc_${System.currentTimeMillis()}.pdf"
        val outFile = File(tempDir, "${System.currentTimeMillis()}_${rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read PDF.")
        outFile to rawName.removeSuffix(".pdf").removeSuffix(".PDF")
    }

    suspend fun refreshDocumentStats(doc: PdfDocumentEntity) = withContext(Dispatchers.IO) {
        val f = File(doc.filePath)
        if (f.exists()) {
            val pages = engine.getPageCount(f)
            dao.updateStats(doc.id, f.length(), pages)
        }
    }

    suspend fun exportPdfToUri(entity: PdfDocumentEntity, destUri: Uri) = withContext(Dispatchers.IO) {
        val src = File(entity.filePath)
        if (!src.exists()) throw IllegalStateException("Source PDF file not found.")
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            src.inputStream().use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Unable to write to destination.")
    }

    fun sharePdf(entity: PdfDocumentEntity) {
        val file = File(entity.filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, entity.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, entity.displayName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openPdfExternally(entity: PdfDocumentEntity): Boolean {
        val file = File(entity.filePath)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "${entity.displayName}.pdf").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    suspend fun toggleFavorite(entity: PdfDocumentEntity) {
        dao.setFavorite(entity.id, !entity.isFavorite)
    }

    suspend fun touchOpened(id: Long) {
        dao.touchLastOpened(id)
    }

    suspend fun renameDocument(entity: PdfDocumentEntity, newNameRaw: String): Boolean = withContext(Dispatchers.IO) {
        val clean = newNameRaw.trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isBlank()) return@withContext false
        val oldFile = File(entity.filePath)
        val targetFile = File(vaultDir, "$clean.pdf")
        if (targetFile.exists() && targetFile.absolutePath != oldFile.absolutePath) {
            return@withContext false
        }
        val renamed = if (oldFile.exists()) oldFile.renameTo(targetFile) else true
        if (renamed) {
            dao.rename(entity.id, clean, targetFile.absolutePath)
        }
        renamed
    }

    suspend fun deleteDocument(entity: PdfDocumentEntity) = withContext(Dispatchers.IO) {
        runCatching { File(entity.filePath).delete() }
        dao.deleteById(entity.id)
    }

    fun resolveFileName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.2f MB", mb)
        }

        fun formatDate(millis: Long): String {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return sdf.format(Date(millis))
        }
    }
}
