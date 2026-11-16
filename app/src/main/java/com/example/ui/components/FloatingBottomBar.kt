package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.LocalAppStrings
import com.example.navigation.AppRoute
import com.example.ui.theme.LocalPdfPlusTokens

data class BottomNavTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun FloatingGlassBottomBar(
    currentRoute: AppRoute,
    onSelectRoute: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current

    val tabs = listOf(
        BottomNavTab(AppRoute.Home, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavTab(AppRoute.Tools, strings.navTools, Icons.Rounded.AutoAwesomeMosaic, Icons.Outlined.AutoAwesomeMosaic, "nav_tools"),
        BottomNavTab(AppRoute.MyFiles, strings.navFiles, Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_files"),
        BottomNavTab(AppRoute.Favorites, strings.navFavorites, Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder, "nav_favorites"),
        BottomNavTab(AppRoute.Settings, strings.navSettings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = tokens.primaryGlow.copy(alpha = 0.45f),
                    spotColor = tokens.tertiaryCyan.copy(alpha = 0.4f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            tokens.primaryGlow.copy(alpha = 0.45f),
                            tokens.tertiaryCyan.copy(alpha = 0.35f),
                            tokens.goldAccent.copy(alpha = 0.30f)
                        )
                    ),
                    shape = RoundedCornerShape(30.dp)
                ),
            color = if (tokens.isDark) Color(0xEA0E101D) else Color(0xF5FFFFFF),
            shape = RoundedCornerShape(30.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_scale"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) Color.White else tokens.textSecondary,
                        label = "tab_tint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(56.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.linearGradient(
                                            listOf(
                                                tokens.primaryGlow.copy(alpha = 0.9f),
                                                tokens.secondaryGlow.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(bounded = true),
                                onClick = { onSelectRoute(tab.route) }
                            )
                            .testTag(tab.testTag)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tab.label,
                            color = if (selected) Color.White else tokens.textSecondary,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
