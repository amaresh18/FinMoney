package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class TableRow(val cells: List<String>) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

object MarkdownParser {
    fun parse(rawText: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = rawText.lines()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            // Handle Code blocks
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    blocks.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd()))
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                continue
            }

            // Handle dividers
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(MarkdownBlock.Divider)
                continue
            }

            // Handle Table rows
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (trimmed.startsWith("|---") || trimmed.startsWith("|:---") || trimmed.contains("----")) {
                    continue // skip table header separator
                }
                val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (cells.isNotEmpty()) {
                    blocks.add(MarkdownBlock.TableRow(cells))
                }
                continue
            }

            // Handle headers (###, ##, # with optional space)
            if (trimmed.startsWith("#")) {
                val headerText = trimmed.replace(Regex("^#+\\s*"), "").trim()
                if (headerText.isNotEmpty()) {
                    val level = when {
                        trimmed.startsWith("###") -> 3
                        trimmed.startsWith("##") -> 2
                        else -> 1
                    }
                    blocks.add(MarkdownBlock.Header(level, headerText))
                    continue
                }
            }

            // Handle Bullet lists
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                blocks.add(MarkdownBlock.BulletItem(trimmed.removePrefix("- ").removePrefix("* ").trim()))
                continue
            }

            // Handle Numbered lists
            val numRegex = Regex("^(\\d+)\\.\\s+(.*)")
            val numMatch = numRegex.find(trimmed)
            if (numMatch != null) {
                val num = numMatch.groupValues[1].toIntOrNull() ?: 1
                val content = numMatch.groupValues[2]
                blocks.add(MarkdownBlock.NumberedItem(num, content))
                continue
            }

            // Regular paragraph
            if (trimmed.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(trimmed))
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd()))
        }

        return blocks
    }

    fun parseInlineFormatting(text: String): AnnotatedString {
        return buildAnnotatedString {
            val regex = Regex("(\\*\\*(.*?)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
            val matches = regex.findAll(text).toList()
            if (matches.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }

            var lastIndex = 0
            for (match in matches) {
                val range = match.range
                if (range.first > lastIndex) {
                    append(text.substring(lastIndex, range.first))
                }
                when {
                    match.groupValues[1].isNotEmpty() -> {
                        // Bold **...**
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(match.groupValues[2])
                        pop()
                    }
                    match.groupValues[3].isNotEmpty() -> {
                        // Italic *...*
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(match.groupValues[4])
                        pop()
                    }
                    match.groupValues[5].isNotEmpty() -> {
                        // Inline code `...`
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                        append(match.groupValues[6])
                        pop()
                    }
                }
                lastIndex = range.last + 1
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}

@Composable
fun MarkdownContentView(
    text: String,
    textColor: Color,
    accentColor: Color,
    cardBackground: Color
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 18.sp
                        2 -> 16.sp
                        else -> 14.sp
                    }
                    Text(
                        text = MarkdownParser.parseInlineFormatting(block.text),
                        color = accentColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = MarkdownParser.parseInlineFormatting(block.text),
                        color = textColor,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Text(text = "• ", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(
                            text = MarkdownParser.parseInlineFormatting(block.text),
                            color = textColor,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Text(text = "${block.number}. ", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(
                            text = MarkdownParser.parseInlineFormatting(block.text),
                            color = textColor,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.TableRow -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp).padding(vertical = 2.dp)) {
                        Text(text = "• ", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        if (block.cells.size >= 2) {
                            Text(
                                text = MarkdownParser.parseInlineFormatting("${block.cells[0]}: ${block.cells[1]}"),
                                color = textColor,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = MarkdownParser.parseInlineFormatting(block.cells.joinToString(" - ")),
                                color = textColor,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBackground)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.code,
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                is MarkdownBlock.Divider -> {
                    HorizontalDivider(color = accentColor.copy(alpha = 0.2f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
