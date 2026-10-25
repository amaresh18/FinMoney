package com.example.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.FinMoneyDao
import com.example.data.model.CloudSyncOutboxEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-first durability, same pattern as WhatsApp / CRED / INDmoney:
 * 1. Room is the source of truth and is written first.
 * 2. Cloud payloads are queued immediately.
 * 3. A background flusher retries later with backoff. UI and notification
 *    actions never wait on Firestore.
 */
object CloudSyncOutboxManager {

    private const val TAG = "CloudSyncOutbox"
    private const val MAX_ATTEMPTS = 25
    private const val ITEM_TIMEOUT_MS = 8_000L
    private val cloudRepository = UserCloudDataRepository()
    private val flushMutex = Mutex()
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueue(
        dao: FinMoneyDao,
        cloudUser: CloudUserContext,
        subcollection: String,
        documentId: String,
        data: Map<String, Any?>,
        legacyCollections: List<String>,
        error: String? = null
    ) {
        val uid = cloudUser.userId
        if (uid.isBlank()) return
        try {
            val entry = CloudSyncOutboxEntry(
                userId = uid,
                subcollection = subcollection,
                documentId = documentId,
                payloadJson = mapToJson(data),
                legacyCollectionsJson = JSONArray(legacyCollections).toString(),
                attempts = 0,
                lastError = error,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dao.deleteCloudSyncOutboxEntry(uid, subcollection, documentId)
            dao.insertCloudSyncOutbox(entry)
            Log.i(TAG, "Queued cloud sync: $subcollection/$documentId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue cloud sync: ${e.message}", e)
        }
    }

    /**
     * Always queue locally first, then kick a background flush. Never blocks on the network.
     */
    suspend fun enqueueAndSchedule(
        context: Context,
        cloudUser: CloudUserContext,
        subcollection: String,
        documentId: String,
        data: Map<String, Any?>,
        legacyCollections: List<String> = emptyList()
    ) {
        val dao = AppDatabase.getDatabase(context.applicationContext).finMoneyDao()
        enqueue(
            dao = dao,
            cloudUser = cloudUser,
            subcollection = subcollection,
            documentId = documentId,
            data = data,
            legacyCollections = legacyCollections,
            error = if (cloudUser.canSyncToCloud) null else "Waiting for signed-in Firebase user"
        )
        scheduleFlush(context.applicationContext)
    }

    /** Kept for callers; same local-first contract as [enqueueAndSchedule]. */
    suspend fun syncToCloudOrQueue(
        context: Context,
        cloudUser: CloudUserContext,
        subcollection: String,
        documentId: String,
        data: Map<String, Any?>,
        legacyCollections: List<String>
    ) {
        enqueueAndSchedule(context, cloudUser, subcollection, documentId, data, legacyCollections)
    }

    fun scheduleFlush(context: Context) {
        val appContext = context.applicationContext
        flushScope.launch {
            delay(250)
            try {
                val dao = AppDatabase.getDatabase(appContext).finMoneyDao()
                flushPending(appContext, dao)
            } catch (e: Exception) {
                Log.w(TAG, "Background flush failed: ${e.message}")
            }
        }
    }

    suspend fun flushPending(context: Context, dao: FinMoneyDao): Int {
        return flushMutex.withLock {
            val cloudUser = CloudUserContextResolver.resolve(dao)
            if (!cloudUser.canSyncToCloud) return@withLock 0
            val pending = dao.getCloudSyncOutboxForUser(cloudUser.userId)
            if (pending.isEmpty()) return@withLock 0
            var successCount = 0
            val now = System.currentTimeMillis()
            for (item in pending) {
                if (item.attempts >= MAX_ATTEMPTS) continue
                if (now < item.updatedAt + backoffMs(item.attempts)) continue
                val payload = jsonToMap(item.payloadJson)
                val legacy = parseLegacyCollections(item.legacyCollectionsJson)
                val result = withTimeoutOrNull(ITEM_TIMEOUT_MS) {
                    cloudRepository.writeUserDocument(
                        context = cloudUser,
                        subcollection = item.subcollection,
                        documentId = item.documentId,
                        data = payload,
                        legacyCollections = legacy
                    )
                }
                if (result?.isSuccess == true) {
                    dao.deleteCloudSyncOutboxById(item.id)
                    successCount++
                } else {
                    dao.updateCloudSyncOutboxAttempt(
                        id = item.id,
                        attempts = item.attempts + 1,
                        error = result?.exceptionOrNull()?.message ?: "timeout or offline",
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            if (successCount > 0) {
                Log.i(TAG, "Flushed $successCount pending cloud sync item(s) for ${cloudUser.userId}")
            }
            successCount
        }
    }

    private fun backoffMs(attempts: Int): Long {
        val seconds = when {
            attempts <= 0 -> 0L
            attempts == 1 -> 5L
            attempts == 2 -> 30L
            attempts == 3 -> 120L
            else -> 600L
        }
        return seconds * 1000L
    }

    private fun mapToJson(data: Map<String, Any?>): String {
        val json = JSONObject()
        data.forEach { (key, value) ->
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is Map<*, *> -> json.put(key, JSONObject(value as Map<*, *>))
                is List<*> -> json.put(key, JSONArray(value))
                else -> json.put(key, value)
            }
        }
        return json.toString()
    }

    private fun jsonToMap(json: String): Map<String, Any?> {
        val obj = JSONObject(json)
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { key ->
            map[key] = when (val v = obj.get(key)) {
                JSONObject.NULL -> null
                else -> v
            }
        }
        return map
    }

    private fun parseLegacyCollections(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
