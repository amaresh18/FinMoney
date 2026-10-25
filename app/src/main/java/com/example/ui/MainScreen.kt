package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import com.example.service.BankSyncManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.components.AutoExpenseTrackingHeaderStrip
import com.example.ui.components.BrandLogo
import com.example.ui.components.BrandType
import com.example.ui.components.NotificationAccessRequestDialog
import com.example.ui.components.AddCustomCategoryDialog
import com.example.ui.components.QuickMultiAddSheet
import com.example.util.FinMoneyPrefs
import com.example.util.NotificationPermissionsHelper
import com.example.ui.screens.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialOpenPending: Boolean = false,
    onPendingConsumed: () -> Unit = {},
    initialLoanCloudId: String? = null,
    initialLoanId: Long? = null,
    onLoanOpenConsumed: () -> Unit = {}
) {
    val viewModel: FinMoneyViewModel = viewModel()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.userNotice.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        BankSyncManager.syncNotificationEvents.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPendingTransactions by remember { mutableStateOf(initialOpenPending) }

    LaunchedEffect(initialOpenPending) {
        if (initialOpenPending) {
            showPendingTransactions = true
            onPendingConsumed()
        }
    }

    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val pendingTransactions by viewModel.pendingBankTransactions.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val allDebits by viewModel.allDebits.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()

    var focusLoanCloudId by remember { mutableStateOf(initialLoanCloudId) }
    var focusLoanId by remember { mutableStateOf(initialLoanId) }

    LaunchedEffect(initialLoanCloudId, initialLoanId) {
        if (!initialLoanCloudId.isNullOrBlank() || (initialLoanId != null && initialLoanId > 0L)) {
            focusLoanCloudId = initialLoanCloudId
            focusLoanId = initialLoanId
            selectedTab = 1
            showPendingTransactions = false
            onLoanOpenConsumed()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pullLoanInboxAsync()
        viewModel.checkNotificationAccess(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isAutoExpenseTrackingReady by viewModel.isAutoExpenseTrackingReady.collectAsStateWithLifecycle()
    var showAutoTrackPermissionDialog by remember { mutableStateOf(false) }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.checkNotificationAccess(context)
        if (!NotificationPermissionsHelper.hasNotificationListenerAccess(context)) {
            showAutoTrackPermissionDialog = true
        }
    }

    fun beginAutoTrackPermissionFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationPermissionsHelper.hasPostNotifications(context)
        ) {
            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationPermissionsHelper.hasNotificationListenerAccess(context)) {
            showAutoTrackPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!NotificationPermissionsHelper.isAutoExpenseTrackingReady(context) &&
            FinMoneyPrefs.shouldShowAutoTrackOnboarding(context)
        ) {
            FinMoneyPrefs.markAutoTrackOnboardingSeen(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationPermissionsHelper.hasPostNotifications(context)
            ) {
                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (!NotificationPermissionsHelper.hasNotificationListenerAccess(context)) {
                showAutoTrackPermissionDialog = true
            }
        }
    }

    var showQuickMultiAddSheet by remember { mutableStateOf(false) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showKuberaAi by remember { mutableStateOf(false) }

    fun dismissFullScreenOverlays() {
        showPendingTransactions = false
        showKuberaAi = false
        showAnalytics = false
    }

    fun navigateToTab(tab: Int) {
        dismissFullScreenOverlays()
        selectedTab = tab
    }

    val overlayObscuresMainContent = showPendingTransactions || showKuberaAi

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

    val pendingAlertsCount = pendingTransactions.size

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                                if (showKuberaAi) {
                                    showKuberaAi = false
                                } else {
                                    showPendingTransactions = false
                                    showAnalytics = false
                                    showKuberaAi = true
                                }
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
                            onClick = {
                                if (!isAutoExpenseTrackingReady) {
                                    beginAutoTrackPermissionFlow()
                                } else if (showPendingTransactions) {
                                    showPendingTransactions = false
                                } else {
                                    showKuberaAi = false
                                    showAnalytics = false
                                    showPendingTransactions = true
                                }
                            },
                            modifier = Modifier
                                .testTag("notifications_button")
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isAutoExpenseTrackingReady -> Color(0xFFFEF3C7)
                                        pendingAlertsCount > 0 || unreadNotificationCount > 0 -> Color(0xFFFEF3C7)
                                        else -> IndBlueLight
                                    }
                                )
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationCount > 0) {
                                        Badge(
                                            containerColor = IndRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                if (unreadNotificationCount > 99) "99+"
                                                else "$unreadNotificationCount"
                                            )
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
                        val headerProfilePic = userProfile?.profilePicUri?.takeIf { it.isNotBlank() }
                            ?: userProfile?.profileImageUri?.takeIf { it.isNotBlank() }
                        Box(
                            modifier = Modifier
                                .testTag("top_profile_button")
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selectedTab == 4 && !showKuberaAi) IndGreen else IndGreenLight)
                                .border(1.5.dp, if (selectedTab == 4 && !showKuberaAi) IndGreenDark else IndGreen, CircleShape)
                                .clickable { navigateToTab(4) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!headerProfilePic.isNullOrBlank()) {
                                AsyncImage(
                                    model = headerProfilePic,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
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
            }
            if (!isAutoExpenseTrackingReady) {
                AutoExpenseTrackingHeaderStrip(
                    missingStepLabel = NotificationPermissionsHelper.missingStepLabel(context),
                    onEnableClicked = { beginAutoTrackPermissionFlow() }
                )
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
                    selected = selectedTab == 0 && !overlayObscuresMainContent && !showAnalytics,
                    onClick = { navigateToTab(0) },
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
                    selected = selectedTab == 1 && !overlayObscuresMainContent,
                    onClick = { navigateToTab(1) },
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
                    selected = selectedTab == 2 && !overlayObscuresMainContent,
                    onClick = { navigateToTab(2) },
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
                    selected = selectedTab == 3 && !overlayObscuresMainContent,
                    onClick = { navigateToTab(3) },
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
                    selected = selectedTab == 4 && !overlayObscuresMainContent,
                    onClick = { navigateToTab(4) },
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
                    onBack = { showPendingTransactions = false },
                    onOpenLoan = { loanId, cloudId, notificationId ->
                        viewModel.markLoanNotificationRead(notificationId)
                        focusLoanId = loanId
                        focusLoanCloudId = cloudId
                        showPendingTransactions = false
                        selectedTab = 1
                    }
                )
            } else if (showKuberaAi) {
                KuberaAiScreen(
                    viewModel = viewModel,
                    onClose = { showKuberaAi = false }
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
                                    onOpenPending = {
                                        showKuberaAi = false
                                        showAnalytics = false
                                        showPendingTransactions = true
                                    },
                                    onOpenAddDebit = { showQuickMultiAddSheet = true }
                                )
                            }
                        }
                    1 -> LentBorrowedScreen(
                        viewModel = viewModel,
                        onBack = { selectedTab = 0 },
                        focusLoanId = focusLoanId,
                        focusLoanCloudId = focusLoanCloudId,
                        onFocusConsumed = {
                            focusLoanId = null
                            focusLoanCloudId = null
                        }
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
                        onBack = { selectedTab = 0 },
                        showShellHeader = false
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
            customCategories = customCategories,
            allHistoricalDebits = allDebits,
            onDismiss = { showQuickMultiAddSheet = false },
            onSaveBatch = { batch ->
                viewModel.insertDebits(batch)
                showQuickMultiAddSheet = false
            },
            onOpenAddCustomCategory = { showAddCustomCategoryDialog = true }
        )
    }

    if (showAddCustomCategoryDialog) {
        AddCustomCategoryDialog(
            onDismiss = { showAddCustomCategoryDialog = false },
            onSave = { name, icon, color ->
                viewModel.addCustomCategory(name, icon, color)
                showAddCustomCategoryDialog = false
            }
        )
    }

    if (showAutoTrackPermissionDialog) {
        NotificationAccessRequestDialog(
            onDismiss = {
                showAutoTrackPermissionDialog = false
                viewModel.checkNotificationAccess(context)
            },
            onGrantClicked = {
                showAutoTrackPermissionDialog = false
            }
        )
    }
}
