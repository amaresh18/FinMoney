package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ProofFile(
    val id: String = "",
    val name: String = "",
    val localPath: String = "",
    val uri: String = "",
    val sizeBytes: Long = 0L,
    val mimeType: String = "image/jpeg",
    val cloudBase64: String = "",
    val isPdf: Boolean = mimeType.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
) {
    fun ensureLocalPath(context: Context): String {
        return if (localPath.isNotBlank()) localPath else uri
    }
}

object ProofStorageHelper {

    fun parseProofFiles(json: String): List<ProofFile> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<ProofFile>()
        try {
            val regex = Regex("""\{"name":"(.*?)","localPath":"(.*?)","uri":"(.*?)","sizeBytes":(\d+),"mimeType":"(.*?)"(?:,"cloudBase64":"(.*?)")?\}""")
            val matches = regex.findAll(json)
            for (m in matches) {
                val name = m.groupValues[1].replace("\\\"", "\"")
                val mime = m.groupValues[5]
                val b64 = if (m.groupValues.size > 6) m.groupValues[6] else ""
                val isPdf = mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
                list.add(
                    ProofFile(
                        name = name,
                        localPath = m.groupValues[2],
                        uri = m.groupValues[3],
                        sizeBytes = m.groupValues[4].toLongOrNull() ?: 0L,
                        mimeType = mime,
                        cloudBase64 = b64,
                        isPdf = isPdf
                    )
                )
            }
            if (list.isEmpty() && json.isNotBlank()) {
                val fileName = json.substringAfterLast("/").ifBlank { "proof_attachment" }
                val isPdf = json.endsWith(".pdf", ignoreCase = true)
                list.add(ProofFile(name = fileName, localPath = json, uri = json, mimeType = if (isPdf) "application/pdf" else "image/jpeg", isPdf = isPdf))
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return list
    }

    fun encodeProofFiles(files: List<ProofFile>): String {
        if (files.isEmpty()) return ""
        return files.joinToString(prefix = "[", postfix = "]") {
            """{"name":"${it.name.replace("\"", "\\\"")}","localPath":"${it.localPath}","uri":"${it.uri}","sizeBytes":${it.sizeBytes},"mimeType":"${it.mimeType}","cloudBase64":"${it.cloudBase64}"}"""
        }
    }

    fun copyUriToLocalStorage(context: Context, uri: Uri): String {
        return try {
            val contentResolver = context.contentResolver
            val ext = contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
            val file = File(context.filesDir, "proof_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            uri.toString()
        }
    }

    fun persistProofImage(context: Context, uri: Uri): String {
        return copyUriToLocalStorage(context, uri)
    }

    fun persistFileFromUri(context: Context, uri: Uri): ProofFile {
        val path = copyUriToLocalStorage(context, uri)
        val name = uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val isPdf = mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
        val file = File(path)
        val size = if (file.exists()) file.length() else 0L
        return ProofFile(
            name = name,
            localPath = path,
            uri = uri.toString(),
            sizeBytes = size,
            mimeType = mime,
            isPdf = isPdf
        )
    }

    fun ensureLocalPath(context: Context, file: ProofFile): String = file.ensureLocalPath(context)
    fun ensureLocalPath(context: Context, pathOrUri: String): String = pathOrUri

    fun openInExternalViewer(context: Context, file: ProofFile) {
        try {
            val path = file.ensureLocalPath(context)
            val localFile = File(path)
            val uri = if (localFile.exists()) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
            } else {
                Uri.parse(file.uri)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, file.mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Viewer not available
        }
    }

    fun saveToGallery(context: Context, file: ProofFile): Boolean {
        return try {
            val localPath = file.ensureLocalPath(context)
            val src = File(localPath)
            if (src.exists()) {
                val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "FinMoney")
                if (!targetDir.exists()) targetDir.mkdirs()
                val targetFile = File(targetDir, file.name.ifBlank { "proof_${System.currentTimeMillis()}.${if (file.isPdf) "pdf" else "jpg"}" })
                src.copyTo(targetFile, overwrite = true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun uploadProofToCloud(context: Context, localPath: String): String {
        return localPath
    }
}
