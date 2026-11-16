package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.navigation.AppRoute
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowButton
import com.example.ui.components.GoldAccentButton
import com.example.ui.components.PdfToolItem
import com.example.ui.components.RecentDocMiniCard
import com.example.ui.components.buildPdfTools
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.LocalPdfPlusTokens

@Composable
fun HomeScreen(
    recentDocuments: List<PdfDocumentEntity>,
    totalDocCount: Int,
    currentLanguage: AppLanguage,
    onToggleLanguage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onImportPdfClick: () -> Unit,
    onNavigateTool: (AppRoute) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val allTools = remember(strings) { buildPdfTools(strings) }
    var searchText by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { revealed = true }

    val filteredTools = remember(allTools, searchText) {
        val q = searchText.trim().lowercase()
        if (q.isEmpty()) allTools
        else allTools.filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    val filteredRecent = remember(recentDocuments, searchText) {
        val q = searchText.trim().lowercase()
        if (q.isEmpty()) recentDocuments
        else recentDocuments.filter { it.displayName.lowercase().contains(q) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 680.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("home_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Premium Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pdf_plus_logo),
                        contentDescription = strings.appName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, tokens.glassBorder, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.welcomeBack,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampagneGold
                        )
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Language toggle pill
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, tokens.glassBorder, RoundedCornerShape(16.dp))
                            .clickable(onClick = onToggleLanguage)
                            .testTag("home_language_toggle"),
                        color = tokens.glassSurface,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Language,
                                contentDescription = "Language",
                                tint = tokens.tertiaryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = currentLanguage.displayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Profile / Vault badge
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, tokens.glassBorder, CircleShape),
                        shape = CircleShape,
                        color = tokens.glassSurface
                    ) {
                        IconButton(
                            onClick = onOpenFiles,
                            modifier = Modifier.testTag("home_profile_button")
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = strings.allDocuments,
                                tint = tokens.primaryGlow
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Settings icon
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, tokens.glassBorder, CircleShape),
                        shape = CircleShape,
                        color = tokens.glassSurface
                    ) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("home_settings_button")
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = strings.settingsTitle,
                                tint = tokens.textPrimary
                            )
                        }
                    }
                }
            }

            // 2. Large Gradient Welcome Hero Card
            item {
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(tween(320)) + slideInVertically(initialOffsetY = { 24 })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 24.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = tokens.primaryGlow,
                                spotColor = tokens.secondaryGlow
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .border(
                                1.dp,
                                Brush.linearGradient(
                                    listOf(
                                        tokens.primaryGlow.copy(alpha = 0.65f),
                                        tokens.tertiaryCyan.copy(alpha = 0.4f),
                                        ChampagneGold.copy(alpha = 0.45f)
                                    )
                                ),
                                RoundedCornerShape(28.dp)
                            )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_aurora),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x99080912),
                                            Color(0xCC0A0C18)
                                        )
                                    )
                                )
                        )

                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ChampagneGold.copy(alpha = 0.2f),
                                    modifier = Modifier.border(
                                        1.dp,
                                        ChampagneGold.copy(alpha = 0.5f),
                                        RoundedCornerShape(999.dp)
                                    )
                                ) {
                                    Text(
                                        text = "$totalDocCount PDFs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChampagneGold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = onImportPdfClick)
                                        .testTag("home_import_pdf_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.UploadFile,
                                            contentDescription = strings.importPdf,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = strings.importPdf,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = strings.heroTitle,
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = strings.heroSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD0D4EE)
                            )

                            Spacer(Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlowButton(
                                    text = strings.quickCreatePdf,
                                    icon = Icons.Rounded.Add,
                                    onClick = { onNavigateTool(AppRoute.ImageToPdf) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "hero_quick_create_btn"
                                )
                                GoldAccentButton(
                                    text = strings.quickScan,
                                    icon = Icons.Rounded.DocumentScanner,
                                    onClick = { onNavigateTool(AppRoute.PdfScanner) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "hero_quick_scan_btn"
                                )
                            }
                        }
                    }
                }
            }

            // 3. Search Bar
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = {
                        Text(
                            text = strings.searchPlaceholder,
                            color = tokens.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.tertiaryCyan)
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = tokens.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
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
                        .testTag("home_search_input")
                )
            }

            // 4. Recent Documents Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.recentDocuments,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary
                    )
                    TextButton(
                        onClick = onOpenFiles,
                        modifier = Modifier.testTag("home_see_all_docs")
                    ) {
                        Text(strings.viewAll, color = tokens.tertiaryCyan)
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (filteredRecent.isEmpty()) {
                    EmptyStatePanel(
                        title = strings.noDocsYetTitle,
                        subtitle = strings.noDocsYetSubtitle,
                        ctaLabel = strings.createFirstPdf,
                        onCtaClick = { onNavigateTool(AppRoute.TextToPdf) },
                        secondaryCtaLabel = strings.importPdf,
                        onSecondaryClick = onImportPdfClick
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredRecent, key = { it.id }) { doc ->
                            RecentDocMiniCard(
                                doc = doc,
                                onClick = { onOpenDocument(doc) }
                            )
                        }
                    }
                }
            }

            // 5. PDF Tools Grid Header
            item {
                Text(
                    text = strings.pdfTools,
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary
                )
            }

            // 6. Responsive 2-column / 3-column PDF Tools Grid
            val rows = filteredTools.chunked(columns)
            items(rows.size) { rowIndex ->
                val rowItems = rows[rowIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { tool ->
                        PdfToolGridCard(
                            tool = tool,
                            onClick = { onNavigateTool(tool.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun PdfToolGridCard(
    tool: PdfToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPdfPlusTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tool_press"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (tokens.isDark) 14.dp else 6.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = tool.gradientStart.copy(alpha = 0.35f),
                spotColor = tool.gradientEnd.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        tool.gradientStart.copy(alpha = 0.48f),
                        tool.gradientEnd.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .testTag("tool_card_${tool.id}"),
        color = tokens.glassSurface,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(10.dp, RoundedCornerShape(15.dp), spotColor = tool.gradientStart)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.linearGradient(listOf(tool.gradientStart, tool.gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                color = tokens.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
