package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.prefs.UserSettings
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.ui.components.GlassCard
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LocalPdfPlusTokens
import com.example.ui.theme.LuminousCyan

private enum class SettingsModal { PRIVACY, ABOUT, HELP }

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAccentThemeChange: (AccentTheme) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val context = LocalContext.current
    var openModal by remember { mutableStateOf<SettingsModal?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.textPrimary
            )
        }

        // Appearance (Dark Mode / Light Mode)
        item {
            SectionLabel(strings.appearanceSection)
            Spacer(Modifier.height(8.dp))
            GlassCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (settings.isDarkMode) Icons.Rounded.Brightness4 else Icons.Rounded.Brightness7,
                        contentDescription = null,
                        tint = ChampagneGold
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isDarkMode) strings.darkMode else strings.lightMode,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary
                        )
                    }
                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = onDarkModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = tokens.primaryGlow
                        ),
                        modifier = Modifier.testTag("settings_dark_mode_switch")
                    )
                }
            }
        }

        // Language (English / Bengali)
        item {
            SectionLabel(strings.languageSection)
            Spacer(Modifier.height(8.dp))
            GlassCard(contentPadding = PaddingValues(8.dp)) {
                LanguageOptionRow(
                    label = strings.englishLang,
                    selected = settings.language == AppLanguage.ENGLISH,
                    onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                    testTag = "settings_lang_en"
                )
                HorizontalDivider(color = tokens.glassBorder.copy(alpha = 0.3f))
                LanguageOptionRow(
                    label = strings.bengaliLang,
                    selected = settings.language == AppLanguage.BENGALI,
                    onClick = { onLanguageChange(AppLanguage.BENGALI) },
                    testTag = "settings_lang_bn"
                )
            }
        }

        // Accent Theme
        item {
            SectionLabel(strings.appThemeSection)
            Spacer(Modifier.height(8.dp))
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeChip(
                        label = strings.themeViolet,
                        color = ElectricViolet,
                        selected = settings.accentTheme == AccentTheme.VIOLET,
                        onClick = { onAccentThemeChange(AccentTheme.VIOLET) },
                        modifier = Modifier.weight(1f),
                        testTag = "theme_violet"
                    )
                    ThemeChip(
                        label = strings.themeCyan,
                        color = LuminousCyan,
                        selected = settings.accentTheme == AccentTheme.CYAN,
                        onClick = { onAccentThemeChange(AccentTheme.CYAN) },
                        modifier = Modifier.weight(1f),
                        testTag = "theme_cyan"
                    )
                    ThemeChip(
                        label = strings.themeGold,
                        color = ChampagneGold,
                        selected = settings.accentTheme == AccentTheme.GOLD,
                        onClick = { onAccentThemeChange(AccentTheme.GOLD) },
                        modifier = Modifier.weight(1f),
                        testTag = "theme_gold"
                    )
                }
            }
        }

        // Legal & Support
        item {
            SectionLabel(strings.aboutSupportSection)
            Spacer(Modifier.height(8.dp))
            GlassCard(contentPadding = PaddingValues(6.dp)) {
                SettingsNavRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = strings.privacyPolicy,
                    onClick = { openModal = SettingsModal.PRIVACY },
                    testTag = "settings_privacy"
                )
                HorizontalDivider(color = tokens.glassBorder.copy(alpha = 0.25f))
                SettingsNavRow(
                    icon = Icons.Rounded.Info,
                    title = strings.aboutApp,
                    onClick = { openModal = SettingsModal.ABOUT },
                    testTag = "settings_about"
                )
                HorizontalDivider(color = tokens.glassBorder.copy(alpha = 0.25f))
                SettingsNavRow(
                    icon = Icons.Rounded.Share,
                    title = strings.shareApp,
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out PDF PLUS (PDF Maker Plus) — All-in-One Android PDF Studio! https://play.google.com/store/apps/details?id=${context.packageName}"
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, strings.shareApp))
                    },
                    testTag = "settings_share_app"
                )
                HorizontalDivider(color = tokens.glassBorder.copy(alpha = 0.25f))
                SettingsNavRow(
                    icon = Icons.Rounded.StarRate,
                    title = strings.rateApp,
                    onClick = {
                        val marketIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                        )
                        runCatching { context.startActivity(marketIntent) }
                    },
                    testTag = "settings_rate_app"
                )
                HorizontalDivider(color = tokens.glassBorder.copy(alpha = 0.25f))
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Rounded.HelpOutline,
                    title = strings.helpSupport,
                    onClick = { openModal = SettingsModal.HELP },
                    testTag = "settings_help"
                )
            }
        }
    }

    openModal?.let { modal ->
        val (title, body) = when (modal) {
            SettingsModal.PRIVACY -> strings.privacyPolicy to strings.privacyPolicyBody
            SettingsModal.ABOUT -> strings.aboutApp to strings.aboutAppBody
            SettingsModal.HELP -> strings.helpSupport to strings.helpSupportBody
        }
        AlertDialog(
            onDismissRequest = { openModal = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { openModal = null }) {
                    Text(strings.done)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = ChampagneGold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tokens = LocalPdfPlusTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Language, contentDescription = null, tint = tokens.tertiaryCyan)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = tokens.primaryGlow)
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val tokens = LocalPdfPlusTokens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else tokens.glassSurfaceStrong)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) color else tokens.glassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, tokens.secondaryGlow)))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    val tokens = LocalPdfPlusTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tokens.primaryGlow)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tokens.textSecondary)
    }
}
