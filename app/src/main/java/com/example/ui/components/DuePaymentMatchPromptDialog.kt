package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.learning.DueItemType
import com.example.learning.DuePaymentMatchPrompt
import com.example.ui.theme.*

/**
 * Axio-style Confirmation Dialog:
 * When a debit or credit arrives with the same amount as a pending/due expense,
 * lent money record, or pending income, prompts the user to confirm whether
 * this payment is towards that pending due item.
 */
@Composable
fun DuePaymentMatchPromptDialog(
    prompt: DuePaymentMatchPrompt,
    currencySymbol: String = "₹",
    onConfirmSettle: () -> Unit,
    onRejectKeepSeparate: () -> Unit,
    onDismiss: () -> Unit
) {
    val tx = prompt.transaction
    val candidate = prompt.candidate
    val isCredit = tx.type.equals("CREDIT", ignoreCase = true)

    val dialogTitle = when (candidate.itemType) {
        DueItemType.DUE_EXPENSE -> "Payment for Due Expense?"
        DueItemType.LENT_MONEY_RECOVERY -> "Lent Money Payment Received?"
        DueItemType.BORROWED_MONEY_REPAY -> "Borrowed Money Repayment?"
        DueItemType.PENDING_INCOME -> "Pending Credit Received?"
    }

    val questionText = when (candidate.itemType) {
        DueItemType.DUE_EXPENSE ->
            "Is this $currencySymbol${String.format("%.2f", tx.amount)} debit to '${tx.merchant.ifBlank { tx.bankName }}' payment for your due expense '${candidate.title}'?"
        DueItemType.LENT_MONEY_RECOVERY ->
            "Did ${candidate.personOrCategoryName} pay back the $currencySymbol${String.format("%.2f", tx.amount)} lent money?"
        DueItemType.BORROWED_MONEY_REPAY ->
            "Is this $currencySymbol${String.format("%.2f", tx.amount)} payment towards your borrowed loan from ${candidate.personOrCategoryName}?"
        DueItemType.PENDING_INCOME ->
            "Is this $currencySymbol${String.format("%.2f", tx.amount)} credit towards your expected income '${candidate.title}'?"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("due_payment_match_prompt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isCredit) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCredit) Icons.Default.Savings else Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = if (isCredit) AppTheme.colors.primary else AppTheme.colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dialogTitle,
                            style = AppTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Same amount match detected",
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.textSecondary
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

                // Comparison Box (Transaction Alert vs Due Item)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppTheme.colors.background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Incoming Alert
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCredit) "INCOMING CREDIT ALERT" else "INCOMING DEBIT ALERT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) AppTheme.colors.primary else AppTheme.colors.accent,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = tx.merchant.ifBlank { tx.bankName },
                                    style = AppTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$currencySymbol${String.format("%.2f", tx.amount)}",
                                style = AppTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCredit) AppTheme.colors.primary else AppTheme.colors.textPrimary
                            )
                        }

                        HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))

                        // Matching Pending / Due Item
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MATCHES PENDING DUE RECORD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.secondary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = candidate.title,
                                    style = AppTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (candidate.personOrCategoryName.isNotBlank()) {
                                    Text(
                                        text = candidate.personOrCategoryName,
                                        style = AppTheme.typography.caption,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$currencySymbol${String.format("%.2f", candidate.amount)}",
                                    style = AppTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.secondary
                                )
                                Text(
                                    text = candidate.dateInfo,
                                    style = AppTheme.typography.caption,
                                    color = AppTheme.colors.textMuted
                                )
                            }
                        }
                    }
                }

                // Question Prompt Text
                Text(
                    text = questionText,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.Medium
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRejectKeepSeparate,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("due_match_keep_separate_button")
                    ) {
                        Text(
                            text = "No, Keep Separate",
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.colors.textSecondary
                        )
                    }

                    Button(
                        onClick = onConfirmSettle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCredit) AppTheme.colors.primary else AppTheme.colors.accent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 44.dp)
                            .testTag("due_match_confirm_settle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Yes, Mark Paid",
                            style = AppTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
