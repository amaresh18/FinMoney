package com.example.service.statement

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

/**
 * Pure Kotlin PDF text extractor.
 * Extracts text from PDF files by parsing content streams, decompressing
 * FlateDecode streams, and decoding PDF text operators (Tj, TJ, ', \").
 */
object PdfTextExtractor {

    fun extractText(inputStream: InputStream): String {
        val allBytes = inputStream.readBytes()
        val textBuilder = StringBuilder()

        try {
            // Find all stream ... endstream blocks
            var index = 0
            val streamMarker = "stream".toByteArray(Charsets.US_ASCII)
            val endStreamMarker = "endstream".toByteArray(Charsets.US_ASCII)

            while (index < allBytes.size) {
                val streamStart = indexOf(allBytes, streamMarker, index)
                if (streamStart == -1) break

                val contentStart = skipNewline(allBytes, streamStart + streamMarker.size)
                val streamEnd = indexOf(allBytes, endStreamMarker, contentStart)
                if (streamEnd == -1) break

                // Check stream dictionary preceding streamStart for FlateDecode
                val dictStart = (streamStart - 300).coerceAtLeast(0)
                val dictStr = String(allBytes, dictStart, streamStart - dictStart, Charsets.US_ASCII)
                val isFlate = dictStr.contains("FlateDecode")

                val streamBytes = allBytes.copyOfRange(contentStart, streamEnd)
                val streamContent: String = if (isFlate) {
                    try {
                        decompressFlate(streamBytes)
                    } catch (_: Exception) {
                        String(streamBytes, Charsets.ISO_8859_1)
                    }
                } else {
                    String(streamBytes, Charsets.ISO_8859_1)
                }

                // Extract text operators from content stream
                val extractedFromStream = extractTextFromOperators(streamContent)
                if (extractedFromStream.isNotBlank()) {
                    textBuilder.append(extractedFromStream).append("\n")
                }

                index = streamEnd + endStreamMarker.size
            }

            val result = textBuilder.toString().trim()
            if (result.isNotBlank()) {
                return result
            }

            // Fallback: search for direct text strings in raw PDF bytes
            return extractLiteralStrings(allBytes)
        } catch (e: Exception) {
            return extractLiteralStrings(allBytes)
        }
    }

    private fun decompressFlate(data: ByteArray): String {
        val bais = ByteArrayInputStream(data)
        val iis = InflaterInputStream(bais)
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var read: Int
        while (iis.read(buffer).also { read = it } != -1) {
            baos.write(buffer, 0, read)
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun extractTextFromOperators(content: String): String {
        val out = StringBuilder()
        val lines = content.lines()

        for (line in lines) {
            val trimmed = line.trim()

            // TJ array operator: [(Text 1) -10 (Text 2)] TJ
            if (trimmed.endsWith("TJ")) {
                val tjRegex = Regex("\\((.*?)\\)")
                val parts = tjRegex.findAll(trimmed).map { cleanPdfString(it.groupValues[1]) }
                val merged = parts.joinToString(" ")
                if (merged.isNotBlank()) {
                    out.append(merged).append("\n")
                }
            }
            // Tj string operator: (Text) Tj
            else if (trimmed.endsWith("Tj") || trimmed.endsWith("'")) {
                val tjRegex = Regex("\\((.*?)\\)\\s*(?:Tj|')")
                val match = tjRegex.find(trimmed)
                if (match != null) {
                    val text = cleanPdfString(match.groupValues[1])
                    if (text.isNotBlank()) {
                        out.append(text).append("\n")
                    }
                }
            }
        }

        return out.toString().trim()
    }

    private fun cleanPdfString(raw: String): String {
        return raw
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun extractLiteralStrings(bytes: ByteArray): String {
        val rawStr = String(bytes, Charsets.ISO_8859_1)
        val regex = Regex("\\(([\\w\\s.,\\-₹+/@:]{3,100})\\)")
        val found = regex.findAll(rawStr).map { it.groupValues[1].trim() }.filter { it.length > 2 }
        return found.joinToString("\n")
    }

    private fun indexOf(data: ByteArray, target: ByteArray, start: Int): Int {
        for (i in start..(data.size - target.size)) {
            var match = true
            for (j in target.indices) {
                if (data[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun skipNewline(data: ByteArray, start: Int): Int {
        var idx = start
        while (idx < data.size && (data[idx] == '\r'.code.toByte() || data[idx] == '\n'.code.toByte() || data[idx] == ' '.code.toByte())) {
            idx++
        }
        return idx
    }
}
