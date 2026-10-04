package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class ProofFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Proof Document",
    val mimeType: String = "image/jpeg",
    val localPath: String = "",
    val cloudBase64: String = "",
    val sizeBytes: Long = 0L,
    val uploadedAt: Long = System.currentTimeMillis()
) {
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true) || !isPdf
}

object ProofStorageHelper {

    fun parseProofFiles(raw: String): List<ProofFile> {
        if (raw.isBlank()) return emptyList()
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return try {
                val array = JSONArray(trimmed)
                val list = mutableListOf<ProofFile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ProofFile(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Document Proof"),
                            mimeType = obj.optString("mimeType", "image/jpeg"),
                            localPath = obj.optString("localPath", ""),
                            cloudBase64 = obj.optString("cloudBase64", ""),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            uploadedAt = obj.optLong("uploadedAt", System.currentTimeMillis())
                        )
                    )
                }
                list
            } catch (e: Exception) {
                listOf(ProofFile(name = "Legacy Proof", localPath = trimmed, cloudBase64 = if (trimmed.startsWith("data:")) trimmed else ""))
            }
        } else {
            // Legacy single URI or URL
            return listOf(
                ProofFile(
                    id = UUID.randomUUID().toString(),
                    name = "Attached Proof",
                    mimeType = if (trimmed.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "image/jpeg",
                    localPath = trimmed,
                    cloudBase64 = if (trimmed.startsWith("data:")) trimmed else ""
                )
            )
        }
    }

    fun encodeProofFiles(files: List<ProofFile>): String {
        if (files.isEmpty()) return ""
        val array = JSONArray()
        for (f in files) {
            val obj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("mimeType", f.mimeType)
                put("localPath", f.localPath)
                put("cloudBase64", f.cloudBase64)
                put("sizeBytes", f.sizeBytes)
                put("uploadedAt", f.uploadedAt)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun persistFileFromUri(context: Context, sourceUri: Uri): ProofFile {
        val resolver = context.contentResolver
        var displayName = "proof_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        // Resolve display name & size
        try {
            resolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        val mimeType = resolver.getType(sourceUri) ?: if (displayName.endsWith(".pdf", ignoreCase = true)) {
            "application/pdf"
        } else {
            "image/jpeg"
        }

        val extension = when {
            mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }

        val proofsDir = File(context.filesDir, "proofs").apply { mkdirs() }
        val targetFile = File(proofsDir, "proof_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")

        // Copy stream locally
        resolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        var base64Payload = ""

        if (mimeType.startsWith("image/", ignoreCase = true)) {
            // Read and compress image for cross-device cloud sync
            try {
                val bitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
                if (bitmap != null) {
                    val maxDimension = 1024
                    val width = bitmap.width
                    val height = bitmap.height
                    val scale = if (width > maxDimension || height > maxDimension) {
                        maxDimension.toFloat() / maxOf(width, height)
                    } else 1.0f

                    val scaledBitmap = if (scale < 1.0f) {
                        Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                    } else bitmap

                    val bos = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 78, bos)
                    val bytes = bos.toByteArray()
                    base64Payload = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // For PDFs or other docs, read up to 600KB into base64 payload
            try {
                if (targetFile.length() in 1..650000) {
                    val bytes = targetFile.readBytes()
                    base64Payload = "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return ProofFile(
            name = displayName,
            mimeType = mimeType,
            localPath = targetFile.absolutePath,
            cloudBase64 = base64Payload,
            sizeBytes = targetFile.length()
        )
    }

    fun ensureLocalPath(context: Context, pathOrJson: String): String {
        val files = parseProofFiles(pathOrJson)
        if (files.isNotEmpty()) {
            return ensureLocalPath(context, files.first())
        }
        val file = ProofFile(localPath = pathOrJson, cloudBase64 = if (pathOrJson.startsWith("data:")) pathOrJson else "")
        return ensureLocalPath(context, file)
    }

    /**
     * Ensures the file is accessible locally on the current device.
     * If localPath is missing (e.g. on counterparty phone), decodes cloudBase64 into local storage.
     */
    fun ensureLocalPath(context: Context, file: ProofFile): String {
        if (file.localPath.isNotBlank()) {
            val local = File(file.localPath)
            if (local.exists() && local.length() > 0) {
                return local.absolutePath
            }
        }

        if (file.cloudBase64.isNotBlank() && file.cloudBase64.contains("base64,")) {
            try {
                val pureBase64 = file.cloudBase64.substringAfter("base64,")
                val decodedBytes = Base64.decode(pureBase64, Base64.DEFAULT)
                val ext = if (file.isPdf) "pdf" else "jpg"
                val proofsDir = File(context.filesDir, "proofs").apply { mkdirs() }
                val targetFile = File(proofsDir, "synced_${file.id.take(8)}.$ext")
                FileOutputStream(targetFile).use { it.write(decodedBytes) }
                return targetFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return file.localPath
    }

    fun getShareableUri(context: Context, pathOrUri: String): Uri? {
        return try {
            if (pathOrUri.startsWith("/")) {
                val file = File(pathOrUri)
                if (file.exists()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                } else null
            } else {
                Uri.parse(pathOrUri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveToGallery(context: Context, file: ProofFile): Boolean {
        return try {
            val localPath = ensureLocalPath(context, file)
            val sourceFile = File(localPath)
            if (!sourceFile.exists()) return false

            val filename = "FinMoney_${System.currentTimeMillis()}_${file.name.filter { it.isLetterOrDigit() || it == '.' }}"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FinMoney")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = if (file.isImage) {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } else {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } ?: return false

            resolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(targetUri, values, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun persistProofImage(context: Context, sourceUri: Uri): String {
        return persistFileFromUri(context, sourceUri).localPath
    }

    fun saveImageToGallery(context: Context, pathOrUri: String): Boolean {
        val files = parseProofFiles(pathOrUri)
        if (files.isNotEmpty()) {
            return saveToGallery(context, files.first())
        }
        val file = ProofFile(localPath = pathOrUri)
        return saveToGallery(context, file)
    }

    fun openInExternalViewer(context: Context, pathOrUri: String) {
        val files = parseProofFiles(pathOrUri)
        if (files.isNotEmpty()) {
            openInExternalViewer(context, files.first())
        } else {
            val file = ProofFile(localPath = pathOrUri)
            openInExternalViewer(context, file)
        }
    }

    fun openInExternalViewer(context: Context, file: ProofFile) {
        val localPath = ensureLocalPath(context, file)
        val uri = getShareableUri(context, localPath) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open ${file.name}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
