package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audit.UserActionAuditLogger
import com.example.data.model.UserActionAuditLog
import com.example.ui.FinMoneyViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActionAuditLogsDialog(
    viewModel: FinMoneyViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logs by viewModel.auditLogs.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "APPROVED" -> logs.filter { it.eventType == UserActionAuditLogger.EVENT_APPROVED }
            "REJECTED" -> logs.filter { it.eventType == UserActionAuditLogger.EVENT_REJECTED }
            "EDITED" -> logs.filter { it.eventType == UserActionAuditLogger.EVENT_EDITED }
            else -> logs
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(top = 20.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = IndSurface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("AI Studio Action Logs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = IndNavyHeader)
                        Text("${logs.size} user decisions logged", fontSize = 12.sp, color = IndTextSecondary)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_audit_logs")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = IndNavyHeader)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val appCount = logs.count { it.eventType == UserActionAuditLogger.EVENT_APPROVED }
                    val rejCount = logs.count { it.eventType == UserActionAuditLogger.EVENT_REJECTED }
                    val editCount = logs.count { it.eventType == UserActionAuditLogger.EVENT_EDITED }

                    FilterChip(selected = selectedFilter == "ALL", onClick = { selectedFilter = "ALL" }, label = { Text("All (${logs.size})", fontSize = 11.sp) })
                    FilterChip(selected = selectedFilter == "APPROVED", onClick = { selectedFilter = "APPROVED" }, label = { Text("Appr ($appCount)", fontSize = 11.sp) })
                    FilterChip(selected = selectedFilter == "REJECTED", onClick = { selectedFilter = "REJECTED" }, label = { Text("Rej ($rejCount)", fontSize = 11.sp) })
                    FilterChip(selected = selectedFilter == "EDITED", onClick = { selectedFilter = "EDITED" }, label = { Text("Edit ($editCount)", fontSize = 11.sp) })
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No logs recorded yet. Incoming alerts approved, rejected, or edited will appear here.", color = IndTextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredLogs, key = { it.id }) { item ->
                            AuditLogItemCard(item) { text ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SMS", text))
                                Toast.makeText(context, "Copied raw message!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val prompt = UserActionAuditLogger.exportLogsForAIStudioPrompt(filteredLogs)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("AI Studio Logs", prompt))
                            Toast.makeText(context, "Copied ${filteredLogs.size} logs for AI Studio! ✓", Toast.LENGTH_LONG).show()
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = IndBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(46.dp).testTag("copy_logs_ai_studio")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy for AI", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            val prompt = UserActionAuditLogger.exportLogsForAIStudioPrompt(filteredLogs)
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, prompt)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "FinMoney Logs for AI Studio")
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Logs for AI Studio"))
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(46.dp).testTag("share_logs_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val json = UserActionAuditLogger.exportLogsAsJson(filteredLogs)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Logs JSON", json))
                            Toast.makeText(context, "Copied JSON to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        enabled = filteredLogs.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(46.dp).testTag("export_logs_json")
                    ) {
                        Text("JSON", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogItemCard(log: UserActionAuditLog, onCopyRaw: (String) -> Unit) {
    val dateStr = remember(log.timestamp) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(log.timestamp))
    }
    val color = when (log.eventType) {
        UserActionAuditLogger.EVENT_APPROVED -> IndGreenDark
        UserActionAuditLogger.EVENT_REJECTED -> IndRed
        else -> Color(0xFFD97706)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = IndSurface,
        border = BorderStroke(1.dp, IndBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.12f)) {
                    Text(log.eventType.replace("_", " "), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(dateStr, fontSize = 11.sp, color = IndTextSecondary)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(log.sender.ifBlank { log.merchant.ifBlank { "Alert" } }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IndNavyHeader)
                Text("₹${"%,.0f".format(log.amount)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = IndNavyHeader)
            }

            if (log.originalMessage.isNotBlank()) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF1F5F9), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(log.originalMessage.trim(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                        IconButton(onClick = { onCopyRaw(log.originalMessage) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF64748B), modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Text(log.diffSummary.ifBlank { "Action logged." }, fontSize = 11.sp, color = IndNavyHeader)
        }
    }
}
