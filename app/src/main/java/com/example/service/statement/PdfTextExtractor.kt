package com.example.service.statement

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Pure Kotlin PDF text extractor with ToUnicode CMap support.
 *
 * Handles modern UPI statement PDFs (PhonePe Skia, Google Pay dart_pdf) that
 * embed text as CID / custom-encoded glyph streams instead of plain ASCII.
 */
object PdfTextExtractor {

    fun extractText(inputStream: InputStream): String {
        val allBytes = inputStream.readBytes()
        return try {
            val streams = collectStreams(allBytes)
            val toUnicodeMaps = streams.mapNotNull { parseToUnicodeCMap(it) }
            val contentStreams = streams.filter { content ->
                (content.contains("Tj") || content.contains("TJ")) &&
                    !content.contains("beginbfchar") &&
                    !content.contains("CIDInit") &&
                    !looksLikeBinaryFontOrImage(content)
            }

            val builder = StringBuilder()
            for (content in contentStreams) {
                val extracted = extractTextFromContent(content, toUnicodeMaps)
                if (extracted.isNotBlank()) {
                    builder.append(extracted).append('\n')
                }
            }

            val result = normalizeExtractedText(builder.toString())
            if (result.isNotBlank()) result else extractLiteralStrings(allBytes)
        } catch (_: Exception) {
            extractLiteralStrings(allBytes)
        }
    }

    private data class PdfStream(val dict: String, val data: ByteArray)

    private fun collectStreams(allBytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        val streamMarker = "stream".toByteArray(Charsets.US_ASCII)
        val endStreamMarker = "endstream".toByteArray(Charsets.US_ASCII)

        while (index < allBytes.size) {
            val streamStart = indexOf(allBytes, streamMarker, index)
            if (streamStart == -1) break
            // Avoid matching inside names like /Filterstream...
            if (streamStart > 0) {
                val prev = allBytes[streamStart - 1].toInt().and(0xFF).toChar()
                if (prev.isLetterOrDigit() || prev == '/') {
                    index = streamStart + streamMarker.size
                    continue
                }
            }

            val contentStart = skipNewline(allBytes, streamStart + streamMarker.size)
            val streamEnd = indexOf(allBytes, endStreamMarker, contentStart)
            if (streamEnd == -1) break

            val dictStart = (streamStart - 500).coerceAtLeast(0)
            val dictStr = String(allBytes, dictStart, streamStart - dictStart, Charsets.US_ASCII)
            var end = streamEnd
            while (end > contentStart) {
                val b = allBytes[end - 1].toInt().and(0xFF)
                if (b == '\n'.code || b == '\r'.code || b == ' '.code) end-- else break
            }
            val streamBytes = allBytes.copyOfRange(contentStart, end)
            val isFlate = dictStr.contains("FlateDecode")
            val decoded = if (isFlate) {
                decompressFlate(streamBytes) ?: String(streamBytes, Charsets.ISO_8859_1)
            } else {
                String(streamBytes, Charsets.ISO_8859_1)
            }
            result.add(decoded)
            index = streamEnd + endStreamMarker.size
        }
        return result
    }

    private fun looksLikeBinaryFontOrImage(content: String): Boolean {
        if (content.length > 200) {
            var printable = 0
            val sample = content.take(400)
            for (ch in sample) {
                val c = ch.code
                if (c in 32..126 || c == 9 || c == 10 || c == 13) printable++
            }
            if (printable < sample.length / 3) return true
        }
        return content.startsWith("mntr") || content.contains("\u0000\u0001")
    }

    private fun parseToUnicodeCMap(content: String): Map<Int, String>? {
        if (!content.contains("beginbfchar") && !content.contains("beginbfrange")) return null
        val map = mutableMapOf<Int, String>()

        val bfCharRegex = Regex("""beginbfchar([\s\S]*?)endbfchar""")
        for (block in bfCharRegex.findAll(content)) {
            val pairRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            for (m in pairRegex.findAll(block.groupValues[1])) {
                val src = m.groupValues[1].toInt(16)
                map[src] = decodeUtf16BeHex(m.groupValues[2])
            }
        }

        val bfRangeRegex = Regex("""beginbfrange([\s\S]*?)endbfrange""")
        for (block in bfRangeRegex.findAll(content)) {
            val rangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            for (m in rangeRegex.findAll(block.groupValues[1])) {
                val lo = m.groupValues[1].toInt(16)
                val hi = m.groupValues[2].toInt(16)
                val base = m.groupValues[3].toInt(16)
                for (cid in lo..hi) {
                    val code = base + (cid - lo)
                    if (code != 0) map[cid] = code.toChar().toString()
                }
            }
            // Array form: <lo> <hi> [<dst1> <dst2> ...]
            val arrayRange = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[([^\]]*)\]""")
            for (m in arrayRange.findAll(block.groupValues[1])) {
                val lo = m.groupValues[1].toInt(16)
                val destinations = Regex("""<([0-9A-Fa-f]+)>""").findAll(m.groupValues[3]).map {
                    decodeUtf16BeHex(it.groupValues[1])
                }.toList()
                destinations.forEachIndexed { i, dst ->
                    map[lo + i] = dst
                }
            }
        }

        return map.takeIf { it.isNotEmpty() }
    }

    private fun decodeUtf16BeHex(hexRaw: String): String {
        val hex = hexRaw.replace("\\s".toRegex(), "")
        if (hex.isEmpty()) return ""
        val out = StringBuilder()
        if (hex.length % 4 == 0) {
            var i = 0
            while (i < hex.length) {
                val code = hex.substring(i, i + 4).toInt(16)
                if (code != 0) out.append(code.toChar())
                i += 4
            }
        } else {
            var i = 0
            while (i + 1 < hex.length) {
                out.append(hex.substring(i, i + 2).toInt(16).toChar())
                i += 2
            }
        }
        return out.toString()
    }

    private fun extractTextFromContent(content: String, maps: List<Map<Int, String>>): String {
        val out = StringBuilder()
        val line = StringBuilder()

        // Tokenize important operators while scanning the stream
        val tokenRegex = Regex(
            """/(?:[A-Za-z0-9#]+)\s+[\d.]+\s+Tf|""" +
                """\[(?:[^\]]*)\]\s*TJ|""" +
                """(?:\((?:\\.|[^\\)])*\)|<([0-9A-Fa-f\s]+)>)\s*(?:Tj|')|""" +
                """([-+]?\d*\.?\d+)\s+([-+]?\d*\.?\d+)\s+Td|""" +
                """T\*|BT|ET"""
        )

        for (match in tokenRegex.findAll(content)) {
            val token = match.value.trim()
            when {
                token == "ET" || token == "T*" || token == "BT" -> {
                    flushLine(out, line)
                }
                token.endsWith("Td") -> {
                    val parts = token.split(Regex("""\s+"""))
                    if (parts.size >= 3) {
                        val tx = parts[0].toDoubleOrNull() ?: 0.0
                        val ty = parts[1].toDoubleOrNull() ?: 0.0
                        when {
                            kotlin.math.abs(ty) > 3.0 -> flushLine(out, line)
                            ty == 0.0 && kotlin.math.abs(tx) > 2.5 && line.isNotEmpty() -> line.append(' ')
                        }
                    }
                }
                token.endsWith("TJ") -> {
                    val inside = token.removeSuffix("TJ").trim().removePrefix("[").removeSuffix("]")
                    // Hex CID strings and literal strings inside TJ array
                    val pieces = Regex("""\((?:\\.|[^\\)])*\)|<([0-9A-Fa-f\s]+)>""").findAll(inside)
                    for (p in pieces) {
                        line.append(decodeTextOperand(p.value, maps))
                    }
                }
                token.endsWith("Tj") || token.endsWith("'") -> {
                    val operand = token.replace(Regex("""\s*(?:Tj|')$"""), "")
                    line.append(decodeTextOperand(operand, maps))
                    if (token.endsWith("'")) flushLine(out, line)
                }
            }
        }
        flushLine(out, line)
        return out.toString().trim()
    }

    private fun flushLine(out: StringBuilder, line: StringBuilder) {
        if (line.isNotEmpty()) {
            out.append(line.toString().trim()).append('\n')
            line.clear()
        }
    }

    private fun decodeTextOperand(operand: String, maps: List<Map<Int, String>>): String {
        val trimmed = operand.trim()
        return when {
            trimmed.startsWith("<") && trimmed.endsWith(">") -> {
                val hex = trimmed.substring(1, trimmed.length - 1).replace("\\s".toRegex(), "")
                decodeHexWithBestMap(hex, maps)
            }
            trimmed.startsWith("(") && trimmed.endsWith(")") -> {
                val raw = cleanPdfString(trimmed.substring(1, trimmed.length - 1))
                if (maps.isEmpty()) raw else decodeBytesWithBestMap(raw, maps)
            }
            else -> ""
        }
    }

    private fun decodeHexWithBestMap(hex: String, maps: List<Map<Int, String>>): String {
        if (hex.isEmpty()) return ""
        if (maps.isEmpty()) {
            // Fallback: treat as UTF-16BE / latin1
            return if (hex.length % 4 == 0) decodeUtf16BeHex(hex) else {
                buildString {
                    var i = 0
                    while (i + 1 < hex.length) {
                        append(hex.substring(i, i + 2).toInt(16).toChar())
                        i += 2
                    }
                }
            }
        }

        var best = ""
        var bestScore = Int.MIN_VALUE
        for (map in maps) {
            val candidate = decodeHexWithMap(hex, map)
            val score = scoreReadable(candidate)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun decodeHexWithMap(hex: String, map: Map<Int, String>): String {
        val out = StringBuilder()
        val step = when {
            hex.length >= 4 && hex.length % 4 == 0 -> 4
            else -> 2
        }
        // Prefer 4-byte CIDs when map keys look like 2-byte values
        val useStep = if (step == 4) {
            val probe = hex.substring(0, 4).toInt(16)
            if (map.containsKey(probe) || map.keys.any { it > 255 }) 4 else 2
        } else step

        var i = 0
        while (i + useStep <= hex.length) {
            val cid = hex.substring(i, i + useStep).toInt(16)
            val mapped = map[cid] ?: if (useStep == 4) map[cid and 0xFF] else null
            if (mapped != null) out.append(mapped)
            i += useStep
        }
        return out.toString()
    }

    private fun decodeBytesWithBestMap(raw: String, maps: List<Map<Int, String>>): String {
        var best = raw
        var bestScore = scoreReadable(raw)
        for (map in maps) {
            val candidate = buildString {
                for (ch in raw) {
                    val cid = ch.code
                    append(map[cid] ?: ch)
                }
            }
            val score = scoreReadable(candidate)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun scoreReadable(text: String): Int {
        var score = 0
        for (ch in text) {
            val c = ch.code
            score += when {
                c in 65..90 || c in 97..122 || c in 48..57 -> 3
                ch == '₹' || ch == '.' || ch == ',' || ch == ' ' || ch == '-' || ch == ':' || ch == '/' -> 1
                c < 32 -> -5
                else -> -1
            }
        }
        return score
    }

    private fun normalizeExtractedText(text: String): String {
        // Collapse runs of spaces/tabs; keep newlines
        var cleaned = text
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        // Skia letter-spacing often inserts a space between every glyph in headers.
        // Collapse patterns like "T r a n s" -> "Trans" only for runs of single letters.
        cleaned = collapseSkiaLetterSpacing(cleaned)
        return cleaned
    }

    private fun collapseSkiaLetterSpacing(text: String): String {
        val lines = text.lines()
        return lines.joinToString("\n") { line ->
            // Only collapse when >50% of tokens are single letters (header-like)
            val tokens = line.trim().split(' ')
            if (tokens.size >= 4) {
                val singleLetter = tokens.count { it.length == 1 && it[0].isLetter() }
                if (singleLetter * 2 >= tokens.size) {
                    tokens.joinToString("") { tok ->
                        when {
                            tok.length == 1 -> tok
                            tok.all { it.isDigit() } -> " $tok"
                            else -> " $tok "
                        }
                    }.replace(Regex("""\s+"""), " ").trim()
                } else line.trim()
            } else line.trim()
        }
    }

    private fun decompressFlate(data: ByteArray): String? {
        // Try zlib-wrapped then raw deflate
        for (nowrap in listOf(false, true)) {
            try {
                val inflater = Inflater(nowrap)
                val bais = ByteArrayInputStream(data)
                val iis = InflaterInputStream(bais, inflater)
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                while (iis.read(buffer).also { read = it } != -1) {
                    baos.write(buffer, 0, read)
                }
                iis.close()
                return baos.toString(Charsets.ISO_8859_1.name())
            } catch (_: Exception) {
                // try next mode
            }
        }
        // Skip 2-byte zlib header manually with raw inflater
        if (data.size > 2) {
            try {
                val inflater = Inflater(true)
                inflater.setInput(data, 2, data.size - 2)
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) break
                    baos.write(buffer, 0, count)
                }
                inflater.end()
                if (baos.size() > 0) return baos.toString(Charsets.ISO_8859_1.name())
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun cleanPdfString(raw: String): String {
        return raw
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    private fun extractLiteralStrings(bytes: ByteArray): String {
        val rawStr = String(bytes, Charsets.ISO_8859_1)
        val regex = Regex("""\(([\\w\\s.,\\-₹+/@:]{3,100})\)""")
        val found = regex.findAll(rawStr).map { it.groupValues[1].trim() }.filter { it.length > 2 }
        return found.joinToString("\n")
    }

    private fun indexOf(data: ByteArray, target: ByteArray, start: Int): Int {
        outer@ for (i in start..(data.size - target.size)) {
            for (j in target.indices) {
                if (data[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun skipNewline(data: ByteArray, start: Int): Int {
        var idx = start
        while (idx < data.size) {
            val b = data[idx].toInt().and(0xFF)
            if (b == '\r'.code || b == '\n'.code || b == ' '.code) idx++ else break
        }
        return idx
    }
}
