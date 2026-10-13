package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GroqApiClient"
        private const val PREFS_NAME = "groq_ai_prefs"
        private const val KEY_CUSTOM_GROQ_API_KEY = "custom_groq_api_key"
        private const val KEY_SELECTED_MODEL = "selected_groq_model"

        const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
        const val GROQ_CHAT_ENDPOINT = "$GROQ_BASE_URL/chat/completions"
        const val GROQ_MODELS_ENDPOINT = "$GROQ_BASE_URL/models"
        const val GROQ_ENDPOINT = GROQ_CHAT_ENDPOINT

        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        const val FALLBACK_MODEL = "llama-3.3-70b-versatile"

        // Official active text chat completion models on Groq (https://console.groq.com/docs/models)
        val ALLOWED_CHAT_MODELS = setOf(
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-20b"
        )

        // Decommissioned or non-chat models (Audio/TTS/Guardrails) on Groq
        val DECOMMISSIONED_MODELS = setOf(
            "llama3-70b-8192",
            "llama3-8b-8192",
            "llama3-70b-8192-tool-use-preview",
            "llama3-8b-8192-tool-use-preview",
            "deepseek-r1-distill-qwen-32b",
            "deepseek-r1-distill-llama-70b",
            "qwen-2.5-32b",
            "qwen-2.5-coder-32b",
            "mistral-saba-24b",
            "llama-3.2-3b-preview",
            "llama-3.2-1b-preview",
            "llama-3.2-11b-vision-preview",
            "llama-3.2-90b-vision-preview",
            "mixtral-8x7b-32768",
            "gemma2-9b-it",
            "gemma-7b-it",
            "llama2-70b-4096",
            "whisper-large-v3",
            "whisper-large-v3-turbo",
            "distil-whisper-large-v3-en",
            "canopylabs/orpheus-v1-english",
            "canopylabs/orpheus-arabic-saudi",
            "openai/gpt-oss-safeguard-20b"
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getApiKey(): String {
        val userKey = prefs.getString(KEY_CUSTOM_GROQ_API_KEY, "")?.trim() ?: ""
        if (userKey.isNotBlank()) return userKey

        // Fallback to BuildConfig if present and not default placeholder
        val buildKey = try {
            val key = BuildConfig::class.java.getField("GROQ_API_KEY").get(null) as? String
            if (key != null && key.isNotBlank() && key != "default_groq_key") key else ""
        } catch (_: Exception) {
            ""
        }
        return buildKey
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_CUSTOM_GROQ_API_KEY, apiKey.trim()).apply()
    }

    fun getSelectedModel(): String {
        val saved = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        if (saved in DECOMMISSIONED_MODELS || saved !in ALLOWED_CHAT_MODELS) {
            setSelectedModel(DEFAULT_MODEL)
            return DEFAULT_MODEL
        }
        return saved
    }

    fun setSelectedModel(model: String) {
        if (model in ALLOWED_CHAT_MODELS) {
            prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        } else {
            prefs.edit().putString(KEY_SELECTED_MODEL, DEFAULT_MODEL).apply()
        }
    }

    /**
     * Dynamically queries Groq's GET /openai/v1/models endpoint to retrieve
     * all currently active and available text chat models for the user's API key.
     */
    suspend fun fetchAvailableModelsFromGroq(): List<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url(GROQ_MODELS_ENDPOINT)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: return@withContext emptyList()

                val modelList = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val active = item.optBoolean("active", true)
                    // Strictly match allowed text chat models
                    if (id.isNotBlank() && active && id in ALLOWED_CHAT_MODELS) {
                        modelList.add(id)
                    }
                }
                return@withContext modelList
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch dynamic Groq model list: ${e.message}")
            emptyList()
        }
    }

    suspend fun queryGroq(
        userMessage: String,
        systemPrompt: String = KuberaPersona.SYSTEM_PROMPT,
        conversationHistory: List<GroqMessage> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Groq API Key is not configured. Please enter your free Groq API key from console.groq.com to awaken Lord Kubera.")
            )
        }

        val primaryModel = getSelectedModel()
        val dynamicModels = fetchAvailableModelsFromGroq()

        // Models ordered according to official Groq docs: https://console.groq.com/docs/models
        val fallbackModels = listOf(
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "openai/gpt-oss-20b"
        )

        val modelsToTry = (listOf(primaryModel) + dynamicModels + fallbackModels)
            .filter { id -> id.isNotBlank() && id in ALLOWED_CHAT_MODELS }
            .distinct()

        var lastError: Exception? = null

        for (modelName in modelsToTry) {
            try {
                val messagesArray = JSONArray()
                // System message
                if (systemPrompt.isNotBlank()) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt.trim())
                    })
                }

                // Recent conversation context (last 6 messages max)
                conversationHistory
                    .filter { it.content.isNotBlank() }
                    .takeLast(6)
                    .forEach { msg ->
                        messagesArray.put(JSONObject().apply {
                            put("role", if (msg.role == "user") "user" else "assistant")
                            put("content", msg.content.trim().take(1000))
                        })
                    }

                // Current user message
                if (userMessage.isNotBlank()) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage.trim())
                    })
                }

                val requestPayload = JSONObject().apply {
                    put("model", modelName)
                    put("messages", messagesArray)
                    put("temperature", 0.6)
                    put("max_tokens", 1024)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = requestPayload.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(GROQ_CHAT_ENDPOINT)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val parsedError = parseGroqErrorDetails(responseBody)
                        val detailedError = formatGroqErrorMessage(response.code, parsedError)

                        if (response.code == 400) {
                            Log.e(
                                TAG,
                                """
                                |🚨 [GROQ API 400 BAD REQUEST PAYLOAD CAPTURED]
                                |• Attempted Model  : $modelName
                                |• Base URL         : $GROQ_BASE_URL
                                |• Chat Endpoint    : $GROQ_CHAT_ENDPOINT
                                |• HTTP Status      : 400 Bad Request
                                |• Error Type       : ${parsedError.type ?: "unknown"}
                                |• Error Code       : ${parsedError.code ?: "unknown"}
                                |• Error Param      : ${parsedError.param ?: "none"}
                                |• Error Message    : ${parsedError.message}
                                |• Request Meta     : messages_count=${messagesArray.length()}, max_tokens=1024, temperature=0.6
                                |• Full Raw Payload : $responseBody
                                """.trimMargin()
                            )
                        } else {
                            Log.w(
                                TAG,
                                "Groq call failed for model '$modelName' [HTTP ${response.code}]: $detailedError (Raw: $responseBody)"
                            )
                        }
                        throw RuntimeException(detailedError)
                    }

                    val jsonResp = JSONObject(responseBody)
                    val choices = jsonResp.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        val content = messageObj?.optString("content", "") ?: ""
                        if (content.isNotBlank()) {
                            return@withContext Result.success(content)
                        }
                    }
                    val emptyErr = "Empty content received from Groq AI model ($modelName)."
                    Log.w(TAG, emptyErr)
                    throw RuntimeException(emptyErr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Attempt failed with model '$modelName': ${e.message}")
                lastError = e
                // Try next fallback model if available
            }
        }

        val finalError = lastError ?: RuntimeException("Unable to contact Groq API.")
        Log.e(TAG, "All Groq model attempts failed: ${finalError.message}")
        Result.failure(finalError)
    }

    data class GroqErrorDetails(
        val message: String,
        val type: String?,
        val code: String?,
        val param: String?,
        val rawBody: String
    )

    private fun parseGroqErrorDetails(bodyStr: String): GroqErrorDetails {
        return try {
            if (bodyStr.isNotBlank()) {
                val json = JSONObject(bodyStr)
                val errObj = json.optJSONObject("error")
                if (errObj != null) {
                    GroqErrorDetails(
                        message = errObj.optString("message", bodyStr).trim(),
                        type = errObj.optString("type").takeIf { it.isNotBlank() },
                        code = errObj.optString("code").takeIf { it.isNotBlank() },
                        param = errObj.optString("param").takeIf { it.isNotBlank() },
                        rawBody = bodyStr
                    )
                } else {
                    GroqErrorDetails(
                        message = json.optString("message", bodyStr).trim(),
                        type = json.optString("type").takeIf { it.isNotBlank() },
                        code = json.optString("code").takeIf { it.isNotBlank() },
                        param = json.optString("param").takeIf { it.isNotBlank() },
                        rawBody = bodyStr
                    )
                }
            } else {
                GroqErrorDetails("Empty error response body", null, null, null, "")
            }
        } catch (_: Exception) {
            GroqErrorDetails(bodyStr, null, null, null, bodyStr)
        }
    }

    private fun formatGroqErrorMessage(httpCode: Int, err: GroqErrorDetails): String {
        val extraInfo = listOfNotNull(
            err.type?.let { "type=$it" },
            err.code?.let { "code=$it" },
            err.param?.let { "param=$it" }
        ).joinToString(", ")
        val tagSuffix = if (extraInfo.isNotBlank()) " [$extraInfo]" else ""

        return when (httpCode) {
            400 -> "Unsupported Payload / Bad Request (Code 400)$tagSuffix. Reason: ${err.message.ifBlank { "Invalid model or request parameters." }}"
            401, 403 -> "Invalid or unauthorized Groq API Key (Code $httpCode)$tagSuffix. Reason: ${err.message.ifBlank { "Authentication failed. Verify key at console.groq.com." }}"
            404 -> "Model Not Found / Unavailable (Code $httpCode)$tagSuffix. Reason: ${err.message.ifBlank { "Model does not exist or access is restricted." }}"
            413, 429 -> "Rate Limit Exceeded (Code $httpCode)$tagSuffix. Reason: ${err.message.ifBlank { "Request exceeds model token limit or quota." }}"
            500, 502, 503, 504 -> "Groq Server Error (Code $httpCode)$tagSuffix. Reason: ${err.message.ifBlank { "Groq service temporarily unavailable." }}"
            else -> "Groq Error (Code $httpCode)$tagSuffix: ${err.message.ifBlank { "Request failed." }}"
        }
    }

    /**
     * Synthesizes high-fidelity offline Kubera wisdom directly from the structured JSON
     * in the event of no internet or unconfigured API key.
     */
    fun synthesizeOfflineKuberaAdvice(
        mode: KuberaAuditMode,
        contextJson: String
    ): String {
        return try {
            val root = JSONObject(contextJson)
            val incomeLedger = root.optJSONObject("incomeLedger")
            val expenseLedger = root.optJSONObject("expenseLedger")
            val recurring = root.optJSONObject("recurringObligations")
            val debtLedger = root.optJSONObject("debtLedger")
            val surplus = root.optJSONObject("surplusAndSafeToSpend")

            val totalInflow = incomeLedger?.optDouble("totalMonthlyInflow", 0.0) ?: 0.0
            val totalExpense = expenseLedger?.optDouble("totalExpenseBudget", 0.0) ?: 0.0
            val netSurplus = surplus?.optDouble("netFlexibleCashSurplus", 0.0) ?: 0.0
            val safeSpend = surplus?.optString("dailySafeToSpendAllowance", "₹0.00/day") ?: "₹0.00/day"
            val totalDebt = debtLedger?.optDouble("totalLiabilitiesToPayOff", 0.0) ?: 0.0
            val totalLent = debtLedger?.optDouble("totalAssetsToRecover", 0.0) ?: 0.0
            val currency = root.optString("currency", "₹")

            when (mode) {
                KuberaAuditMode.FULL_WEALTH_AUDIT -> """
### 👑 Wealth & Financial Health Audit
*Your Live Financial Summary*

**1. Cash Flow Overview:**
- **Monthly Income:** $currency${"%,.2f".format(totalInflow)}
- **Monthly Expenses & Bills:** $currency${"%,.2f".format(totalExpense)}
- **Remaining Surplus:** $currency${"%,.2f".format(netSurplus)}
- **Status:** ${if (netSurplus > 0) "Great job! You have money left over this month." else "Warning: Your expenses are higher than your income. Let's look at cutting unnecessary costs."}

**2. Debt & Borrowing Summary:**
- **Total Borrowings:** $currency${"%,.2f".format(totalDebt)}
- **Money Owed to You:** $currency${"%,.2f".format(totalLent)}
- **Tip:** Focus on paying off high-interest loans first before spending on non-essential items.

**3. Daily Safe-to-Spend Budget:**
- **Daily Allowance:** **$safeSpend**
- Try to stay within this daily limit so you don't overspend before your next income arrives.

**4. Action Steps to Grow Wealth:**
1. **Emergency Fund:** Keep 3 to 6 months of living expenses in a safe, easily accessible savings or liquid account.
2. **Automated Savings:** Set up automatic savings or investments at the start of every month.
3. **Track Expenses:** Review your daily spending regularly to avoid small impulse purchases.

*(Note: This guidance is for financial education. Consult a certified financial planner for major financial decisions.)*
""".trimIndent()

                KuberaAuditMode.DEBT_LIQUIDATION -> """
### ⚔️ Debt Payoff Masterplan
*Step-by-Step Plan to Clear Your Loans*

**1. Your Debt Summary:**
- **Total Loans / Borrowings:** **$currency${"%,.2f".format(totalDebt)}**
- **Monthly EMIs & Bills:** **$currency${"%,.2f".format(recurring?.optDouble("totalMonthlyRecurringBurden", 0.0) ?: 0.0)}/mo**

**2. Recommended Strategy:**
- ${if (totalDebt > 50000) "**Pay Highest Interest First:** Focus extra payments on the loan with the highest interest rate to save the most money over time." else "**Pay Smallest Balance First:** Pay off your smallest loan first for quick motivation and one less monthly bill."}

**3. Collecting Money Owed:**
- You have **$currency${"%,.2f".format(totalLent)}** lent to others. Politely remind them to return the funds so you can use them to clear your own debts.

*(Note: Review repayment terms with your bank or lender before prepaying loans.)*
""".trimIndent()

                KuberaAuditMode.LEAK_PATROL -> """
### 🛡️ Spending Leak Patrol & Budget Check
*How to Spot and Stop Unnecessary Spending*

**1. Your Spending Limits:**
- **Monthly Expenses:** $currency${"%,.2f".format(totalExpense)}
- **Safe Daily Spending Limit:** **$safeSpend**

**2. Simple Tips to Save More:**
- **Daily Cap:** Try not to spend more than **$safeSpend** per day on non-essential items.
- **Review Subscriptions:** Cancel any apps, streaming services, or memberships you haven't used in the last month.
- **Dining & Shopping:** Plan your meals and shopping lists in advance to avoid impulse buying.

*Tip: Save first, spend what is left — not the other way around!*
""".trimIndent()

                KuberaAuditMode.WEALTH_COMPOUNDING -> """
### 📈 Wealth Growth & Investing Guide
*Turning Extra Cash Into Long-Term Savings*

**1. Available Savings:**
- **Monthly Extra Cash:** **$currency${"%,.2f".format(netSurplus)}**

**2. Simple 3-Step Investing Plan:**
1. **Emergency Fund:** Ensure you have 3 to 6 months of living expenses saved in a liquid account.
2. **Diversified Investments:** Put your monthly savings into safe, low-cost index funds or fixed-income options.
3. **Consistency:** Invest a fixed amount every month automatically so your money grows steadily over time.

*(Note: Consult a licensed financial planner for personalized investment advice.)*
""".trimIndent()
            }
        } catch (_: Exception) {
            "Lord Kubera bids you welcome. Please examine your financial ledger and ensure your income and expenses are logged accurately."
        }
    }
}
