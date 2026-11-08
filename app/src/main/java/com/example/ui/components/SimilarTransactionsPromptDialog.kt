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
import com.example.learning.SimilarTransactionsPrompt
import com.example.ui.theme.*

/**
 * Axio-style Dialog:
 * When a user edits a transaction (e.g., changes category, title, or tags),
 * detects all similar transactions from the same merchant/pattern/SMS and
 * prompts the user to retroactively update them in one tap.
 */
@Composable
fun SimilarTransactionsPromptDialog(
    prompt: SimilarTransactionsPrompt,
    currencySymbol: String = "₹",
    onConfirmUpdate: (selectedCandidateIds: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(prompt) {
        mutableStateOf(prompt.candidates.map { it.id }.toSet())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("similar_transactions_prompt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update similar transactions?",
                            style = AppTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Found ${prompt.candidates.size} similar transaction(s) for '${prompt.merchantName}'",
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Proposed Change Summary Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AppTheme.colors.primaryLight.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "APPLY TO SELECTED:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Category: ${prompt.newCategory}",
                                style = AppTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        if (prompt.newTitle.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Title: ${prompt.newTitle}",
                                    style = AppTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }

                        if (prompt.newTags.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Tags: ${prompt.newTags}",
                                    style = AppTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Selection Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Selected (${selectedIds.size}/${prompt.candidates.size})",
                        style = AppTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )

                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == prompt.candidates.size) {
                                emptySet()
                            } else {
                                prompt.candidates.map { it.id }.toSet()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (selectedIds.size == prompt.candidates.size) "Deselect all" else "Select all",
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Candidate List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(prompt.candidates, key = { it.id }) { candidate ->
                        val isChecked = selectedIds.contains(candidate.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) AppTheme.colors.surface else AppTheme.colors.background,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChecked) AppTheme.colors.primary.copy(alpha = 0.4f) else AppTheme.colors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - candidate.id
                                    } else {
                                        selectedIds + candidate.id
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + candidate.id
                                        } else {
                                            selectedIds - candidate.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AppTheme.colors.primary),
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = candidate.title,
                                            style = AppTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$currencySymbol${String.format("%.2f", candidate.amount)}",
                                            style = AppTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AppTheme.colors.textPrimary
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = candidate.dateFormatted,
                                            style = AppTheme.typography.caption,
                                            color = AppTheme.colors.textMuted
                                        )
                                        Text(
                                            text = "Current: ${candidate.currentCategory}",
                                            style = AppTheme.typography.caption,
                                            color = AppTheme.colors.secondary.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("dismiss_similar_prompt_button")
                    ) {
                        Text(
                            text = "Only this one",
                            style = AppTheme.typography.labelMedium,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    Button(
                        onClick = { onConfirmUpdate(selectedIds) },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 44.dp)
                            .testTag("confirm_similar_bulk_update_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedIds.size == prompt.candidates.size) "Update All (${selectedIds.size})" else "Update (${selectedIds.size})",
                            style = AppTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
