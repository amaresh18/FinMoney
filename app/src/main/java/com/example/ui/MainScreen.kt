package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.data.model.DebitCategory
import com.example.data.model.LoanType
import com.example.ui.components.*
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HistoryAnalyticsScreen
import com.example.ui.screens.LentBorrowedScreen
import com.example.ui.screens.SalaryUtilityScreen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinMoneyApp(
    viewModel: FinMoneyViewModel
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val allLoans by viewModel.allLoans.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifs by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()

    var showAddDebitDialog by remember { mutableStateOf(false) }
    var debitInitialCategory by remember { mutableStateOf(DebitCategory.EMI.name) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var loanInitialType by remember { mutableStateOf(LoanType.GIVEN) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Check if user is signed in (requires at least phone or email)
    val isAuthenticated = remember(userProfile) {
        userProfile != null && (userProfile?.phoneNumber?.isNotBlank() == true || userProfile?.email?.isNotBlank() == true)
    }

    if (!isAuthenticated) {
        AuthScreen(
            viewModel = viewModel,
            onContinue = {
                // Auth state will update automatically via StateFlow
            }
        )
        return
    }

    val monthName = FinMoneyViewModel.getMonthName(currentMonth)

    Scaffold(
        containerColor = IndBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IndBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("₹", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        Text("FinMoney", fontWeight = FontWeight.Bold, color = IndNavyHeader)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = IndSurface),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // Notifications Icon with unread badge
                        IconButton(
                            onClick = { showNotificationsDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifs > 0) {
                                        Badge(
                                            containerColor = IndBlue,
                                            contentColor = Color.White
                                        ) {
                                            Text(if (unreadNotifs > 9) "9+" else "$unreadNotifs", fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (unreadNotifs > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (unreadNotifs > 0) IndBlue else IndTextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IndCardSecondary
                        ) {
                            Text(
                                text = "$monthName $currentYear",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        // User Profile / Sign In button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (userProfile?.phoneNumber?.isNotBlank() == true) IndBlue else IndCardSecondary)
                                .border(1.dp, if (userProfile?.phoneNumber?.isNotBlank() == true) IndBlue else IndBorder, CircleShape)
                                .clickable { showProfileDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val pic = userProfile?.profilePicUri
                            if (!pic.isNullOrBlank()) {
                                AsyncImage(
                                    model = pic,
                                    contentDescription = "Profile",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (userProfile?.phoneNumber?.isNotBlank() == true) {
                                val initial = userProfile?.name?.take(1)?.uppercase()?.ifBlank { "U" } ?: "U"
                                Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            } else {
                                Icon(Icons.Filled.Person, contentDescription = "Profile / Sign In", tint = IndTextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = IndSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Salary") },
                    label = { Text("Salary Utility", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Filled.Handshake, contentDescription = "Lent & Borrowed") },
                    label = { Text("Lent / Borrowed", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Analytics") },
                    label = { Text("Analytics", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = IndBlue,
                        selectedTextColor = IndBlue,
                        indicatorColor = IndBlueLight
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> SalaryUtilityScreen(
                    viewModel = viewModel,
                    onOpenAddDebit = { categoryKey ->
                        debitInitialCategory = categoryKey
                        showAddDebitDialog = true
                    },
                    onOpenAddCustomCategory = { showAddCustomCategoryDialog = true }
                )
                1 -> LentBorrowedScreen(
                    viewModel = viewModel,
                    onOpenAddLoan = { type ->
                        loanInitialType = type
                        showAddLoanDialog = true
                    }
                )
                2 -> HistoryAnalyticsScreen(
                    viewModel = viewModel
                )
            }
        }
    }

    // Add Debit Dialog
    if (showAddDebitDialog) {
        AddEditDebitDialog(
            debitToEdit = null,
            customCategories = customCategories,
            monthName = monthName,
            year = currentYear,
            currencySymbol = currencySymbol,
            initialCategory = debitInitialCategory,
            onDismiss = { showAddDebitDialog = false },
            onAddCustomCategoryClick = { showAddCustomCategoryDialog = true },
            onSave = { debitId, category, customCatName, title, amount, dueDateDay, isRecurring, interestRate, totalTenure, currentTenure, notes ->
                viewModel.addDebit(
                    category = category,
                    customCategoryName = customCatName,
                    title = title,
                    amount = amount,
                    dueDateDay = dueDateDay,
                    isRecurring = isRecurring,
                    interestRate = interestRate,
                    totalTenureMonths = totalTenure,
                    currentMonthTenure = currentTenure,
                    notes = notes
                )
                showAddDebitDialog = false
            }
        )
    }

    // Add Custom Category Dialog
    if (showAddCustomCategoryDialog) {
        AddCustomCategoryDialog(
            onDismiss = { showAddCustomCategoryDialog = false },
            onSave = { name, colorHex ->
                viewModel.addCustomCategory(name, "category", colorHex)
                showAddCustomCategoryDialog = false
            }
        )
    }

    // Add Loan Dialog
    if (showAddLoanDialog) {
        AddEditLoanDialog(
            currencySymbol = currencySymbol,
            initialType = loanInitialType,
            existingLoans = allLoans,
            onDismiss = { showAddLoanDialog = false },
            onSave = { _, type, amount, name, contact, rate, isMonthly, startDate, dueDate, note, proofUri ->
                viewModel.createLoan(
                    type = type,
                    amount = amount,
                    counterpartyName = name,
                    counterpartyContact = contact,
                    interestRatePercent = rate,
                    isMonthlyInterest = isMonthly,
                    startDateTimestamp = startDate,
                    dueDateTimestamp = dueDate,
                    note = note,
                    proofUri = proofUri
                )
                showAddLoanDialog = false
            }
        )
    }

    // User Profile & Sign-In Dialog
    if (showProfileDialog) {
        ProfileDialog(
            userProfile = userProfile,
            onDismiss = { showProfileDialog = false },
            onSaveProfile = { first, last, phone, email, pic ->
                viewModel.saveUserProfile(
                    firstName = first,
                    lastName = last,
                    phoneNumber = phone,
                    email = email,
                    profilePicUri = pic
                )
                showProfileDialog = false
            },
            onSignOut = {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                coroutineScope.launch {
                    try {
                        credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                    } catch (_: Exception) {}
                }
                viewModel.clearUserProfile()
                showProfileDialog = false
            }
        )
    }

    // Notifications Dialog
    if (showNotificationsDialog) {
        NotificationsDialog(
            notifications = notifications,
            onDismiss = { showNotificationsDialog = false },
            onMarkAllRead = { viewModel.markAllNotificationsRead() }
        )
    }
}
