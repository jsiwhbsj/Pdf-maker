package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.localization.AppStrings
import com.example.navigation.AppRoute
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseMagenta
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.WarmGold

enum class ToolCategory { CREATE, ORGANIZE, ENHANCE }

data class PdfToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientStart: Color,
    val gradientEnd: Color,
    val category: ToolCategory,
    val route: AppRoute
)

fun buildPdfTools(strings: AppStrings): List<PdfToolItem> = listOf(
    PdfToolItem(
        id = "image_to_pdf",
        title = strings.toolImageToPdf,
        description = strings.toolImageToPdfDesc,
        icon = Icons.Rounded.Image,
        gradientStart = ElectricViolet,
        gradientEnd = RoyalBlue,
        category = ToolCategory.CREATE,
        route = AppRoute.ImageToPdf
    ),
    PdfToolItem(
        id = "text_to_pdf",
        title = strings.toolTextToPdf,
        description = strings.toolTextToPdfDesc,
        icon = Icons.Rounded.TextFields,
        gradientStart = RoyalBlue,
        gradientEnd = LuminousCyan,
        category = ToolCategory.CREATE,
        route = AppRoute.TextToPdf
    ),
    PdfToolItem(
        id = "merge_pdf",
        title = strings.toolMergePdf,
        description = strings.toolMergePdfDesc,
        icon = Icons.Rounded.MergeType,
        gradientStart = DeepPurple,
        gradientEnd = RoseMagenta,
        category = ToolCategory.ORGANIZE,
        route = AppRoute.MergePdf
    ),
    PdfToolItem(
        id = "split_pdf",
        title = strings.toolSplitPdf,
        description = strings.toolSplitPdfDesc,
        icon = Icons.Rounded.CallSplit,
        gradientStart = LuminousCyan,
        gradientEnd = EmeraldSuccess,
        category = ToolCategory.ORGANIZE,
        route = AppRoute.SplitPdf
    ),
    PdfToolItem(
        id = "compress_pdf",
        title = strings.toolCompressPdf,
        description = strings.toolCompressPdfDesc,
        icon = Icons.Rounded.Compress,
        gradientStart = ChampagneGold,
        gradientEnd = WarmGold,
        category = ToolCategory.ORGANIZE,
        route = AppRoute.CompressPdf
    ),
    PdfToolItem(
        id = "pdf_scanner",
        title = strings.toolPdfScanner,
        description = strings.toolPdfScannerDesc,
        icon = Icons.Rounded.DocumentScanner,
        gradientStart = ElectricViolet,
        gradientEnd = LuminousCyan,
        category = ToolCategory.CREATE,
        route = AppRoute.PdfScanner
    ),
    PdfToolItem(
        id = "pdf_viewer",
        title = strings.toolPdfViewer,
        description = strings.toolPdfViewerDesc,
        icon = Icons.Rounded.PictureAsPdf,
        gradientStart = RoyalBlue,
        gradientEnd = ElectricViolet,
        category = ToolCategory.ENHANCE,
        route = AppRoute.PdfViewer()
    ),
    PdfToolItem(
        id = "pdf_editor",
        title = strings.toolPdfEditor,
        description = strings.toolPdfEditorDesc,
        icon = Icons.Rounded.EditNote,
        gradientStart = RoseMagenta,
        gradientEnd = ElectricViolet,
        category = ToolCategory.ENHANCE,
        route = AppRoute.PdfEditor()
    ),
    PdfToolItem(
        id = "pdf_to_image",
        title = strings.toolPdfToImage,
        description = strings.toolPdfToImageDesc,
        icon = Icons.Rounded.PhotoLibrary,
        gradientStart = EmeraldSuccess,
        gradientEnd = RoyalBlue,
        category = ToolCategory.CREATE,
        route = AppRoute.PdfToImage()
    ),
    PdfToolItem(
        id = "word_to_pdf",
        title = strings.toolWordToPdf,
        description = strings.toolWordToPdfDesc,
        icon = Icons.Rounded.Description,
        gradientStart = RoyalBlue,
        gradientEnd = ChampagneGold,
        category = ToolCategory.CREATE,
        route = AppRoute.WordToPdf
    ),
    PdfToolItem(
        id = "add_signature",
        title = strings.toolAddSignature,
        description = strings.toolAddSignatureDesc,
        icon = Icons.Rounded.Draw,
        gradientStart = ChampagneGold,
        gradientEnd = ElectricViolet,
        category = ToolCategory.ENHANCE,
        route = AppRoute.AddSignature()
    ),
    PdfToolItem(
        id = "add_watermark",
        title = strings.toolAddWatermark,
        description = strings.toolAddWatermarkDesc,
        icon = Icons.Rounded.BrandingWatermark,
        gradientStart = LuminousCyan,
        gradientEnd = DeepPurple,
        category = ToolCategory.ENHANCE,
        route = AppRoute.AddWatermark()
    )
)
