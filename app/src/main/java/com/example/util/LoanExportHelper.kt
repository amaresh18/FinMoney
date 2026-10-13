package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.LoanTransaction
import com.example.data.model.LoanType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LoanExportHelper {

    private val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())

    fun exportToCsv(context: Context, loans: List<LoanTransaction>, currencySymbol: String): File {
        val fileName = "P2P_Loan_Ledger_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        val sb = StringBuilder()
        
        // Header
        sb.append("Counterparty Name,Contact,Type,Total Amount ($currencySymbol),Settled Amount ($currencySymbol),Remaining Due ($currencySymbol),Interest Rate (%),Start Date,Due Date,Status,Note\n")
        
        for (loan in loans) {
            val typeStr = if (loan.type == LoanType.GIVEN || loan.type == LoanType.LENT) "GIVEN (LENT)" else "TAKEN (BORROWED)"
            val remaining = (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            val start = dateFormat.format(Date(loan.startDateTimestamp))
            val due = loan.dueDateTimestamp?.let { dateFormat.format(Date(it)) } ?: "None"
            val name = escapeCsv(loan.counterpartyName)
            val contact = escapeCsv(loan.counterpartyContact)
            val note = escapeCsv(loan.notes)
            
            sb.append("$name,$contact,$typeStr,${loan.totalAmount},${loan.settledAmount},$remaining,${loan.interestRatePercent},$start,$due,${loan.status},$note\n")
        }
        
        file.writeText(sb.toString())
        return file
    }

    fun exportToExcel(context: Context, loans: List<LoanTransaction>, currencySymbol: String): File {
        val fileName = "P2P_Loan_Ledger_${System.currentTimeMillis()}.xls"
        val file = File(context.cacheDir, fileName)
        val sb = StringBuilder()
        
        sb.append("<html><head><meta charset='UTF-8'></head><body>")
        sb.append("<h2 style='color: #0F1A36; font-family: Arial;'>P2P Loan Ledger - External Backup</h2>")
        sb.append("<table border='1' cellspacing='0' cellpadding='6' style='border-collapse: collapse; font-family: Arial; font-size: 12pt;'>")
        
        // Header row with proper coloring
        sb.append("<tr style='background-color: #0F1A36; color: #FFFFFF; font-weight: bold; text-align: center;'>")
        sb.append("<th>Counterparty Name</th><th>Contact</th><th>Type</th><th>Total Amount ($currencySymbol)</th><th>Settled ($currencySymbol)</th><th>Remaining Due ($currencySymbol)</th><th>Interest Rate</th><th>Start Date</th><th>Due Date</th><th>Status</th><th>Note</th>")
        sb.append("</tr>")
        
        for (loan in loans) {
            val isGiven = loan.type == LoanType.GIVEN || loan.type == LoanType.LENT
            val typeStr = if (isGiven) "GIVEN (LENT)" else "TAKEN (BORROWED)"
            val bg = if (isGiven) "#E8F5E9" else "#FFEBEE"
            val fg = if (isGiven) "#2E7D32" else "#C62828"
            val remaining = (loan.totalAmount - loan.settledAmount).coerceAtLeast(0.0)
            val start = dateFormat.format(Date(loan.startDateTimestamp))
            val due = loan.dueDateTimestamp?.let { dateFormat.format(Date(it)) } ?: "None"
            
            sb.append("<tr style='background-color: $bg; color: $fg;'>")
            sb.append("<td><b>${escapeHtml(loan.counterpartyName)}</b></td>")
            sb.append("<td>${escapeHtml(loan.counterpartyContact)}</td>")
            sb.append("<td style='font-weight: bold; text-align: center;'>$typeStr</td>")
            sb.append("<td style='text-align: right;'>${loan.totalAmount}</td>")
            sb.append("<td style='text-align: right;'>${loan.settledAmount}</td>")
            sb.append("<td style='text-align: right; font-weight: bold;'>$remaining</td>")
            sb.append("<td style='text-align: center;'>${loan.interestRatePercent}%</td>")
            sb.append("<td style='text-align: center;'>$start</td>")
            sb.append("<td style='text-align: center;'>$due</td>")
            sb.append("<td style='text-align: center;'>${loan.status}</td>")
            sb.append("<td>${escapeHtml(loan.notes)}</td>")
            sb.append("</tr>")
        }
        
        sb.append("</table></body></html>")
        file.writeText(sb.toString())
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            // handle error
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun escapeHtml(value: String): String {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
