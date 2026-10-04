package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppNotification
import com.example.ui.FinMoneyViewModel
import com.example.ui.components.IndCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ApprovalsNotificationScreen(
    viewModel: FinMoneyViewModel,
    onNavigateToLentBorrowed: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header
        item {
            IndCard(
                backgroundColor = IndSurface,
                borderColor = IndBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndAmberLight,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "APPROVALS & ALERTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Notification Feed",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "Dual party confirmations & loan updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary
                        )
                    }

                    if (unreadCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = IndAmberLight,
                            modifier = Modifier.clickable { viewModel.markAllNotificationsRead() }
                        ) {
                            Text(
                                text = "$unreadCount New • Mark Read",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                IndCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = IndTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No notifications yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = IndTextPrimary
                        )
                        Text(
                            text = "When you or counterparties record money given or borrowed, mutual approval requests and alerts will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IndTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(notifications, key = { it.id }) { notif ->
                NotificationCard(
                    notification = notif,
                    dateFormatted = dateFormatter.format(Date(notif.timestamp)),
                    onCardClick = {
                        viewModel.markNotificationRead(notif.id)
                        if (notif.relatedLoanId != null) {
                            onNavigateToLentBorrowed()
                        }
                    },
                    onMarkRead = { viewModel.markNotificationRead(notif.id) }
                )
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: AppNotification,
    dateFormatted: String,
    onCardClick: () -> Unit,
    onMarkRead: () -> Unit
) {
    val isApproval = notification.actionType == "APPROVAL_REQUEST"

    IndCard(
        onClick = onCardClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${notification.id}"),
        backgroundColor = if (!notification.isRead) IndCardHighlight else IndSurface,
        borderColor = if (!notification.isRead) IndBlue.copy(alpha = 0.4f) else IndBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isApproval) IndAmberLight else IndGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isApproval) Icons.Filled.VerifiedUser else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (isApproval) Color(0xFFB45309) else IndGreenDark,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.Medium,
                        color = IndTextPrimary
                    )

                    if (!notification.isRead) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IndRed))
                    }
                }

                Text(
                    text = notification.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IndTextSecondary
                )

                if (isApproval && notification.relatedLoanId != null) {
                    Button(
                        onClick = onCardClick,
                        colors = ButtonDefaults.buttonColors(containerColor = IndNavyHeader),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Review Agreement & Sign / Approve →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = IndTextMuted
                    )

                    if (notification.relatedLoanId != null && !isApproval) {
                        Text(
                            text = "View in Lent & Borrowed →",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
