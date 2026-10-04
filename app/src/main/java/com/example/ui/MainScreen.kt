package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.AuthManager
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FinMoneyViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val allLoans by viewModel.allLoans.collectAsStateWithLifecycle()

    val currentUser = remember { Firebase.auth.currentUser }
    val userDisplayName = currentUser?.displayName ?: "User"
    val userEmail = currentUser?.email ?: ""
    val userInitials = if (userDisplayName.length >= 2) userDisplayName.take(2).uppercase() else "FM"

    // Dialog States
    var showAddDebitDialog by remember { mutableStateOf(false) }
    var debitInitialCategory by remember { mutableStateOf(DebitCategory.EMI.name) }
    var showAddCustomCategoryDialog by remember { mutableStateOf(false) }
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var loanInitialType by remember { mutableStateOf(LoanType.GIVEN) }
    var showQuickActionSheet by remember { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var inviteDialogData by remember { mutableStateOf<InviteDialogData?>(null) }

    val detectedSmsList by viewModel.detectedSmsTransactions.collectAsStateWithLifecycle()

    val monthName = FinMoneyViewModel.getMonthName(currentMonth)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IndNavyHeader)
            ) {
                // Top Level Navigation Bar (Navy Blue with Profile, App Title, Currency & Alerts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // User Avatar with Profile Dialog Trigger
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(IndNavyHeaderLight)
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                .clickable { showProfileDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userInitials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "FinMoney",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                            }
                            Text(
                                text = "$monthName $currentYear Utility",
                                color = IndTextOnNavyMuted,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Currency Switcher Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showCurrencyPicker = !showCurrencyPicker },
                            color = IndNavyHeaderLight
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = currencySymbol,
                                    color = IndGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "Change currency",
                                    modifier = Modifier.size(16.dp),
                                    tint = IndTextOnNavyMuted
                                )
                            }
                        }

                        // Notification Bell with Red Badge
                        IconButton(
                            onClick = { viewModel.setTab(3) },
                            modifier = Modifier.size(36.dp).testTag("notification_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge(
                                            containerColor = IndRed,
                                            contentColor = Color.White
                                        ) {
                                            Text("$unreadCount", fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 3) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Top Header Tabs
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val tabs = listOf(
                        0 to "Salary & Debits",
                        1 to "Lent & Borrowed",
                        2 to "History & Trends",
                        3 to "Mutual Approvals"
                    )

                    tabs.forEach { (index, title) ->
                        val isSelected = selectedTab == index
                        Column(
                            modifier = Modifier
                                .clickable { viewModel.setTab(index) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else IndTextOnNavyMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                if (index == 3 && unreadCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = IndRed
                                    ) {
                                        Text(
                                            text = "$unreadCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .width(if (isSelected) 36.dp else 0.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // White Bottom Navigation Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTab(
                        icon = Icons.Filled.AccountBalanceWallet,
                        label = "Salary",
                        isSelected = selectedTab == 0,
                        onClick = { viewModel.setTab(0) }
                    )

                    BottomNavTab(
                        icon = Icons.Filled.PeopleAlt,
                        label = "Lent/Owed",
                        isSelected = selectedTab == 1,
                        onClick = { viewModel.setTab(1) }
                    )

                    BottomNavTab(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "Analytics",
                        isSelected = selectedTab == 2,
                        onClick = { viewModel.setTab(2) }
                    )

                    BottomNavTab(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Approvals",
                        isSelected = selectedTab == 3,
                        badgeCount = unreadCount,
                        onClick = { viewModel.setTab(3) }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickActionSheet = true },
                containerColor = IndGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("main_quick_action_fab")
                    .padding(bottom = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Quick Add", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = IndBackground
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
                    viewModel = viewModel,
                    onSelectMonth = { m, y ->
                        viewModel.setMonthAndYear(m, y)
                        viewModel.setTab(0)
                    }
                )
                3 -> ApprovalsNotificationScreen(
                    viewModel = viewModel,
                    onNavigateToLentBorrowed = { viewModel.setTab(1) }
                )
            }
        }
    }

    // Quick Action Bottom Sheet
    if (showQuickActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickActionSheet = false },
            containerColor = IndSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary
                )

                // Primary Add Planned Debit option
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IndBlueLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndBlue.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showQuickActionSheet = false
                            debitInitialCategory = DebitCategory.GENERAL.name
                            showAddDebitDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(IndNavyHeader),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add Planned Monthly Debit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndNavyHeader
                            )
                            Text(
                                text = "Rent, Bills, WiFi, EMIs, or Custom categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = IndTextSecondary
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = IndNavyHeader)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionItem(
                        icon = Icons.Filled.CreditCard,
                        title = "Add Loan EMI",
                        subtitle = "Tenure & interest",
                        color = IndRed,
                        onClick = {
                            showQuickActionSheet = false
                            debitInitialCategory = DebitCategory.EMI.name
                            showAddDebitDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionItem(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        title = "Add Investment",
                        subtitle = "SIP / Stocks / PPF",
                        color = IndGreen,
                        onClick = {
                            showQuickActionSheet = false
                            debitInitialCategory = DebitCategory.INVESTMENT.name
                            showAddDebitDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionItem(
                        icon = Icons.Filled.Savings,
                        title = "Add Savings",
                        subtitle = "Emergency RD / FD",
                        color = IndCyan,
                        onClick = {
                            showQuickActionSheet = false
                            debitInitialCategory = DebitCategory.SAVING.name
                            showAddDebitDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionItem(
                        icon = Icons.Filled.Receipt,
                        title = "Custom Bill",
                        subtitle = "Rent, WiFi, Utilities",
                        color = IndAmber,
                        onClick = {
                            showQuickActionSheet = false
                            debitInitialCategory = DebitCategory.CUSTOM.name
                            showAddDebitDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionItem(
                        icon = Icons.Filled.ArrowUpward,
                        title = "Lend Money",
                        subtitle = "Money given to friend",
                        color = IndGreen,
                        onClick = {
                            showQuickActionSheet = false
                            if (userProfile?.phoneNumber.isNullOrBlank()) {
                                showProfileDialog = true
                            } else {
                                loanInitialType = LoanType.GIVEN
                                showAddLoanDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    QuickActionItem(
                        icon = Icons.Filled.ArrowDownward,
                        title = "Borrow Money",
                        subtitle = "Money taken from friend",
                        color = IndRed,
                        onClick = {
                            showQuickActionSheet = false
                            if (userProfile?.phoneNumber.isNullOrBlank()) {
                                showProfileDialog = true
                            } else {
                                loanInitialType = LoanType.TAKEN
                                showAddLoanDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Profile & Sign Out Dialog
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
            },
            onSignOut = {
                showProfileDialog = false
                AuthManager.signOut(context, credentialManager, onSignOut, scope)
            }
        )
    }

    // Currency Switcher Dialog
    if (showCurrencyPicker) {
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text("Select Currency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("₹" to "Indian Rupee (₹)", "$" to "US Dollar ($)", "€" to "Euro (€)", "£" to "British Pound (£)").forEach { (sym, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setCurrency(sym)
                                    showCurrencyPicker = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(sym, fontWeight = FontWeight.Bold, color = IndGreen, style = MaterialTheme.typography.titleMedium)
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyPicker = false }) {
                    Text("Close")
                }
            }
        )
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

    // Add Loan Dialog (1-on-1 Peer Record)
    if (showAddLoanDialog) {
        AddEditLoanDialog(
            currencySymbol = currencySymbol,
            initialType = loanInitialType,
            existingLoans = allLoans,
            onSavePayment = { loanId, amount, note, proof, direct ->
                viewModel.recordPartialPayment(loanId, amount, note, proof, userDisplayName, direct)
                showAddLoanDialog = false
            },
            onDismiss = { showAddLoanDialog = false },
            onSave = { loanId, type, amount, counterpartyName, counterpartyContact, interestRatePercent, isMonthlyInterest, startDateTimestamp, dueDateTimestamp, note, proofUri ->
                viewModel.createLoan(
                    type = type,
                    amount = amount,
                    counterpartyName = counterpartyName,
                    counterpartyContact = counterpartyContact,
                    interestRatePercent = interestRatePercent,
                    isMonthlyInterest = isMonthlyInterest,
                    startDateTimestamp = startDateTimestamp,
                    dueDateTimestamp = dueDateTimestamp,
                    note = note,
                    proofUri = proofUri
                )
                if (counterpartyContact.isNotBlank()) {
                    viewModel.checkUserRegistered(counterpartyContact) { isRegistered ->
                        if (!isRegistered) {
                            inviteDialogData = InviteDialogData(
                                name = counterpartyName,
                                phone = counterpartyContact,
                                amount = amount,
                                isGiven = type == LoanType.GIVEN
                            )
                        }
                    }
                }
                showAddLoanDialog = false
            }
        )
    }

    // Invite Counterparty Dialog (WhatsApp, SMS, Social Share)
    if (inviteDialogData != null) {
        val data = inviteDialogData!!
        InviteCounterpartyDialog(
            counterpartyName = data.name,
            counterpartyPhone = data.phone,
            amount = data.amount,
            isGiven = data.isGiven,
            currencySymbol = currencySymbol,
            onDismiss = { inviteDialogData = null }
        )
    }
}

@Composable
fun BottomNavTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = IndRed,
                        contentColor = Color.White
                    ) {
                        Text("$badgeCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) IndBlue else IndTextMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) IndBlue else IndTextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(1.dp, IndBorder, RoundedCornerShape(14.dp)),
        color = IndSurface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IndTextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IndTextSecondary, fontSize = 10.sp)
            }
        }
    }
}


