package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Tools : AppRoute
    @Serializable data object MyFiles : AppRoute
    @Serializable data object Favorites : AppRoute
    @Serializable data object Settings : AppRoute

    // Dedicated Tool Screens
    @Serializable data object ImageToPdf : AppRoute
    @Serializable data object TextToPdf : AppRoute
    @Serializable data object MergePdf : AppRoute
    @Serializable data object SplitPdf : AppRoute
    @Serializable data object CompressPdf : AppRoute
    @Serializable data object PdfScanner : AppRoute
    @Serializable data class PdfViewer(val documentId: Long = -1L) : AppRoute
    @Serializable data class PdfEditor(val documentId: Long = -1L) : AppRoute
    @Serializable data class PdfToImage(val documentId: Long = -1L) : AppRoute
    @Serializable data object WordToPdf : AppRoute
    @Serializable data class AddSignature(val documentId: Long = -1L) : AppRoute
    @Serializable data class AddWatermark(val documentId: Long = -1L) : AppRoute
}
