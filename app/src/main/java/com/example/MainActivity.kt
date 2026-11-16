package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.localization.AppLanguage
import com.example.localization.BengaliStrings
import com.example.localization.EnglishStrings
import com.example.localization.LocalAppStrings
import com.example.navigation.AppRoute
import com.example.ui.components.AnimatedAuroraBackground
import com.example.ui.components.FloatingGlassBottomBar
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.tools.AddSignatureScreen
import com.example.ui.screens.tools.AddWatermarkScreen
import com.example.ui.screens.tools.CompressPdfScreen
import com.example.ui.screens.tools.ImageToPdfScreen
import com.example.ui.screens.tools.MergePdfScreen
import com.example.ui.screens.tools.PdfEditorScreen
import com.example.ui.screens.tools.PdfScannerScreen
import com.example.ui.screens.tools.PdfToImageScreen
import com.example.ui.screens.tools.PdfViewerScreen
import com.example.ui.screens.tools.SplitPdfScreen
import com.example.ui.screens.tools.TextToPdfScreen
import com.example.ui.screens.tools.WordToPdfScreen
import com.example.ui.theme.PdfPlusTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PdfPlusApplication
        val incomingPdfUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.pdfRepository, app.settingsRepository)
            )
            PdfPlusApp(
                viewModel = viewModel,
                initialExternalPdfUri = incomingPdfUri
            )
        }
    }
}

@Composable
fun PdfPlusApp(
    viewModel: MainViewModel,
    initialExternalPdfUri: android.net.Uri? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val recentDocs by viewModel.recentDocuments.collectAsStateWithLifecycle()
    val favDocs by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val filteredDocs by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.fileFilter.collectAsStateWithLifecycle()
    val toastMsg by viewModel.toastMessage.collectAsStateWithLifecycle()

    val isBengali = settings.language == AppLanguage.BENGALI
    val strings = if (isBengali) BengaliStrings else EnglishStrings

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdf(uri) { imported ->
                navController.navigate(AppRoute.PdfViewer(imported.id))
            }
        }
    }

    LaunchedEffect(initialExternalPdfUri) {
        if (initialExternalPdfUri != null) {
            viewModel.importPdf(initialExternalPdfUri) { imported ->
                navController.navigate(AppRoute.PdfViewer(imported.id)) {
                    popUpTo(AppRoute.Splash) { inclusive = true }
                }
            }
        }
    }

    val currentMainTab: AppRoute? = when {
        currentRouteName.endsWith("Home") -> AppRoute.Home
        currentRouteName.endsWith("Tools") -> AppRoute.Tools
        currentRouteName.endsWith("MyFiles") -> AppRoute.MyFiles
        currentRouteName.endsWith("Favorites") -> AppRoute.Favorites
        currentRouteName.endsWith("Settings") -> AppRoute.Settings
        else -> null
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        PdfPlusTheme(
            darkTheme = settings.isDarkMode,
            accentTheme = settings.accentTheme,
            isBengali = isBengali
        ) {
            AnimatedAuroraBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = if (currentMainTab != null) 88.dp else 16.dp)
                        )
                    }
                ) { _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.Splash,
                            enterTransition = {
                                fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 12 }
                            },
                            exitTransition = {
                                fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 16 }
                            },
                            popEnterTransition = {
                                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 12 }
                            },
                            popExitTransition = {
                                fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 12 }
                            }
                        ) {
                            composable<AppRoute.Splash> {
                                SplashScreen(
                                    onSplashComplete = {
                                        navController.navigate(AppRoute.Home) {
                                            popUpTo(AppRoute.Splash) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<AppRoute.Home> {
                                HomeScreen(
                                    recentDocuments = recentDocs,
                                    totalDocCount = allDocs.size,
                                    currentLanguage = settings.language,
                                    onToggleLanguage = { viewModel.toggleLanguage() },
                                    onOpenSettings = {
                                        navController.navigate(AppRoute.Settings) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenFiles = {
                                        navController.navigate(AppRoute.MyFiles) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenDocument = { doc ->
                                        navController.navigate(AppRoute.PdfViewer(doc.id))
                                    },
                                    onImportPdfClick = {
                                        importLauncher.launch(arrayOf("application/pdf"))
                                    },
                                    onNavigateTool = { route ->
                                        navController.navigate(route)
                                    }
                                )
                            }

                            composable<AppRoute.Tools> {
                                ToolsHubScreen(
                                    onNavigateTool = { route -> navController.navigate(route) }
                                )
                            }

                            composable<AppRoute.MyFiles> {
                                FilesScreen(
                                    favoritesOnly = false,
                                    documents = filteredDocs,
                                    searchQuery = searchQuery,
                                    activeFilter = activeFilter,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::updateFileFilter,
                                    onImportPdf = { uri -> viewModel.importPdf(uri) },
                                    onOpenDocument = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onShareDocument = viewModel::shareDocument,
                                    onExportDocument = viewModel::exportDocument,
                                    onRenameDocument = viewModel::renameDocument,
                                    onDeleteDocument = viewModel::deleteDocument,
                                    onCreatePdfClick = { navController.navigate(AppRoute.ImageToPdf) }
                                )
                            }

                            composable<AppRoute.Favorites> {
                                FilesScreen(
                                    favoritesOnly = true,
                                    documents = favDocs,
                                    searchQuery = searchQuery,
                                    activeFilter = activeFilter,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::updateFileFilter,
                                    onImportPdf = { uri -> viewModel.importPdf(uri) },
                                    onOpenDocument = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onShareDocument = viewModel::shareDocument,
                                    onExportDocument = viewModel::exportDocument,
                                    onRenameDocument = viewModel::renameDocument,
                                    onDeleteDocument = viewModel::deleteDocument,
                                    onCreatePdfClick = { navController.navigate(AppRoute.ImageToPdf) }
                                )
                            }

                            composable<AppRoute.Settings> {
                                SettingsScreen(
                                    settings = settings,
                                    onDarkModeChange = viewModel::setDarkMode,
                                    onLanguageChange = viewModel::setLanguage,
                                    onAccentThemeChange = viewModel::setAccentTheme
                                )
                            }

                            composable<AppRoute.ImageToPdf> {
                                ImageToPdfScreen(
                                    repository = viewModel.repository,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.TextToPdf> {
                                TextToPdfScreen(
                                    repository = viewModel.repository,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.MergePdf> {
                                MergePdfScreen(
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.SplitPdf> {
                                SplitPdfScreen(
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.CompressPdf> {
                                CompressPdfScreen(
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.PdfScanner> {
                                PdfScannerScreen(
                                    repository = viewModel.repository,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.PdfViewer> { entry ->
                                val route = entry.toRoute<AppRoute.PdfViewer>()
                                PdfViewerScreen(
                                    initialDocumentId = route.documentId,
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenEditor = { id -> navController.navigate(AppRoute.PdfEditor(id)) }
                                )
                            }

                            composable<AppRoute.PdfEditor> { entry ->
                                val route = entry.toRoute<AppRoute.PdfEditor>()
                                PdfEditorScreen(
                                    initialDocumentId = route.documentId,
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.PdfToImage> { entry ->
                                val route = entry.toRoute<AppRoute.PdfToImage>()
                                PdfToImageScreen(
                                    initialDocumentId = route.documentId,
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AppRoute.WordToPdf> {
                                WordToPdfScreen(
                                    repository = viewModel.repository,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.AddSignature> { entry ->
                                val route = entry.toRoute<AppRoute.AddSignature>()
                                AddSignatureScreen(
                                    initialDocumentId = route.documentId,
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }

                            composable<AppRoute.AddWatermark> { entry ->
                                val route = entry.toRoute<AppRoute.AddWatermark>()
                                AddWatermarkScreen(
                                    initialDocumentId = route.documentId,
                                    repository = viewModel.repository,
                                    vaultDocuments = allDocs,
                                    onBack = { navController.popBackStack() },
                                    onOpenViewer = { doc -> navController.navigate(AppRoute.PdfViewer(doc.id)) }
                                )
                            }
                        }

                        if (currentMainTab != null) {
                            FloatingGlassBottomBar(
                                currentRoute = currentMainTab,
                                onSelectRoute = { target ->
                                    if (target != currentMainTab) {
                                        navController.navigate(target) {
                                            popUpTo(AppRoute.Home) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }
}
