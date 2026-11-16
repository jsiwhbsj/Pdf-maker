package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import android.media.ExifInterface
import com.example.R
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfPageSize(val widthPt: Int, val heightPt: Int, val label: String) {
    A4(595, 842, "A4"),
    LETTER(612, 792, "Letter"),
    FIT_IMAGE(0, 0, "Fit")
}

enum class PageMargin(val pt: Int, val label: String) {
    NONE(0, "None"),
    SMALL(18, "Small"),
    STANDARD(36, "Standard")
}

enum class PageOrientation { AUTO, PORTRAIT, LANDSCAPE }

enum class CompressionLevel(val dpi: Int, val jpegQuality: Int, val label: String) {
    STRONG(105, 45, "Max Compression"),
    BALANCED(150, 68, "Balanced"),
    HIGH_QUALITY(210, 86, "High Quality")
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    MAGIC("Magic Color"),
    BW_DOC("Crisp B&W"),
    GRAYSCALE("Grayscale")
}

enum class TextPdfTheme(val bgColor: Int, val textColor: Int, val accentColor: Int, val label: String) {
    PAPER_WHITE(Color.WHITE, Color.parseColor("#181A24"), Color.parseColor("#6D28D9"), "Clean White"),
    WARM_CREAM(Color.parseColor("#FAF6EE"), Color.parseColor("#2A241E"), Color.parseColor("#B45309"), "Book Cream"),
    MIDNIGHT_DARK(Color.parseColor("#0B0C14"), Color.parseColor("#F3F4FA"), Color.parseColor("#8B5CF6"), "Obsidian Dark")
}

enum class WatermarkPosition { DIAGONAL_CENTER, TILED, FOOTER }

data class EditablePageSpec(
    val originalIndex: Int,
    val rotationDegrees: Int = 0
)

data class CompressionReport(
    val outputFile: File,
    val originalBytes: Long,
    val compressedBytes: Long,
    val pageCount: Int
)

data class GalleryImageItem(
    val uri: Uri,
    val rotationDegrees: Int = 0
)

class PdfEngine(private val context: Context) {

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext 0
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        maxWidthPx: Int = 1080,
        invertNightMode: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = maxWidthPx.toFloat() / max(1, page.width)
                        val targetW = max(1, (page.width * scale).roundToInt()).coerceAtMost(2400)
                        val targetH = max(1, (page.height * scale).roundToInt()).coerceAtMost(3400)
                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (invertNightMode) {
                            applyInvertFilter(bitmap)
                        } else {
                            bitmap
                        }
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun renderThumbnails(
        file: File,
        thumbWidthPx: Int = 280,
        maxPages: Int = 60
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val bitmaps = mutableListOf<Bitmap>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = min(renderer.pageCount, maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val scale = thumbWidthPx.toFloat() / max(1, page.width)
                            val w = max(1, (page.width * scale).roundToInt())
                            val h = max(1, (page.height * scale).roundToInt())
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
        }
        bitmaps
    }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        margin: PageMargin = PageMargin.STANDARD,
        orientation: PageOrientation = PageOrientation.AUTO,
        onProgress: (Float) -> Unit = {}
    ): Int = imagesToPdfWithRotations(
        images = imageUris.map { GalleryImageItem(it, 0) },
        outputFile = outputFile,
        pageSize = pageSize,
        margin = margin,
        orientation = orientation,
        onProgress = onProgress
    )

    suspend fun imagesToPdfWithRotations(
        images: List<GalleryImageItem>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        margin: PageMargin = PageMargin.STANDARD,
        orientation: PageOrientation = PageOrientation.AUTO,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        var createdPages = 0
        try {
            images.forEachIndexed { idx, item ->
                val raw = decodeSampledBitmapFromUri(item.uri, 1800) ?: return@forEachIndexed
                val exifBmp = applyExifOrientationIfNeeded(item.uri, raw)
                val bmp = rotateBitmapIfNeeded(exifBmp, item.rotationDegrees)
                try {
                    val (pageW, pageH) = resolvePageDimensions(bmp.width, bmp.height, pageSize, orientation)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, createdPages + 1).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val m = if (pageSize == PdfPageSize.FIT_IMAGE) 0f else margin.pt.toFloat()
                    val availW = max(1f, pageW - 2 * m)
                    val availH = max(1f, pageH - 2 * m)
                    val scale = min(availW / bmp.width, availH / bmp.height)
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bmp, null, RectF(left, top, left + drawW, top + drawH), paint)
                    pdf.finishPage(page)
                    createdPages++
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
                onProgress((idx + 1).toFloat() / max(1, images.size))
            }
            if (createdPages == 0) {
                throw IllegalStateException("No readable images could be decoded from the selected items.")
            }
            FileOutputStream(outputFile).use { out -> pdf.writeTo(out) }
            createdPages
        } finally {
            pdf.close()
        }
    }

    /**
     * Stages a content:// or file:// image URI into persistent app storage so that:
     * 1) Persistable/temporary URI grants cannot expire while the user edits or reorders images
     * 2) Large images and JPG/JPEG/PNG/WEBP formats are validated immediately with clear error messages.
     */
    suspend fun stageGalleryImageUri(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            if (uri.scheme == "file" && uri.path != null && File(uri.path!!).exists()) {
                val f = File(uri.path!!)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) return@runCatching uri
            }

            val stagingDir = File(context.filesDir, "gallery_staging").apply { mkdirs() }
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
            val ext = when {
                mime.contains("png", ignoreCase = true) -> "png"
                mime.contains("webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val stagedFile = File(stagingDir, "img_${System.nanoTime()}.$ext")

            var copied = false
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    stagedFile.outputStream().use { out -> input.copyTo(out) }
                    copied = stagedFile.length() > 0L
                }
            }
            if (!copied) {
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                            stagedFile.outputStream().use { out -> input.copyTo(out) }
                            copied = stagedFile.length() > 0L
                        }
                    }
                }
            }

            if (copied) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(stagedFile.absolutePath, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    return@runCatching Uri.fromFile(stagedFile)
                }
                stagedFile.delete()
            }

            // Fallback via ImageDecoder / sampled decoder for unusual providers or HEIC -> normalized JPEG
            val decoded = decodeSampledBitmapFromUri(uri, 2400)
                ?: throw IllegalArgumentException("Could not open or decode the selected image.")
            try {
                val exifCorrected = applyExifOrientationIfNeeded(uri, decoded)
                val jpgFallback = File(stagingDir, "img_${System.nanoTime()}.jpg")
                FileOutputStream(jpgFallback).use { out ->
                    exifCorrected.compress(Bitmap.CompressFormat.JPEG, 94, out)
                }
                if (exifCorrected !== decoded && !exifCorrected.isRecycled) exifCorrected.recycle()
                Uri.fromFile(jpgFallback)
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }
        }
    }

    /**
     * Stages the image URI and decodes a guaranteed GL-safe (<= 1280px ARGB_8888) EXIF-corrected preview Bitmap
     * so selected gallery images are always visible immediately in Compose Image().
     */
    suspend fun loadAndStageGalleryImage(uri: Uri, previewMaxDimPx: Int = 1280): Result<Pair<Uri, Bitmap>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeUri = stageGalleryImageUri(uri).getOrThrow()
                val rawPreview = decodeSampledBitmapFromUri(safeUri, previewMaxDimPx)
                    ?: throw IllegalStateException("Selected image could not be rendered.")
                val orientedPreview = applyExifOrientationIfNeeded(safeUri, rawPreview)
                safeUri to orientedPreview
            }
        }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        val norm = ((degrees % 360) + 360) % 360
        if (norm == 0) return bitmap
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    suspend fun scannedBitmapsToPdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val pdf = PdfDocument()
        var pages = 0
        try {
            bitmaps.forEachIndexed { idx, bmp ->
                val (pageW, pageH) = resolvePageDimensions(bmp.width, bmp.height, PdfPageSize.A4, PageOrientation.AUTO)
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val marginPt = 16f
                val availW = pageW - 2 * marginPt
                val availH = pageH - 2 * marginPt
                val scale = min(availW / bmp.width, availH / bmp.height)
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (pageW - drawW) / 2f
                val top = (pageH - drawH) / 2f
                canvas.drawBitmap(
                    bmp,
                    null,
                    RectF(left, top, left + drawW, top + drawH),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                pdf.finishPage(page)
                pages++
                onProgress((idx + 1).toFloat() / max(1, bitmaps.size))
            }
            FileOutputStream(outputFile).use { out -> pdf.writeTo(out) }
            pages
        } finally {
            pdf.close()
        }
    }

    suspend fun textToPdf(
        title: String,
        body: String,
        outputFile: File,
        fontSizePt: Float = 13f,
        pageSize: PdfPageSize = PdfPageSize.A4,
        theme: TextPdfTheme = TextPdfTheme.PAPER_WHITE,
        useMonoFont: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        val pageW = if (pageSize == PdfPageSize.LETTER) PdfPageSize.LETTER.widthPt else PdfPageSize.A4.widthPt
        val pageH = if (pageSize == PdfPageSize.LETTER) PdfPageSize.LETTER.heightPt else PdfPageSize.A4.heightPt
        val margin = 44
        val contentW = pageW - margin * 2
        val footerArea = 34
        val contentH = pageH - margin * 2 - footerArea

        val containsBengali = (title + body).any { it in '\u0980'..'\u09FF' }
        val typeface = when {
            containsBengali -> ResourcesCompat.getFont(context, R.font.noto_sans_bengali) ?: Typeface.DEFAULT
            useMonoFont -> ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
            else -> ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
        }

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            textSize = fontSizePt + 7f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textColor
            textSize = fontSizePt
            this.typeface = typeface
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            strokeWidth = 2f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, Color.red(theme.textColor), Color.green(theme.textColor), Color.blue(theme.textColor))
            textSize = 9.5f
            this.typeface = typeface
        }

        val cleanTitle = title.trim()
        val cleanBody = body.ifBlank { " " }

        val fullBodyLayout = StaticLayout.Builder.obtain(cleanBody, 0, cleanBody.length, bodyPaint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.25f)
            .build()

        val titleLayout = if (cleanTitle.isNotEmpty()) {
            StaticLayout.Builder.obtain(cleanTitle, 0, cleanTitle.length, titlePaint, contentW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()
        } else null

        val pdf = PdfDocument()
        try {
            var lineIdx = 0
            var pageNum = 0
            val totalLines = fullBodyLayout.lineCount

            do {
                pageNum++
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create())
                val canvas = page.canvas
                canvas.drawColor(theme.bgColor)

                var cursorY = margin.toFloat()
                if (pageNum == 1 && titleLayout != null) {
                    canvas.save()
                    canvas.translate(margin.toFloat(), cursorY)
                    titleLayout.draw(canvas)
                    canvas.restore()
                    cursorY += titleLayout.height + 10f
                    canvas.drawLine(margin.toFloat(), cursorY, (pageW - margin).toFloat(), cursorY, accentPaint)
                    cursorY += 16f
                }

                val maxBottom = (margin + contentH).toFloat()
                val startLine = lineIdx
                while (lineIdx < totalLines) {
                    val lineHeight = fullBodyLayout.getLineBottom(lineIdx) - fullBodyLayout.getLineTop(lineIdx)
                    if (cursorY + lineHeight > maxBottom && lineIdx > startLine) {
                        break
                    }
                    val lineStart = fullBodyLayout.getLineStart(lineIdx)
                    val lineEnd = fullBodyLayout.getLineEnd(lineIdx)
                    val lineText = cleanBody.substring(lineStart, lineEnd).trimEnd('\n')
                    val baseline = cursorY - fullBodyLayout.getLineAscent(lineIdx)
                    canvas.drawText(lineText, margin.toFloat(), baseline, bodyPaint)
                    cursorY += lineHeight
                    lineIdx++
                }

                val footerText = "PDF PLUS  •  Page $pageNum"
                canvas.drawText(footerText, margin.toFloat(), (pageH - 20).toFloat(), footerPaint)
                pdf.finishPage(page)
            } while (lineIdx < totalLines)

            FileOutputStream(outputFile).use { out -> pdf.writeTo(out) }
            pageNum
        } finally {
            pdf.close()
        }
    }

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        if (inputFiles.size < 2) throw IllegalArgumentException("Select at least 2 PDF files to merge.")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        inputFiles.forEachIndexed { idx, file ->
            merger.addSource(file)
            onProgress(0.3f * (idx + 1) / inputFiles.size)
        }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        onProgress(1f)
        getPageCount(outputFile)
    }

    suspend fun splitPdfBySelection(
        inputFile: File,
        pageIndicesZeroBased: List<Int>,
        outputFile: File
    ): Int = withContext(Dispatchers.IO) {
        if (pageIndicesZeroBased.isEmpty()) throw IllegalArgumentException("Select at least one page.")
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
            PDDocument().use { dst ->
                pageIndicesZeroBased.sorted().distinct().forEach { index ->
                    if (index in 0 until src.numberOfPages) {
                        dst.importPage(src.getPage(index))
                    }
                }
                if (dst.numberOfPages == 0) throw IllegalArgumentException("No valid pages in selection.")
                dst.save(outputFile)
                dst.numberOfPages
            }
        }
    }

    suspend fun splitPdfEveryNPages(
        inputFile: File,
        chunkSize: Int,
        outputDir: File,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        require(chunkSize > 0) { "Chunk size must be positive." }
        val results = mutableListOf<File>()
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
            val total = src.numberOfPages
            var part = 1
            var i = 0
            while (i < total) {
                val end = min(i + chunkSize, total)
                val outFile = File(outputDir, "${baseName}_part$part.pdf")
                PDDocument().use { dst ->
                    for (p in i until end) {
                        dst.importPage(src.getPage(p))
                    }
                    dst.save(outFile)
                }
                results.add(outFile)
                i = end
                part++
            }
        }
        results
    }

    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel,
        onProgress: (Float) -> Unit = {}
    ): CompressionReport = withContext(Dispatchers.IO) {
        val originalSize = inputFile.length()
        val tempPdf = PdfDocument()
        var pageCount = 0
        try {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val scaleFactor = level.dpi / 72f
                            val renderW = max(1, (page.width * scaleFactor).roundToInt()).coerceAtMost(2200)
                            val renderH = max(1, (page.height * scaleFactor).roundToInt()).coerceAtMost(3100)
                            val rawBmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            Canvas(rawBmp).drawColor(Color.WHITE)
                            page.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val baos = ByteArrayOutputStream()
                            rawBmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                            rawBmp.recycle()
                            val compressedBytes = baos.toByteArray()
                            val decodedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            val outPage = tempPdf.startPage(
                                PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                            )
                            outPage.canvas.drawBitmap(
                                decodedBmp,
                                null,
                                Rect(0, 0, page.width, page.height),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            tempPdf.finishPage(outPage)
                            decodedBmp.recycle()
                        }
                        onProgress((i + 1).toFloat() / max(1, pageCount))
                    }
                }
            }
            FileOutputStream(outputFile).use { out -> tempPdf.writeTo(out) }
        } finally {
            tempPdf.close()
        }
        CompressionReport(
            outputFile = outputFile,
            originalBytes = originalSize,
            compressedBytes = outputFile.length(),
            pageCount = pageCount
        )
    }

    suspend fun editPdfPages(
        inputFile: File,
        pageSpecs: List<EditablePageSpec>,
        outputFile: File
    ): Int = withContext(Dispatchers.IO) {
        if (pageSpecs.isEmpty()) throw IllegalArgumentException("At least one page must remain.")
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { src ->
            PDDocument().use { dst ->
                pageSpecs.forEach { spec ->
                    if (spec.originalIndex in 0 until src.numberOfPages) {
                        val imported = dst.importPage(src.getPage(spec.originalIndex))
                        val normalized = ((imported.rotation + spec.rotationDegrees) % 360 + 360) % 360
                        imported.rotation = (normalized / 90) * 90
                    }
                }
                dst.save(outputFile)
                dst.numberOfPages
            }
        }
    }

    suspend fun pdfToImagesToGallery(
        inputFile: File,
        pageIndices: List<Int>,
        isPng: Boolean,
        dpi: Int = 150,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()
        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndices = pageIndices.filter { it in 0 until renderer.pageCount }
                validIndices.forEachIndexed { idx, pageIdx ->
                    renderer.openPage(pageIdx).use { page ->
                        val scale = dpi / 72f
                        val w = max(1, (page.width * scale).roundToInt()).coerceAtMost(2400)
                        val h = max(1, (page.height * scale).roundToInt()).coerceAtMost(3400)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val ext = if (isPng) "png" else "jpg"
                        val mime = if (isPng) "image/png" else "image/jpeg"
                        val fileName = "${baseName}_page_${pageIdx + 1}.$ext"
                        val savedUri = saveBitmapToGallery(bmp, fileName, mime, isPng)
                        bmp.recycle()
                        if (savedUri != null) uris.add(savedUri)
                    }
                    onProgress((idx + 1).toFloat() / max(1, validIndices.size))
                }
            }
        }
        uris
    }

    suspend fun extractWordOrTextContent(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        val lower = fileName.lowercase()
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (lower.endsWith(".docx")) {
                parseDocxXml(input.readBytes())
            } else if (lower.endsWith(".doc")) {
                throw IllegalArgumentException("Legacy binary .doc files are not supported. Please convert to .docx or .txt.")
            } else {
                input.bufferedReader().readText()
            }
        } ?: throw IllegalStateException("Unable to read selected file.")
    }

    suspend fun addSignatureToPdf(
        inputFile: File,
        outputFile: File,
        signatureBitmap: Bitmap,
        targetPageZeroBased: Int,
        normalizedX: Float,
        normalizedY: Float,
        normalizedWidth: Float
    ): Int = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val pageIdx = targetPageZeroBased.coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(pageIdx)
            val mediaBox = page.mediaBox
            val pageW = mediaBox.width
            val pageH = mediaBox.height

            val pdImage = LosslessFactory.createFromImage(doc, signatureBitmap)
            val aspect = signatureBitmap.height.toFloat() / max(1, signatureBitmap.width)
            val drawW = pageW * normalizedWidth.coerceIn(0.12f, 0.7f)
            val drawH = drawW * aspect
            val drawX = (pageW - drawW) * normalizedX.coerceIn(0f, 1f)
            val drawY = (pageH - drawH) * (1f - normalizedY.coerceIn(0f, 1f))

            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
            }
            doc.save(outputFile)
            doc.numberOfPages
        }
    }

    suspend fun addWatermarkToPdf(
        inputFile: File,
        outputFile: File,
        watermarkText: String,
        textColorInt: Int,
        opacity: Float,
        rotationDegrees: Float,
        fontSizeSp: Float,
        position: WatermarkPosition,
        targetPageIndices: List<Int>? = null
    ): Int = withContext(Dispatchers.IO) {
        val cleanText = watermarkText.trim()
        require(cleanText.isNotEmpty()) { "Watermark text cannot be empty." }

        val containsBengali = cleanText.any { it in '\u0980'..'\u09FF' }
        val typeface = if (containsBengali) {
            ResourcesCompat.getFont(context, R.font.noto_sans_bengali) ?: Typeface.DEFAULT_BOLD
        } else {
            ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.DEFAULT_BOLD
        }

        val stampBitmap = renderWatermarkBitmap(
            text = cleanText,
            colorInt = textColorInt,
            fontSize = fontSizeSp * 2.2f,
            rotationDegrees = rotationDegrees,
            typeface = typeface,
            tiled = (position == WatermarkPosition.TILED)
        )

        try {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val pdImage = LosslessFactory.createFromImage(doc, stampBitmap)
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = opacity.coerceIn(0.08f, 0.95f)
                }
                val pagesToStamp = targetPageIndices ?: (0 until doc.numberOfPages).toList()

                pagesToStamp.forEach { idx ->
                    if (idx in 0 until doc.numberOfPages) {
                        val page: PDPage = doc.getPage(idx)
                        val box = page.mediaBox
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.setGraphicsStateParameters(gs)
                            when (position) {
                                WatermarkPosition.TILED -> {
                                    cs.drawImage(pdImage, 0f, 0f, box.width, box.height)
                                }
                                WatermarkPosition.DIAGONAL_CENTER -> {
                                    val w = box.width * 0.78f
                                    val h = w * (stampBitmap.height.toFloat() / max(1, stampBitmap.width))
                                    cs.drawImage(pdImage, (box.width - w) / 2f, (box.height - h) / 2f, w, h)
                                }
                                WatermarkPosition.FOOTER -> {
                                    val w = box.width * 0.5f
                                    val h = w * (stampBitmap.height.toFloat() / max(1, stampBitmap.width))
                                    cs.drawImage(pdImage, (box.width - w) / 2f, 24f, w, h)
                                }
                            }
                        }
                    }
                }
                doc.save(outputFile)
                doc.numberOfPages
            }
        } finally {
            if (!stampBitmap.isRecycled) stampBitmap.recycle()
        }
    }

    fun applyScanFilter(source: Bitmap, filter: ScanFilter, rotationDegrees: Int): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }
        if (filter == ScanFilter.ORIGINAL) return rotated

        val out = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = when (filter) {
            ScanFilter.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            ScanFilter.MAGIC -> ColorMatrix(
                floatArrayOf(
                    1.35f, 0f, 0f, 0f, -24f,
                    0f, 1.35f, 0f, 0f, -24f,
                    0f, 0f, 1.35f, 0f, -24f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ScanFilter.BW_DOC -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix(
                    floatArrayOf(
                        2.2f, 0f, 0f, 0f, -135f,
                        0f, 2.2f, 0f, 0f, -135f,
                        0f, 0f, 2.2f, 0f, -135f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                gray.postConcat(contrast)
                gray
            }
            ScanFilter.ORIGINAL -> ColorMatrix()
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        if (rotated !== source) rotated.recycle()
        return out
    }

    fun decodeSampledBitmapFromUri(uri: Uri, maxDimPx: Int = 1800): Bitmap? {
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.exists()) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                var inSample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
                repeat(3) {
                    val decoded = try {
                        BitmapFactory.decodeFile(
                            f.absolutePath,
                            BitmapFactory.Options().apply {
                                inSampleSize = inSample
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                        )
                    } catch (_: OutOfMemoryError) {
                        inSample *= 2
                        null
                    }
                    if (decoded != null) return decoded
                }
            }
        }

        val resolverResult = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStreamOrFd(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var inSample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimPx)
            repeat(3) {
                val decoded = try {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = inSample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    openStreamOrFd(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                } catch (_: OutOfMemoryError) {
                    inSample *= 2
                    null
                }
                if (decoded != null) return@runCatching decoded
            }
            null
        }.getOrNull()
        if (resolverResult != null) return resolverResult

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    if (w > maxDimPx || h > maxDimPx) {
                        val scale = min(maxDimPx.toFloat() / max(1, w), maxDimPx.toFloat() / max(1, h))
                        decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
                    }
                }
            }.getOrNull()
        }
        return null
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDimPx: Int): Int {
        var inSample = 1
        if (width <= 0 || height <= 0) return 1
        while (width / inSample > maxDimPx || height / inSample > maxDimPx) {
            inSample *= 2
        }
        return inSample
    }

    private fun openStreamOrFd(uri: Uri): java.io.InputStream? {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.let { return it }
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }.getOrNull()
    }

    private fun applyExifOrientationIfNeeded(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            if (uri.scheme == "file" && uri.path != null) {
                ExifInterface(uri.path!!).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } else {
                openStreamOrFd(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun resolvePageDimensions(
        imgW: Int,
        imgH: Int,
        pageSize: PdfPageSize,
        orientation: PageOrientation
    ): Pair<Int, Int> {
        if (pageSize == PdfPageSize.FIT_IMAGE) {
            val maxPt = 1200f
            val scale = min(1f, maxPt / max(imgW, imgH))
            return max(100, (imgW * scale).roundToInt()) to max(100, (imgH * scale).roundToInt())
        }
        val baseW = pageSize.widthPt
        val baseH = pageSize.heightPt
        val isLandscape = when (orientation) {
            PageOrientation.LANDSCAPE -> true
            PageOrientation.PORTRAIT -> false
            PageOrientation.AUTO -> imgW > imgH
        }
        return if (isLandscape) baseH to baseW else baseW to baseH
    }

    private fun applyInvertFilter(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
    }

    private fun renderWatermarkBitmap(
        text: String,
        colorInt: Int,
        fontSize: Float,
        rotationDegrees: Float,
        typeface: Typeface,
        tiled: Boolean
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = fontSize
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        if (tiled) {
            val w = 900
            val h = 1200
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val stepX = 320f
            val stepY = 240f
            var y = 100f
            while (y < h) {
                var x = 120f
                while (x < w) {
                    canvas.save()
                    canvas.rotate(rotationDegrees, x, y)
                    canvas.drawText(text, x, y, paint)
                    canvas.restore()
                    x += stepX
                }
                y += stepY
            }
            return bmp
        } else {
            val w = 900
            val h = 650
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.save()
            canvas.rotate(rotationDegrees, w / 2f, h / 2f)
            val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, w / 2f, baseline, paint)
            canvas.restore()
            return bmp
        }
    }

    private fun saveBitmapToGallery(
        bitmap: Bitmap,
        fileName: String,
        mimeType: String,
        isPng: Boolean
    ): Uri? {
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (isPng) 100 else 92
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PDF PLUS")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(format, quality, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun parseDocxXml(bytes: ByteArray): String {
        val sb = StringBuilder()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(zis, "UTF-8")
                    var event = parser.eventType
                    var inText = false
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "w:t", "t" -> inText = true
                                    "w:tab", "tab" -> sb.append("    ")
                                    "w:br", "br" -> sb.append("\n")
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inText) sb.append(parser.text)
                            }
                            XmlPullParser.END_TAG -> {
                                when (parser.name) {
                                    "w:t", "t" -> inText = false
                                    "w:p", "p" -> sb.append("\n")
                                }
                            }
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        val result = sb.toString().trim()
        if (result.isEmpty()) {
            throw IllegalStateException("No readable text found inside .docx document.")
        }
        return result
    }
}
