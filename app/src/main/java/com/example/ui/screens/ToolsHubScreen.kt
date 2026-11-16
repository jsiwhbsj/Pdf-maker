package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localization.LocalAppStrings
import com.example.navigation.AppRoute
import com.example.ui.components.ToolCategory
import com.example.ui.components.buildPdfTools
import com.example.ui.theme.LocalPdfPlusTokens

@Composable
fun ToolsHubScreen(
    onNavigateTool: (AppRoute) -> Unit
) {
    val strings = LocalAppStrings.current
    val tokens = LocalPdfPlusTokens.current
    val tools = remember(strings) { buildPdfTools(strings) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    val filtered = remember(tools, search, selectedCategory) {
        tools.filter { tool ->
            val matchCat = selectedCategory == null || tool.category == selectedCategory
            val q = search.trim().lowercase()
            val matchQuery = q.isEmpty() || tool.title.lowercase().contains(q) || tool.description.lowercase().contains(q)
            matchCat && matchQuery
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cols = if (maxWidth >= 680.dp) 3 else 2

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("tools_hub_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = strings.allTools,
                        style = MaterialTheme.typography.headlineLarge,
                        color = tokens.textPrimary
                    )
                    Text(
                        text = strings.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(strings.searchPlaceholder, color = tokens.textSecondary) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.tertiaryCyan) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.glassSurface,
                        unfocusedContainerColor = tokens.glassSurface,
                        focusedBorderColor = tokens.primaryGlow,
                        unfocusedBorderColor = tokens.glassBorder,
                        focusedTextColor = tokens.textPrimary,
                        unfocusedTextColor = tokens.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val categories = listOf(
                    null to strings.filterAll,
                    ToolCategory.CREATE to "Create & Convert",
                    ToolCategory.ORGANIZE to "Organize & Optimize",
                    ToolCategory.ENHANCE to "Edit & Protect"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { (cat, label) ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primaryGlow,
                                selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                containerColor = tokens.glassSurface,
                                labelColor = tokens.textSecondary
                            )
                        )
                    }
                }
            }

            val rows = filtered.chunked(cols)
            items(rows.size) { idx ->
                val rowItems = rows[idx]
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
                    repeat(cols - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
