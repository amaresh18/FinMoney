package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.BrandLogo
import com.example.ui.components.BrandType
import com.example.ui.components.QuickMultiAddSheet
import com.example.ui.screens.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialOpenPending: Boolean = false,
    onPendingConsumed: () -> Unit = {}
) {
    val viewModel: FinMoneyViewModel = viewModel()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPendingTransactions by remember { mutableStateOf(initialOpenPending) }

    LaunchedEffect(initialOpenPending) {
        if (initialOpenPending) {
            showPendingTransactions = true
            onPendingConsumed()
        }
    }

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val pendingTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()

    var showQuickMultiAddSheet by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showKuberaAi by remember { mutableStateOf(false) }

    // Intercept back button on secondary views or tabs to navigate back safely
    if (showPendingTransactions) {
        BackHandler {
            showPendingTransactions = false
        }
    } else if (showKuberaAi) {
        BackHandler {
            showKuberaAi = false
        }
    } else if (selectedTab != 0 || showAnalytics) {
        BackHandler {
            if (showAnalytics) {
                showAnalytics = false
            } else {
                selectedTab = 0
            }
        }
    }

    val unreadNotifications = remember(notifications) {
        notifications.count { !it.isRead }
    }
    val pendingAlertsCount = pendingTransactions.size

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            Surface(
                color = IndSurface,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Brand Logo & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BrandLogo(
                            name = "FinMoney",
                            badgeSize = 32.dp
                        )
                        Column {
                            Text(
                                text = "FinMoney",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = FinMoneyViewModel.getMonthName(currentMonth) + " " + currentYear,
                                style = MaterialTheme.typography.labelSmall,
                                color = IndTextSecondary
                            )
                        }
                    }

                    // Right: Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Kubera AI Quick Action
                        IconButton(
                            onClick = {
                                showPendingTransactions = false
                                showKuberaAi = true
                            },
                            modifier = Modifier
                                .testTag("top_ai_button")
                                .clip(CircleShape)
                                .background(if (showKuberaAi) Color(0xFFFDE68A) else Color(0xFFFEF3C7))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Kubera AI Assistant",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Quick Add Multi-Debit FAB icon
                        IconButton(
                            onClick = { showQuickMultiAddSheet = true },
                            modifier = Modifier
                                .testTag("top_quick_add_button")
                                .clip(CircleShape)
                                .background(IndBlueLight)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Debits",
                                tint = IndBlue
                            )
                        }

                        // Notifications / Approvals
                        IconButton(
                            onClick = { showPendingTransactions = true },
                            modifier = Modifier
                                .testTag("notifications_button")
                                .clip(CircleShape)
                                .background(if (pendingAlertsCount > 0 || unreadNotifications > 0) Color(0xFFFEF3C7) else IndBlueLight)
                        ) {
                            BadgedBox(
                                badge = {
                                    val count = unreadNotifications + pendingAlertsCount
                                    if (count > 0) {
                                        Badge(
                                            containerColor = IndRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(if (count > 99) "99+" else "$count")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (pendingAlertsCount > 0) Icons.Filled.NotificationsActive else if (showPendingTransactions) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = "Notifications & Approvals",
                                    tint = if (pendingAlertsCount > 0) Color(0xFFD97706) else IndNavyHeader
                                )
                            }
                        }

                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .testTag("top_profile_button")
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 4 && !showKuberaAi) IndGreen else IndGreenLight)
                                .border(1.5.dp, if (selectedTab == 4 && !showKuberaAi) IndGreenDark else IndGreen, CircleShape)
                                .clickable {
                                    showPendingTransactions = false
                                    showKuberaAi = false
                                    showAnalytics = false
                                    selectedTab = 4
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile?.name?.takeIf { it != "Signed Out" && it.isNotBlank() }?.trim()?.take(1) ?: "A").uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 4 && !showKuberaAi) Color.White else IndGreenDark
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = IndSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.border(1.dp, IndBorderSubtle, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.SwapHoriz else Icons.Outlined.SwapHoriz,
                            contentDescription = "Lent & Borrowed"
                        )
                    },
                    label = { Text("Lent/Borrowed", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.BarChart else Icons.Outlined.BarChart,
                            contentDescription = "Analytics"
                        )
                    },
                    label = { Text("Analytics", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.AccountBalance else Icons.Outlined.AccountBalance,
                            contentDescription = "Reconcile"
                        )
                    },
                    label = { Text("Reconcile", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 4 && !showKuberaAi) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = "Profile & Settings"
                        )
                    },
                    label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 4 && !showKuberaAi,
                    onClick = {
                        showPendingTransactions = false
                        showKuberaAi = false
                        showAnalytics = false
                        selectedTab = 4
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )
            }
        },

    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(IndBackground)
        ) {
            if (showPendingTransactions) {
                PendingTransactionsScreen(
                    viewModel = viewModel,
                    onBack = { showPendingTransactions = false }
                )
            } else if (showKuberaAi) {
                KuberaAiScreen(viewModel = viewModel)
            } else {
                Crossfade(
                    targetState = selectedTab,
                    label = "MainScreenNavigation"
                ) { tab ->
                    when (tab) {
                        0 -> {
                            if (showAnalytics) {
                                AnalyticsScreen(
                                    viewModel = viewModel,
                                    onBackToDashboard = { showAnalytics = false }
                                )
                            } else {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onOpenAnalytics = { showAnalytics = true },
                                    onOpenPending = { showPendingTransactions = true },
                                    onOpenAddDebit = { showQuickMultiAddSheet = true }
                                )
                            }
                        }
                    1 -> LentBorrowedScreen(
                        viewModel = viewModel,
                        onBack = { selectedTab = 0 }
                    )
                    2 -> AnalyticsScreen(
                        viewModel = viewModel,
                        onBackToDashboard = { selectedTab = 0 }
                    )
                    3 -> BankReconciliationScreen(
                        viewModel = viewModel,
                        onBack = { selectedTab = 0 }
                    )
                    4 -> UserProfileScreen(
                        viewModel = viewModel,
                        onBack = { selectedTab = 0 }
                    )
                }
            }
        }
    }
}

    if (showQuickMultiAddSheet) {
        QuickMultiAddSheet(
            currencySymbol = currencySymbol,
            todayDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH),
            month = currentMonth,
            year = currentYear,
            customCategories = emptyList(),
            allHistoricalDebits = allDebits,
            onDismiss = { showQuickMultiAddSheet = false },
            onSaveBatch = { batch ->
                viewModel.insertDebits(batch)
                showQuickMultiAddSheet = false
            },
            onOpenAddCustomCategory = {}
        )
    }

}
