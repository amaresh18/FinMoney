package com.example.service.statement

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Pure Kotlin Excel XLSX text extractor using java.util.zip.ZipInputStream.
 * Extracts rows and cell values without heavy third-party dependencies.
 */
object XlsxTextExtractor {

    fun extractText(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<String>()

        try {
            val fileContents = mutableMapOf<String, ByteArray>()
            while (entry != null) {
                if (entry.name.equals("xl/sharedStrings.xml", ignoreCase = true) ||
                    entry.name.startsWith("xl/worksheets/sheet", ignoreCase = true)
                ) {
                    fileContents[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            // 1. Parse sharedStrings.xml if present
            fileContents["xl/sharedStrings.xml"]?.let { bytes ->
                val xml = String(bytes, Charsets.UTF_8)
                val tRegex = Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                for (match in tRegex.findAll(xml)) {
                    sharedStrings.add(unescapeXml(match.groupValues[1]))
                }
            }

            // 2. Parse sheet1.xml (or first worksheet)
            val sheetKey = fileContents.keys.firstOrNull { it.startsWith("xl/worksheets/sheet", ignoreCase = true) }
            if (sheetKey != null) {
                val sheetXml = String(fileContents[sheetKey]!!, Charsets.UTF_8)
                val rowRegex = Regex("<row(?:\\s[^>]*)?>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)

                for (rowMatch in rowRegex.findAll(sheetXml)) {
                    val rowContent = rowMatch.groupValues[1]
                    val cellRegex = Regex("<c\\s+([^>]*)>(?:<v>([^<]*)</v>)?(?:<is><t>([^<]*)</t></is>)?</c>", RegexOption.DOT_MATCHES_ALL)
                    val cellsInRow = mutableListOf<String>()

                    for (cellMatch in cellRegex.findAll(rowContent)) {
                        val attrs = cellMatch.groupValues[1]
                        val vVal = cellMatch.groupValues[2]
                        val isVal = cellMatch.groupValues[3]

                        val isString = attrs.contains("t=\"s\"")
                        val cellText = when {
                            isVal.isNotBlank() -> isVal
                            isString -> {
                                val idx = vVal.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else vVal
                            }
                            else -> vVal
                        }
                        if (cellText.isNotBlank()) {
                            cellsInRow.add(cellText.trim())
                        }
                    }

                    if (cellsInRow.isNotEmpty()) {
                        sheetRows.add(cellsInRow.joinToString(", "))
                    }
                }
            }

            if (sheetRows.isNotEmpty()) {
                return sheetRows.joinToString("\n")
            }

            // If empty, return all shared strings
            return sharedStrings.joinToString("\n")
        } catch (_: Exception) {
            return ""
        }
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()
    }
}
