package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.data.repository.PdfRepository
import com.example.localization.LocalAppStrings
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.WarmGold

@Composable
fun AnimatedAuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalPdfPlusTokens.current
    val transition = rememberInfiniteTransition(label = "aurora_bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.canvasBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alphaScale = if (tokens.isDark) 1f else 0.38f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.primaryGlow.copy(alpha = 0.24f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.18f + 0.12f * drift), h * (0.10f + 0.05f * drift)),
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = Offset(w * (0.18f + 0.12f * drift), h * (0.10f + 0.05f * drift))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.secondaryGlow.copy(alpha = 0.20f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.85f - 0.10f * drift), h * (0.34f - 0.06f * drift)),
                    radius = w * 0.72f
                ),
                radius = w * 0.72f,
                center = Offset(w * (0.85f - 0.10f * drift), h * (0.34f - 0.06f * drift))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.tertiaryCyan.copy(alpha = 0.12f * alphaScale),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.35f + 0.2f * drift), h * 0.85f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * (0.35f + 0.2f * drift), h * 0.85f)
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderBrush: Brush? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalPdfPlusTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glass_press"
    )

    val defaultBorder = borderBrush ?: Brush.linearGradient(
        colors = listOf(
            tokens.glassBorder.copy(alpha = if (tokens.isDark) 0.55f else 0.35f),
            tokens.tertiaryCyan.copy(alpha = if (tokens.isDark) 0.22f else 0.12f),
            tokens.goldAccent.copy(alpha = if (tokens.isDark) 0.18f else 0.10f)
        )
    )

    val clickModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick)
    } else {
        Modifier.clip(shape)
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (tokens.isDark) 14.dp else 6.dp,
                shape = shape,
                ambientColor = tokens.primaryGlow.copy(alpha = 0.35f),
                spotColor = tokens.secondaryGlow.copy(alpha = 0.35f)
            )
            .border(1.dp, defaultBorder, shape)
            .then(clickModifier),
        color = tokens.glassSurface,
        shape = shape
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String = "glow_button"
) {
    val tokens = LocalPdfPlusTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btn_scale"
    )
    val gradient = Brush.horizontalGradient(
        colors = if (enabled) {
            listOf(tokens.primaryGlow, tokens.secondaryGlow, tokens.tertiaryCyan)
        } else {
            listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.3f))
        }
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .minimumInteractiveComponentSize()
            .height(54.dp)
            .shadow(
                elevation = if (enabled) 16.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = tokens.primaryGlow,
                spotColor = tokens.secondaryGlow
            )
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.28f else 0.1f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick
            )
            .testTag(testTag)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.4.dp
                )
                Spacer(Modifier.width(10.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GoldAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String = "gold_button"
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = ChampagneGold)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(ChampagneGold, WarmGold)))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ObsidianBlack, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = ObsidianBlack,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ToolHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val tokens = LocalPdfPlusTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, tokens.glassBorder, CircleShape),
            shape = CircleShape,
            color = tokens.glassSurface
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("header_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = tokens.textPrimary
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(listOf(tokens.primaryGlow, tokens.secondaryGlow))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    subtitle: String,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    secondaryCtaLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPdfPlusTokens.current
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_docs),
                contentDescription = title,
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, tokens.glassBorder, RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center
            )
            if (ctaLabel != null && onCtaClick != null) {
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowButton(
                        text = ctaLabel,
                        onClick = onCtaClick,
                        modifier = Modifier.weight(1f),
                        testTag = "empty_primary_cta"
                    )
                    if (secondaryCtaLabel != null && onSecondaryClick != null) {
                        OutlinedButton(
                            onClick = onSecondaryClick,
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("empty_secondary_cta"),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, tokens.glassBorder)
                        ) {
                            Text(
                                text = secondaryCtaLabel,
                                color = tokens.textPrimary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerBar(
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val tokens = LocalPdfPlusTokens.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to tokens.glassSurfaceStrong,
                        (offset.coerceIn(0f, 1f)) to tokens.primaryGlow.copy(alpha = 0.8f),
                        1f to tokens.tertiaryCyan.copy(alpha = 0.45f)
                    )
                )
            )
    )
}

@Composable
fun PdfProcessingBanner(
    label: String,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPdfPlusTokens.current
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = LuminousCyan,
                strokeWidth = 2.5.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = tokens.primaryGlow,
                trackColor = tokens.glassSurfaceStrong
            )
        } else {
            ShimmerBar(height = 6.dp)
        }
    }
}

@Composable
fun SuccessResultCard(
    document: PdfDocumentEntity,
    extraNote: String? = null,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.94f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        GlassCard(
            modifier = modifier.fillMaxWidth(),
            borderBrush = Brush.linearGradient(listOf(EmeraldSuccess, LuminousCyan, ChampagneGold)),
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EmeraldSuccess.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = strings.successTitle,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.successTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${document.displayName}.pdf",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${document.pageCount} ${strings.pagesLabel} • ${PdfRepository.formatBytes(document.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary
                    )
                    if (!extraNote.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = extraNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampagneGold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlowButton(
                    text = strings.openInViewer,
                    icon = Icons.Rounded.Visibility,
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    testTag = "result_preview_button"
                )
                IconActionPill(
                    icon = Icons.Rounded.Download,
                    contentDescription = strings.exportDownload,
                    onClick = onDownload,
                    testTag = "result_download_button"
                )
                IconActionPill(
                    icon = Icons.Rounded.Share,
                    contentDescription = strings.share,
                    onClick = onShare,
                    testTag = "result_share_button"
                )
            }
        }
    }
}

@Composable
fun IconActionPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color? = null,
    testTag: String = "action_pill"
) {
    val tokens = LocalPdfPlusTokens.current
    Surface(
        modifier = Modifier
            .size(54.dp)
            .border(1.dp, tokens.glassBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = tokens.glassSurfaceStrong
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: tokens.textPrimary
            )
        }
    }
}
