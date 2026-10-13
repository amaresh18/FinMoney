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
    var showUserProfile by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Intercept back button on secondary views or tabs to navigate back safely
    if (showUserProfile) {
        BackHandler {
            showUserProfile = false
        }
    } else if (showPendingTransactions) {
        BackHandler {
            showPendingTransactions = false
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(IndGreenLight)
                                .border(1.dp, IndGreen, CircleShape)
                                .clickable {
                                    showUserProfile = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile?.name?.take(1) ?: "Y").uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndGreenDark
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
                            imageVector = if (selectedTab == 4) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "Kubera AI"
                        )
                    },
                    label = { Text("Kubera AI", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD97706),
                        selectedTextColor = Color(0xFFD97706),
                        indicatorColor = Color(0xFFFEF3C7)
                    )
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showQuickMultiAddSheet = true },
                    containerColor = IndBlue,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    modifier = Modifier.testTag("quick_add_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Entry", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(IndBackground)
        ) {
            if (showUserProfile) {
                UserProfileScreen(
                    viewModel = viewModel,
                    onBack = { showUserProfile = false }
                )
            } else if (showPendingTransactions) {
                PendingTransactionsScreen(
                    viewModel = viewModel,
                    onBack = { showPendingTransactions = false }
                )
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
                    4 -> KuberaAiScreen(viewModel = viewModel)
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

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Account Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Name: ${userProfile?.name ?: "You"}")
                    Text("Phone: ${userProfile?.phoneNumber.takeIf { !it.isNullOrBlank() } ?: "Not linked"}")
                    Text("Email: ${userProfile?.email.takeIf { !it.isNullOrBlank() } ?: "Not linked"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Do you want to sign out of FinMoney?", color = IndTextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndRed)
                ) {
                    Text("Sign Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
