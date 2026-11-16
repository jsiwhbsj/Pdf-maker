package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.pdf.DocumentQuad
import com.example.data.pdf.DocumentScannerProcessor
import com.example.data.pdf.EditablePageSpec
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.ScanEnhanceMode
import com.example.data.pdf.ScanFilter
import com.example.data.pdf.WatermarkPosition
import com.example.localization.BengaliStrings
import com.example.localization.EnglishStrings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var engine: PdfEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        engine = PdfEngine(context)
    }

    @Test
    fun `app name matches PDF PLUS and bilingual workflow strings exist`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PDF PLUS", appName)
        assertEquals("Gallery to PDF", EnglishStrings.toolImageToPdf)
        assertEquals("Camera Scanner", EnglishStrings.toolPdfScanner)
        assertEquals("CREATE PDF", EnglishStrings.convertToPdf)
        assertTrue(BengaliStrings.toolImageToPdf.isNotBlank())
        assertTrue(BengaliStrings.toolPdfScanner.isNotBlank())
    }

    @Test
    fun `scanner edge detection perspective warp and auto enhance produce valid bitmaps`() {
        // Create synthetic photo: dark desk background with bright white document rectangle in center
        val photo = Bitmap.createBitmap(400, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(photo)
        canvas.drawColor(Color.rgb(30, 30, 36))
        val paperPaint = Paint().apply { color = Color.rgb(235, 235, 240) }
        canvas.drawRect(50f, 60f, 350f, 460f, paperPaint)
        val textPaint = Paint().apply { color = Color.BLACK; strokeWidth = 4f }
        canvas.drawLine(80f, 120f, 300f, 120f, textPaint)
        canvas.drawLine(80f, 160f, 280f, 160f, textPaint)

        val detectedQuad = DocumentScannerProcessor.detectDocumentQuad(photo)
        assertTrue(detectedQuad.isConvexAndValid())

        val warped = DocumentScannerProcessor.warpPerspectiveAndCrop(photo, detectedQuad, rotationDegrees = 90)
        assertTrue(warped.width >= 200 && warped.height >= 200)

        val enhancedColor = DocumentScannerProcessor.enhanceScannedDocument(warped, ScanEnhanceMode.COLOR)
        assertEquals(warped.width, enhancedColor.width)
        assertEquals(warped.height, enhancedColor.height)

        val enhancedGray = DocumentScannerProcessor.enhanceScannedDocument(warped, ScanEnhanceMode.GRAYSCALE)
        assertEquals(warped.width, enhancedGray.width)
        assertEquals(warped.height, enhancedGray.height)

        val enhancedBw = DocumentScannerProcessor.enhanceScannedDocument(warped, ScanEnhanceMode.CRISP_BW)
        assertEquals(warped.width, enhancedBw.width)
        assertEquals(warped.height, enhancedBw.height)

        val customQuad = DocumentQuad(0.1f, 0.1f, 0.9f, 0.12f, 0.88f, 0.9f, 0.12f, 0.88f)
        assertTrue(customQuad.isConvexAndValid())
    }

    @Test
    fun `gallery URI staging decodes JPG PNG WEBP images cleanly`() = runTest {
        val dir = File(context.cacheDir, "gallery_unit_test").apply { mkdirs() }
        val formats = listOf(
            "photo.jpg" to Bitmap.CompressFormat.JPEG,
            "graphic.png" to Bitmap.CompressFormat.PNG,
            "shot.webp" to Bitmap.CompressFormat.WEBP_LOSSY
        )
        formats.forEach { (name, fmt) ->
            val file = File(dir, name)
            val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.rgb(90, 140, 210))
            file.outputStream().use { out -> bmp.compress(fmt, 90, out) }
            bmp.recycle()

            val staged = engine.loadAndStageGalleryImage(android.net.Uri.fromFile(file), previewMaxDimPx = 200)
            assertTrue("Expected loadAndStage success for $name", staged.isSuccess)
            val (safeUri, previewBmp) = staged.getOrThrow()
            assertTrue("Expected staged URI for $name", safeUri.toString().isNotBlank())
            assertTrue(
                "Expected valid scaled preview bitmap for $name",
                !previewBmp.isRecycled && previewBmp.width in 1..200 && previewBmp.height in 1..200
            )
            previewBmp.recycle()
        }
    }

    @Test
    fun `merge split edit signature and watermark create valid PDFs`() = runTest {
        val dir = File(context.cacheDir, "unit_pdf_test").apply { mkdirs() }
        val pdf1 = File(dir, "doc1.pdf")
        val pdf2 = File(dir, "doc2.pdf")
        val merged = File(dir, "merged.pdf")
        val split = File(dir, "split.pdf")
        val edited = File(dir, "edited.pdf")
        val signed = File(dir, "signed.pdf")
        val watermarked = File(dir, "watermarked.pdf")

        createSamplePdf(pdf1, "Page One", 1)
        createSamplePdf(pdf2, "Page Two", 2)

        assertEquals(1, engine.getPageCount(pdf1))
        assertEquals(2, engine.getPageCount(pdf2))

        val mergedPages = engine.mergePdfs(listOf(pdf1, pdf2), merged)
        assertEquals(3, mergedPages)
        assertTrue(merged.length() > 0)

        val splitPages = engine.splitPdfBySelection(merged, listOf(0, 2), split)
        assertEquals(2, splitPages)

        val editedPages = engine.editPdfPages(
            merged,
            listOf(EditablePageSpec(1, 90), EditablePageSpec(0, 180)),
            edited
        )
        assertEquals(2, editedPages)

        val sigBmp = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        sigBmp.eraseColor(Color.BLACK)
        val signedPages = engine.addSignatureToPdf(merged, signed, sigBmp, 0, 0.8f, 0.85f, 0.3f)
        assertEquals(3, signedPages)
        assertTrue(signed.length() > 0)

        val wmPages = engine.addWatermarkToPdf(
            inputFile = merged,
            outputFile = watermarked,
            watermarkText = "PDF PLUS",
            textColorInt = Color.BLUE,
            opacity = 0.3f,
            rotationDegrees = -30f,
            fontSizeSp = 36f,
            position = WatermarkPosition.DIAGONAL_CENTER
        )
        assertEquals(3, wmPages)
        assertTrue(watermarked.length() > 0)

        val scanBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val filtered = engine.applyScanFilter(scanBmp, ScanFilter.BW_DOC, 90)
        assertEquals(100, filtered.width)
        assertEquals(100, filtered.height)
    }

    private fun createSamplePdf(file: File, label: String, pages: Int) {
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    cs.newLineAtOffset(72f, 720f)
                    cs.showText("$label #${i + 1}")
                    cs.endText()
                }
            }
            doc.save(file)
        }
    }
}
