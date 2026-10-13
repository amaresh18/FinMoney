package com.example.ai

import java.util.UUID

data class KuberaMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: KuberaSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String? = null,
    val isLoading: Boolean = false
)

enum class KuberaSender {
    USER,
    KUBERA
}

enum class KuberaAuditMode(val title: String, val subtitle: String, val icon: String) {
    FULL_WEALTH_AUDIT(
        "Cosmic Wealth Audit",
        "Holistic audit of cash flow, surplus & wealth pot",
        "👑"
    ),
    DEBT_LIQUIDATION(
        "Debt Liquidation Plan",
        "Highest interest vs. smallest debt audit of EMIs & borrowings",
        "⚔️"
    ),
    LEAK_PATROL(
        "Leak Patrol & Safe-to-Spend",
        "Uncover wasteful expense leaks & set daily spend limit",
        "🛡️"
    ),
    WEALTH_COMPOUNDING(
        "Compounding Paradigm",
        "Mobilize idle capital into intentional compounding",
        "📈"
    )
}

data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.6,
    val max_tokens: Int = 2048
)

data class GroqMessage(
    val role: String,
    val content: String
)
