package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LearnedTransactionRule
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*

/**
 * Interactive Dialog showing the Self-Learning System's adapted transaction rules:
 * - Automatically created from user edits (title, category, tags)
 * - Auto-ignored patterns learned from repeated user rejections
 * - Full user transparency and control (toggle, remove, clear)
 */
@Composable
fun LearnedRulesDialog(
    viewModel: FinMoneyViewModel,
    onDismiss: () -> Unit
) {
    val rules by viewModel.learnedRules.collectAsStateWithLifecycle()
    LearnedRulesDialog(
        rules = rules,
        onToggleAutoIgnored = { viewModel.toggleRuleAutoIgnored(it) },
        onDeleteRule = { viewModel.deleteLearnedRule(it) },
        onClearAll = { viewModel.clearAllLearnedRules() },
        onDismiss = onDismiss
    )
}

@Composable
fun LearnedRulesDialog(
    rules: List<LearnedTransactionRule>,
    onToggleAutoIgnored: (LearnedTransactionRule) -> Unit,
    onDeleteRule: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterTab by remember { mutableStateOf(0) } // 0: All, 1: Category/Tag Rules, 2: Auto-Ignored Noise
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val filteredRules = remember(rules, filterTab, searchQuery) {
        rules.filter { rule ->
            val matchesTab = when (filterTab) {
                1 -> !rule.isAutoIgnored
                2 -> rule.isAutoIgnored
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                rule.patternKey.contains(searchQuery, ignoreCase = true) ||
                rule.merchantDisplayName.contains(searchQuery, ignoreCase = true) ||
                rule.learnedCategory.contains(searchQuery, ignoreCase = true) ||
                rule.learnedTags.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = IndSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IndBlueLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = IndBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Self-Learning Rules",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Adapts automatically from your edits & actions",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = IndTextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = IndTextMuted)
                    }
                }

                // Summary Stats Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IndCardSecondary,
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${rules.count { !it.isAutoIgnored }}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = IndBlue)
                            Text("Learned Prefs", fontSize = 10.sp, color = IndTextSecondary)
                        }
                        VerticalDivider(modifier = Modifier.height(28.dp), thickness = 0.8.dp, color = IndBorderSubtle)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${rules.count { it.isAutoIgnored }}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = IndRed)
                            Text("Auto-Ignored", fontSize = 10.sp, color = IndTextSecondary)
                        }
                        VerticalDivider(modifier = Modifier.height(28.dp), thickness = 0.8.dp, color = IndBorderSubtle)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${rules.sumOf { it.editCount + it.approveCount }}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = IndGreenDark)
                            Text("Actions Learned", fontSize = 10.sp, color = IndTextSecondary)
                        }
                    }
                }

                // Filter Tabs (All / Learned Prefs / Auto-Ignored)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All (${rules.size})", "Customized (${rules.count { !it.isAutoIgnored }})", "Auto-Ignored (${rules.count { it.isAutoIgnored }})").forEachIndexed { idx, label ->
                        val isSelected = filterTab == idx
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) IndNavyHeader else IndSurface,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) IndNavyHeader else IndBorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { filterTab = idx }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else IndTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter pattern e.g. Swiggy, Netflix...", fontSize = 12.sp, color = IndTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IndTextMuted, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Rules List
                if (filteredRules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = IndTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (rules.isEmpty()) "No learned rules yet" else "No matching rules found",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndTextSecondary
                            )
                            Text(
                                text = if (rules.isEmpty()) "As you edit or reject transactions, FinMoney automatically learns your preferences." else "Try searching for a different merchant or pattern.",
                                fontSize = 11.sp,
                                color = IndTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRules, key = { it.id }) { rule ->
                            LearnedRuleCard(
                                rule = rule,
                                onToggleAutoIgnored = { onToggleAutoIgnored(rule) },
                                onDelete = { onDeleteRule(rule.id) }
                            )
                        }
                    }
                }

                // Footer Actions
                if (rules.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showClearConfirmation = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = IndRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset All Rules", color = IndRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Reset Self-Learning Rules?") },
            text = { Text("This will remove all automatically learned merchant categories, title preferences, and auto-ignore rules. Future transactions will start with default suggestions.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Reset", color = IndRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LearnedRuleCard(
    rule: LearnedTransactionRule,
    onToggleAutoIgnored: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (rule.isAutoIgnored) Color(0xFFFEF2F2) else IndSurface,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (rule.isAutoIgnored) Color(0xFFFECACA) else IndBorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.merchantDisplayName.ifBlank { rule.patternKey },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = if (rule.isAutoIgnored) IndRed else IndNavyHeader
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (rule.isAutoIgnored) Color(0xFFFEE2E2) else IndBlueLight
                    ) {
                        Text(
                            text = if (rule.isAutoIgnored) "AUTO-IGNORED" else "LEARNED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rule.isAutoIgnored) IndRed else IndBlueDark,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                if (rule.isAutoIgnored) {
                    Text(
                        text = "Rejected ${rule.rejectCount} time(s). Future alerts from this pattern will be suppressed automatically.",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Category: ${rule.learnedCategory.ifBlank { "General" }}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndTextPrimary
                        )
                        if (rule.learnedCustomCategoryName.isNotBlank()) {
                            Text("• ${rule.learnedCustomCategoryName}", fontSize = 11.sp, color = IndTextSecondary)
                        }
                    }
                    if (rule.learnedTags.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tags:", fontSize = 10.sp, color = IndTextSecondary)
                            rule.learnedTags.split(",").filter { it.isNotBlank() }.forEach { t ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = IndBlueLight
                                ) {
                                    Text(
                                        text = "#${t.trim().removePrefix("#")}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndBlueDark,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Learned from ${rule.editCount} edit(s) & ${rule.approveCount} approval(s)",
                        fontSize = 10.sp,
                        color = IndTextMuted
                    )
                }
            }

            // Actions: Toggle Ignored, Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (rule.isAutoIgnored) {
                    TextButton(onClick = onToggleAutoIgnored) {
                        Text("Unblock", color = IndBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = onToggleAutoIgnored, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = "Mute / Ignore Pattern",
                            tint = IndTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete Rule",
                        tint = IndRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
