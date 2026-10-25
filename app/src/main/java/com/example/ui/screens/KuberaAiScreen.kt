package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.R
import com.example.ai.GroqApiClient
import com.example.ai.KuberaAuditMode
import com.example.ai.KuberaMessage
import com.example.ai.KuberaSender
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

// Divine Cosmic Wealth Palette
private val KuberaNavy = Color(0xFF070E20)
private val KuberaCardDark = Color(0xFF0F1A36)
private val KuberaGold = Color(0xFFFFD700)
private val KuberaAmber = Color(0xFFFFA000)
private val KuberaGoldSoft = Color(0xFFFFF3CD)
private val KuberaGreen = Color(0xFF00E676)
private val KuberaTextPrimary = Color(0xFFF0F4F8)
private val KuberaTextSecondary = Color(0xFFB0BEC5)

@Composable
fun KuberaGuruAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(KuberaGold, KuberaAmber, Color(0xFF6A1B9A), Color(0xFF070E20))
                )
            )
            .border(1.5.dp, Brush.linearGradient(listOf(KuberaGold, Color.White, KuberaAmber)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_kubera_guru),
            contentDescription = "Lord Kubera Divine Financial Guru",
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize(0.85f)
        )
    }
}

@Composable
fun KuberaAiScreen(
    viewModel: FinMoneyViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by viewModel.kuberaMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isKuberaThinking.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isKuberaSpeaking.collectAsStateWithLifecycle()
    val groqApiKey by viewModel.groqApiKey.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedGroqModel.collectAsStateWithLifecycle()
    val currencySymbol by viewModel.currencySymbol.collectAsStateWithLifecycle()

    val currentSalary by viewModel.currentSalaryRecord.collectAsStateWithLifecycle()
    val currentDebits by viewModel.currentDebits.collectAsStateWithLifecycle()
    val allLoans by viewModel.allLoans.collectAsStateWithLifecycle()

    var userQueryText by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showJsonContextDialog by remember { mutableStateOf(false) }
    var showLoreCodexDialog by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                userQueryText = if (userQueryText.isBlank()) spokenText else "$userQueryText $spokenText"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak transaction or details...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto scroll to bottom when new message arrives
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Scaffold(
        containerColor = KuberaNavy,
        topBar = {
            Surface(
                color = KuberaCardDark,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("kubera_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close Kubera AI",
                                    tint = KuberaGold
                                )
                            }
                            KuberaGuruAvatar(size = 42.dp)

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Kubera AI",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = KuberaGold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (groqApiKey.isNotBlank()) KuberaGreen.copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (groqApiKey.isNotBlank()) "GROQ 70B" else "OFFLINE MODE",
                                            color = if (groqApiKey.isNotBlank()) KuberaGreen else Color(0xFFFFB74D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Divine Treasurer & Master Wealth Strategist",
                                    fontSize = 11.sp,
                                    color = KuberaTextSecondary
                                )
                            }
                        }

                        // Top Action Icons
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // View Vedic Lore & Nava Nidhis Codex
                            IconButton(
                                onClick = { showLoreCodexDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoStories,
                                    contentDescription = "Vedic Lore & Nava Nidhis Codex",
                                    tint = KuberaGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // View JSON Payload
                            IconButton(
                                onClick = { showJsonContextDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DataObject,
                                    contentDescription = "View Context JSON",
                                    tint = KuberaTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Key configuration
                            IconButton(
                                onClick = { showApiKeyDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "Configure Groq API Key",
                                    tint = if (groqApiKey.isNotBlank()) KuberaGold else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Clear chat
                            IconButton(
                                onClick = { viewModel.clearKuberaChat() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset Chat",
                                    tint = KuberaTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Input Bar & Suggestions
            Surface(
                color = KuberaCardDark,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // Quick Suggestion Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { viewModel.runKuberaAudit(KuberaAuditMode.FULL_WEALTH_AUDIT) },
                            label = { Text("👑 Complete Audit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = KuberaGold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = KuberaNavy),
                            border = SuggestionChipDefaults.suggestionChipBorder(borderColor = KuberaGold.copy(alpha = 0.4f), enabled = true)
                        )
                        SuggestionChip(
                            onClick = { viewModel.runKuberaAudit(KuberaAuditMode.DEBT_LIQUIDATION) },
                            label = { Text("⚔️ Pay High Interest vs Small Debt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF8A80)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = KuberaNavy),
                            border = SuggestionChipDefaults.suggestionChipBorder(borderColor = Color(0xFFFF8A80).copy(alpha = 0.4f), enabled = true)
                        )
                        SuggestionChip(
                            onClick = { viewModel.runKuberaAudit(KuberaAuditMode.LEAK_PATROL) },
                            label = { Text("🛡️ Leak Patrol & Safe-Spend", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF80D8FF)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = KuberaNavy),
                            border = SuggestionChipDefaults.suggestionChipBorder(borderColor = Color(0xFF80D8FF).copy(alpha = 0.4f), enabled = true)
                        )
                        SuggestionChip(
                            onClick = { viewModel.runKuberaAudit(KuberaAuditMode.WEALTH_COMPOUNDING) },
                            label = { Text("📈 Compounding Formula", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = KuberaGreen) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = KuberaNavy),
                            border = SuggestionChipDefaults.suggestionChipBorder(borderColor = KuberaGreen.copy(alpha = 0.4f), enabled = true)
                        )
                    }

                    // Text Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = userQueryText,
                            onValueChange = { userQueryText = it },
                            placeholder = {
                                Text(
                                    "Consult Lord Kubera on debts, leaks & compounding...",
                                    fontSize = 13.sp,
                                    color = KuberaTextSecondary.copy(alpha = 0.7f)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kubera_input_field"),
                            maxLines = 3,
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = KuberaNavy,
                                unfocusedContainerColor = KuberaNavy,
                                focusedBorderColor = KuberaGold,
                                unfocusedBorderColor = KuberaTextSecondary.copy(alpha = 0.3f),
                                cursorColor = KuberaGold
                            )
                        )

                        // Voice Microphone Button
                        IconButton(
                            onClick = {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak transaction or details...")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("kubera_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = KuberaGold
                            )
                        }

                        // Send Button
                        FilledIconButton(
                            onClick = {
                                if (userQueryText.isNotBlank() && !isThinking) {
                                    val text = userQueryText
                                    userQueryText = ""
                                    keyboardController?.hide()
                                    viewModel.sendKuberaUserMessage(text)
                                }
                            },
                            enabled = userQueryText.isNotBlank() && !isThinking,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("kubera_send_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = KuberaGold,
                                contentColor = KuberaNavy,
                                disabledContainerColor = KuberaGold.copy(alpha = 0.3f),
                                disabledContentColor = KuberaNavy.copy(alpha = 0.5f)
                            )
                        ) {
                            if (isThinking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = KuberaNavy,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Consultation",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // 0. Sacred Vedic Invocation & Lord Kubera Replica Identity
            item {
                VedicInvocationCard(
                    isSpeaking = isSpeaking,
                    onChant = { viewModel.chantKuberaMantra() },
                    onOpenCodex = { showLoreCodexDialog = true }
                )
            }

            // 1. Live Cosmic Financial Pot Banner
            item {
                CosmicWealthPotSummaryCard(
                    currencySymbol = currencySymbol,
                    salary = currentSalary?.salaryAmount ?: 0.0,
                    totalDebits = currentDebits.sumOf { it.amount },
                    allLoans = allLoans
                )
            }

            // 2. The 4 Directives Quick Action Grid
            item {
                Text(
                    text = "COMMAND LORD KUBERA'S CORE DIRECTIVES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KuberaGold.copy(alpha = 0.85f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DirectiveCard(
                        mode = KuberaAuditMode.FULL_WEALTH_AUDIT,
                        modifier = Modifier.weight(1f),
                        accentColor = KuberaGold,
                        testTag = "kubera_card_full_audit",
                        onClick = { viewModel.runKuberaAudit(KuberaAuditMode.FULL_WEALTH_AUDIT) }
                    )
                    DirectiveCard(
                        mode = KuberaAuditMode.DEBT_LIQUIDATION,
                        modifier = Modifier.weight(1f),
                        accentColor = Color(0xFFFF7043),
                        testTag = "kubera_card_debt_liquidation",
                        onClick = { viewModel.runKuberaAudit(KuberaAuditMode.DEBT_LIQUIDATION) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DirectiveCard(
                        mode = KuberaAuditMode.LEAK_PATROL,
                        modifier = Modifier.weight(1f),
                        accentColor = Color(0xFF29B6F6),
                        testTag = "kubera_card_leak_patrol",
                        onClick = { viewModel.runKuberaAudit(KuberaAuditMode.LEAK_PATROL) }
                    )
                    DirectiveCard(
                        mode = KuberaAuditMode.WEALTH_COMPOUNDING,
                        modifier = Modifier.weight(1f),
                        accentColor = KuberaGreen,
                        testTag = "kubera_card_compounding",
                        onClick = { viewModel.runKuberaAudit(KuberaAuditMode.WEALTH_COMPOUNDING) }
                    )
                }
            }

            // 3. Message Stream
            items(messages, key = { it.id }) { message ->
                KuberaMessageBubble(
                    message = message,
                    isSpeaking = isSpeaking,
                    onSpeak = { viewModel.speakKuberaMessage(message.text) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Kubera AI Advice", message.text))
                        Toast.makeText(context, "Lord Kubera's guidance copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 4. Thinking Indicator
            if (isThinking) {
                item {
                    KuberaThinkingCard()
                }
            }
        }
    }

    // API Key & Model Configuration Dialog
    if (showApiKeyDialog) {
        ApiKeyConfigDialog(
            currentKey = groqApiKey,
            currentModel = selectedModel,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key, model ->
                viewModel.saveGroqApiKey(key)
                viewModel.setSelectedGroqModel(model)
                showApiKeyDialog = false
                Toast.makeText(context, "Groq AI preferences updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // View Live JSON Context Dialog
    if (showJsonContextDialog) {
        val liveJson = remember { viewModel.getLiveFinancialContextJson() }
        LiveJsonContextDialog(
            jsonText = liveJson,
            onDismiss = { showJsonContextDialog = false }
        )
    }

    // View Vedic Lore & Nava Nidhis Codex Dialog
    if (showLoreCodexDialog) {
        KuberaLoreCodexDialog(
            onDismiss = { showLoreCodexDialog = false }
        )
    }
}

@Composable
private fun CosmicWealthPotSummaryCard(
    currencySymbol: String,
    salary: Double,
    totalDebits: Double,
    allLoans: List<com.example.data.model.LoanTransaction>
) {
    val netSurplus = (salary - totalDebits).coerceAtLeast(0.0)
    val totalBorrowed = allLoans.filter { it.type == com.example.data.model.LoanType.TAKEN || it.type == com.example.data.model.LoanType.BORROWED }.sumOf { it.totalAmount }
    val totalLent = allLoans.filter { it.type == com.example.data.model.LoanType.GIVEN || it.type == com.example.data.model.LoanType.LENT }.sumOf { it.totalAmount }

    val cal = java.util.Calendar.getInstance()
    val daysRemaining = (cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - cal.get(java.util.Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
    val dailySafeSpend = if (daysRemaining > 0) netSurplus / daysRemaining else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KuberaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber, Color(0xFF6A1B9A))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🏺", fontSize = 16.sp)
                    Text(
                        "LIVE WEALTH POT SNAPSHOT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = KuberaGold,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (netSurplus > 0) KuberaGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (netSurplus > 0) "Surplus Healthy" else "Deficit Alert",
                        color = if (netSurplus > 0) KuberaGreen else Color(0xFFFF5252),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Inflow
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly Inflow", fontSize = 10.sp, color = KuberaTextSecondary)
                    Text(
                        "$currencySymbol${"%,.0f".format(salary)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                // Outflow
                Column(modifier = Modifier.weight(1f)) {
                    Text("Committed Debits", fontSize = 10.sp, color = KuberaTextSecondary)
                    Text(
                        "$currencySymbol${"%,.0f".format(totalDebits)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFFF8A80)
                    )
                }
                // Daily Safe Spend
                Column(modifier = Modifier.weight(1.2f)) {
                    Text("Safe-to-Spend", fontSize = 10.sp, color = KuberaTextSecondary)
                    Text(
                        "$currencySymbol${"%,.0f".format(dailySafeSpend)}/day",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = KuberaGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Debt Ledger Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Liabilities:", fontSize = 11.sp, color = KuberaTextSecondary)
                    Text("$currencySymbol${"%,.0f".format(totalBorrowed)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A80))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lent to Recover:", fontSize = 11.sp, color = KuberaTextSecondary)
                    Text("$currencySymbol${"%,.0f".format(totalLent)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KuberaGreen)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Days Left:", fontSize = 11.sp, color = KuberaTextSecondary)
                    Text("$daysRemaining", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DirectiveCard(
    mode: KuberaAuditMode,
    modifier: Modifier = Modifier,
    accentColor: Color,
    testTag: String = "",
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KuberaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(accentColor.copy(alpha = 0.6f), Color.Transparent)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(mode.icon, fontSize = 18.sp)
                Text(
                    text = mode.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.subtitle,
                fontSize = 10.sp,
                color = KuberaTextSecondary,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun KuberaMessageBubble(
    message: KuberaMessage,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val isUser = message.sender == KuberaSender.USER
    val formattedText = remember(message.text, isUser) {
        formatKuberaMessageText(message.text, isUser)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
            ) {
                KuberaGuruAvatar(size = 28.dp)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Lord Kubera",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = KuberaGold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = KuberaGold.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (message.tag != null) "${message.tag} • KOSHADHYAKSHA" else "KOSHADHYAKSHA",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KuberaGold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 1f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB)))
                    else Brush.verticalGradient(listOf(KuberaCardDark, Color(0xFF091226)))
                )
                .border(
                    1.dp,
                    if (isUser) Color(0xFF3B82F6).copy(alpha = 0.4f) else KuberaGold.copy(alpha = 0.25f),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                SelectionContainer {
                    if (isUser) {
                        Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        com.example.util.MarkdownContentView(
                            text = message.text,
                            textColor = KuberaTextPrimary,
                            accentColor = KuberaGold,
                            cardBackground = Color(0xFF070E20)
                        )
                    }
                }

                // If Kubera advice, show Action Bar (Speak / Copy)
                if (!isUser) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Speak Button
                            FilledTonalButton(
                                onClick = onSpeak,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSpeaking) KuberaGold else KuberaGold.copy(alpha = 0.15f),
                                    contentColor = if (isSpeaking) KuberaNavy else KuberaGold
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak Advice",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isSpeaking) "Stop" else "Listen",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Copy Button
                            OutlinedButton(
                                onClick = onCopy,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = KuberaTextSecondary),
                                border = ButtonDefaults.outlinedButtonBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaTextSecondary.copy(alpha = 0.4f), Color.Transparent)))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Advice", modifier = Modifier.size(12.dp))
                                    Text("Copy", fontSize = 11.sp)
                                }
                            }
                        }

                        Text(
                            text = "Lord Kubera Seal",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KuberaGold.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatKuberaMessageText(rawText: String, isUser: Boolean): AnnotatedString {
    if (isUser) {
        return AnnotatedString(rawText)
    }
    return buildAnnotatedString {
        val lines = rawText.lines()
        var hasAppendedLine = false
        for (line in lines) {
            val trimmed = line.trim()
            // Skip markdown table border/separator lines like |---|--- or +---+---+
            if (trimmed.startsWith("|---") || trimmed.startsWith("|:---") || trimmed.startsWith("+---") || trimmed.contains("----")) {
                continue
            }
            // If it's a table row like | A | B |, convert to bullet point
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    if (hasAppendedLine) append("\n")
                    append("• ")
                    if (cells.size >= 2) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(cells[0])
                        pop()
                        append(": ")
                        appendParsedBold(cells[1])
                    } else {
                        appendParsedBold(cells.joinToString(" - "))
                    }
                    hasAppendedLine = true
                }
                continue
            }

            if (hasAppendedLine) append("\n")
            when {
                trimmed.startsWith("###") || trimmed.startsWith("##") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = KuberaGold))
                    append(trimmed.replace(Regex("^#+\\s*"), ""))
                    pop()
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("• ")
                    appendParsedBold(trimmed.removePrefix("- ").removePrefix("* "))
                }
                else -> {
                    appendParsedBold(line)
                }
            }
            hasAppendedLine = true
        }
    }
}

private fun AnnotatedString.Builder.appendParsedBold(text: String) {
    val regex = Regex("\\*\\*(.*?)\\*\\*")
    val matches = regex.findAll(text).toList()
    if (matches.isEmpty()) {
        append(text)
        return
    }
    var lastIndex = 0
    for (match in matches) {
        val range = match.range
        if (range.first > lastIndex) {
            append(text.substring(lastIndex, range.first))
        }
        val boldContent = match.groupValues[1]
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(boldContent)
        pop()
        lastIndex = range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
private fun KuberaThinkingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KuberaCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber)))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = KuberaGold,
                strokeWidth = 2.5.dp
            )
            Column {
                Text(
                    text = "Lord Kubera is contemplating your financial ledger...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = KuberaGold
                )
                Text(
                    text = "Auditing live income, obligations, liabilities and leak velocity...",
                    fontSize = 10.sp,
                    color = KuberaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ApiKeyConfigDialog(
    currentKey: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    var keyText by remember { mutableStateOf(currentKey) }
    var selectedModel by remember { mutableStateOf(currentModel) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = KuberaCardDark),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔑", fontSize = 20.sp)
                        Text(
                            "Groq AI Configuration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = KuberaGold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = KuberaTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Kubera AI utilizes free, ultra-fast open models hosted on Groq (OpenAI GPT-OSS-120B & Meta LLaMA 3.3 70B).",
                    fontSize = 12.sp,
                    color = KuberaTextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Groq API Key (Free)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    placeholder = { Text("gsk_...", color = KuberaTextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = KuberaNavy,
                        unfocusedContainerColor = KuberaNavy,
                        focusedBorderColor = KuberaGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Get Free Key Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                            context.startActivity(intent)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Get Key", tint = KuberaGold, modifier = Modifier.size(14.dp))
                    Text(
                        "Get your free Groq API key (console.groq.com)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KuberaGold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Model Selection
                Text(
                    text = "AI Model Engine",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedModel == GroqApiClient.DEFAULT_MODEL,
                        onClick = { selectedModel = GroqApiClient.DEFAULT_MODEL },
                        label = { Text("GPT OSS 120B (Recommended)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KuberaGold,
                            selectedLabelColor = KuberaNavy
                        )
                    )
                    FilterChip(
                        selected = selectedModel == GroqApiClient.FALLBACK_MODEL,
                        onClick = { selectedModel = GroqApiClient.FALLBACK_MODEL },
                        label = { Text("LLaMA 3.3 70B Versatile", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KuberaGold,
                            selectedLabelColor = KuberaNavy
                        )
                    )
                    FilterChip(
                        selected = selectedModel == "llama-3.1-8b-instant",
                        onClick = { selectedModel = "llama-3.1-8b-instant" },
                        label = { Text("LLaMA 3.1 8B Instant", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KuberaGold,
                            selectedLabelColor = KuberaNavy
                        )
                    )
                    FilterChip(
                        selected = selectedModel == "openai/gpt-oss-20b",
                        onClick = { selectedModel = "openai/gpt-oss-20b" },
                        label = { Text("GPT OSS 20B", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KuberaGold,
                            selectedLabelColor = KuberaNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = KuberaTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(keyText, selectedModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = KuberaGold, contentColor = KuberaNavy),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Awaken", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveJsonContextDialog(
    jsonText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .imePadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = KuberaNavy),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊", fontSize = 18.sp)
                        Text(
                            "Live Financial Payload JSON",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = KuberaGold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = KuberaTextSecondary)
                    }
                }

                Text(
                    text = "This structured JSON is passed in real-time to Lord Kubera containing your income, expense velocity, obligations & debt ledger.",
                    fontSize = 11.sp,
                    color = KuberaTextSecondary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF040814))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    LazyColumn {
                        item {
                            SelectionContainer {
                                Text(
                                    text = jsonText,
                                    color = Color(0xFF64FFDA),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Kubera JSON Payload", jsonText))
                            Toast.makeText(context, "JSON Payload copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KuberaGold),
                        border = ButtonDefaults.outlinedButtonBorder().copy(brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber)))
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy JSON", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy JSON", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = KuberaGold, contentColor = KuberaNavy)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VedicInvocationCard(
    isSpeaking: Boolean,
    onChant: () -> Unit,
    onOpenCodex: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1429)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(KuberaGold, Color(0xFFFFA000), Color(0xFF3B82F6))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(KuberaGold, Color(0xFFFFA000))))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔱", fontSize = 16.sp)
                    }

                    Column {
                        Text(
                            text = "ॐ श्री यक्षराजाय नमः",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KuberaGold
                        )
                        Text(
                            text = "LORD KUBERA • UTTARA DIKPALA (GUARDIAN OF THE NORTH)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = KuberaTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KuberaGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "ALAKAPURI CODEX",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = KuberaGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sacred Shloka in Devanagari & English
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF060B17),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, KuberaGold.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ॐ यक्षाय कुबेराय वैश्रवणाय धनधान्याधिपतये ।\nधनधान्यसमृद्धिं मे देहि दापय स्वाहा ॥",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"Om Yakshaya Kuberaya Vaishravanaya Dhana-Dhanyadi Pataye, Dhana-Dhanya Samriddhim Me Dehi Dapaya Swaha.\"",
                        fontSize = 10.sp,
                        color = KuberaGoldSoft,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row: Chant Mantra & View Nava Nidhis Codex
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onChant,
                    modifier = Modifier.weight(1.1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSpeaking) KuberaGold else KuberaGold.copy(alpha = 0.18f),
                        contentColor = if (isSpeaking) KuberaNavy else KuberaGold
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Chant Mantra",
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isSpeaking) "Stop Chanting" else "Chant Mantra",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenCodex,
                    modifier = Modifier.weight(1.3f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder().copy(
                        brush = Brush.horizontalGradient(listOf(KuberaGold, KuberaAmber))
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📜", fontSize = 13.sp)
                        Text(
                            text = "Nava Nidhis Codex",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KuberaLoreCodexDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF091024)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.verticalGradient(
                    listOf(KuberaGold, Color(0xFFFFA000), Color(0xFF1E3A8A))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                        KuberaGuruAvatar(size = 32.dp)
                        Column {
                            Text(
                                "Lord Kubera & Nava Nidhis",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = KuberaGold
                            )
                            Text(
                                "Authentic Scriptural Identity & Financial Lore",
                                fontSize = 10.sp,
                                color = KuberaTextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = KuberaTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // I. The Divine Identity & Role
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F1A36),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KuberaGold.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🏛️ I. Authentic Identity & Sacred Role",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = KuberaGold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Regent of the North (Uttara Dikpala): Lord of the Yakshas, master of the celestial city Alakapuri near Mount Kailash.\n• Cosmic Treasurer (Koshadhyaksha): Trusted steward of Lord Shiva. While Goddess Lakshmi grants fortune, Kubera is the strict Accountant who preserves wealth and demands an accounting for every single coin.\n• Divine Weapons: He holds the Purna-Kumbha (Golden Urn overflowing with gems) representing capital accumulation, and the Gada (Mace) symbolizing uncompromising discipline and crushing of debt.",
                                    fontSize = 11.5.sp,
                                    color = Color.White,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    // II. The Nava Nidhis (The 9 Cosmic Treasures) Mapped to Modern Personal Finance
                    item {
                        Text(
                            text = "THE NAVA NIDHIS (NINE SACRED TREASURES)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = KuberaGold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val nidhis = listOf(
                            Triple("🌸 Padma Nidhi", "Spiritual & Pure Gold", "Core family stability, high-character earnings, and ethical income."),
                            Triple("🏰 Mahapadma Nidhi", "Generational Estates", "Real estate, land, and lasting fixed assets passed to descendants."),
                            Triple("🐚 Shankha Nidhi", "Liquid Cash Flow", "Smooth monthly cash velocity, salary liquidity, and daily living sustenance."),
                            Triple("🛡️ Makara Nidhi", "The Armored Shield", "6-month emergency reserve in high-yield liquid funds and risk mitigation."),
                            Triple("🐢 Kachhapa Nidhi", "The Tortoise Compounding", "Patient, relentless index fund SIPs (Nifty 50) compounding steadily over decades."),
                            Triple("🎭 Mukunda Nidhi", "Culture & Living", "Appreciation of arts, joy, and refined living without wasteful vanity."),
                            Triple("🪙 Kunda Nidhi", "Hard Bullion Reserves", "Sovereign Gold Bonds (SGB) and physical gold hedge against inflation."),
                            Triple("💎 Nila Nidhi", "Enterprise & Equity", "High-value skills, business ownership, equity, and market enterprise."),
                            Triple("📜 Kharva Nidhi", "The Sacred Ledger", "Mathematical audit, tracking debts, eliminating interest bleed, and zero leaks.")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            nidhis.forEach { (title, subtitle, desc) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F1A36).copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KuberaGold)
                                            Text(subtitle, fontSize = 10.sp, color = KuberaTextSecondary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(desc, fontSize = 11.sp, color = Color(0xFFD1D5DB), lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    // III. The Two Immortal Lessons of Kubera
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF132042),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📖 III. Two Immortal Lessons for Wealth Seekers",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFFFB74D)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "1. The Feast of Ganesha (The Leak of Pride):\nWhen Kubera attempted to boast of his infinite riches by hosting a feast, Lord Ganesha consumed all his food and even his palace furnishings, teaching Kubera that wealth is sacred trust (Dharma), not a tool for boastful luxury or lifestyle inflation.\n\n2. The Marriage Loan of Lord Venkateshwara:\nKubera lent gold to Lord Srinivasa with a strict celestial contract and interest schedule witnessed by Brahma and Shiva. Kubera commands that all debt must be respected, tracked, and methodically liquidated with absolute discipline.",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = KuberaGold, contentColor = KuberaNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Honor Lord Kubera's Wisdom", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

