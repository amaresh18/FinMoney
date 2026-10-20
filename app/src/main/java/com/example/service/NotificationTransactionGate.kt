package com.example.service

import com.example.data.bank.BankTxType
import com.example.data.bank.ParsedBankTransaction

/**
 * Decides whether an intercepted notification should be ignored, applied as a silent
 * balance update, or surfaced to the user for approval.
 */
object NotificationTransactionGate {

    enum class Action {
        IGNORE,
        BALANCE_UPDATE_ONLY,
        REQUEST_USER_APPROVAL
    }

    data class Decision(
        val action: Action,
        val reason: String,
        val balanceToApply: Double? = null
    )

    private val PROMO_PATTERNS = listOf(
        Regex("""\b(?:pre[- ]?approved|preapproved)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:apply now|click here|tap to|limited time|hurry|don't miss)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:scratch card|spin to win|lucky draw|refer(?:ral)?\s+(?:and\s+)?earn)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:flat\s+\d+%\s*off|\d+%\s*off|upto\s*₹|up to\s*rs)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:cashback\s+offer|offer\s+for\s+you|exclusive\s+deal|special\s+offer)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:recharge\s+now|pay\s+bill\s+now|avail\s+(?:the\s+)?offer)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:rate\s+us|update\s+available|new\s+features)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:you\s+are\s+eligible|get\s+instant\s+loan|personal\s+loan\s+offer)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reward\s+points?\s+expir|redeem\s+your\s+points)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwon\s+a\s+(?:scratch|reward|cashback)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:rewards?\s+unlocked|voucher\s+inside|coupon\s+inside)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:complete\s+your\s+kyc|update\s+kyc)\s+to\s+(?:win|get)\b""", RegexOption.IGNORE_CASE)
    )

    private val UNSETTLED_REQUEST_MARKERS = listOf(
        "payment request", "collect request", "has requested", "is requesting",
        "money request", "upi request", "pay request", "awaiting your approval",
        "approve the request", "requesting rs", "requesting ₹", "please pay",
        "reminder to pay", "payment pending", "awaiting payment", "pay by clicking"
    )

    private val EXPLICIT_DEBIT_VERBS = listOf(
        "debited", "debit of", "is debited", "has been debited",
        "paid to", "paid rs", "paid ₹", "spent on", "spent rs", "spent ₹",
        "sent to", "sent rs", "sent ₹", "withdrawn", "withdrawal of",
        "charged to", "charged rs", "charged ₹", "purchase of", "transferred to",
        "transfer of", "autopay of", "mandate of", "imps to", "neft to", "upi to"
    )

    private val EXPLICIT_CREDIT_VERBS = listOf(
        "credited", "credit of", "is credited", "has been credited",
        "received from", "received rs", "received ₹", "recd rs", "recd.",
        "deposited", "refunded", "refund of", "salary credited",
        "transferred to your", "money received", "payment received",
        "credited to your", "credited by", "credited in", "inward credit"
    )

    private val BALANCE_ONLY_MARKERS = listOf(
        "avl bal", "avail bal", "available bal", "available balance",
        "account balance", "a/c balance", "ac balance", "balance in a/c",
        "balance in account", "balance for account", "balance is rs",
        "balance is ₹", "ending balance", "updated balance", "cur bal",
        "current balance", "closing balance", "clear bal", "eff bal"
    )

    fun evaluate(
        title: String,
        body: String,
        parseResult: TransactionParseResult,
        parsedTx: ParsedBankTransaction?
    ): Decision {
        val combined = "$title $body"
        val lower = combined.lowercase()

        val nonFinancial = TransactionParser.determineNonFinancialReason(body, title)
        if (nonFinancial != null) {
            return Decision(Action.IGNORE, nonFinancial)
        }

        if (matchesPromoWithoutSettlement(lower)) {
            return Decision(Action.IGNORE, "Promotional or marketing message without settled transaction")
        }

        if (isUnsettledPaymentRequest(lower) && !hasExplicitTransactionVerb(lower)) {
            return Decision(Action.IGNORE, "UPI/payment request — not a completed debit or credit")
        }

        val txAmount = parseResult.amount
        val avlBalance = parseResult.availableBalance
        val hasExplicitTx = hasExplicitTransactionVerb(lower)
        val hasRef = parseResult.referenceNumber.isNotBlank()
        val balanceOnly = isBalanceOnlyAlert(lower, txAmount, hasExplicitTx, hasRef)

        if (balanceOnly && avlBalance != null && avlBalance >= 0.0) {
            return Decision(
                action = Action.BALANCE_UPDATE_ONLY,
                reason = "Balance enquiry alert — updating reconciliation without approval prompt",
                balanceToApply = avlBalance
            )
        }

        if (parsedTx == null || txAmount == null || txAmount <= 0.0) {
            if (avlBalance != null && avlBalance >= 0.0 && !hasExplicitTx) {
                return Decision(
                    action = Action.BALANCE_UPDATE_ONLY,
                    reason = "Balance-only alert with no transaction amount",
                    balanceToApply = avlBalance
                )
            }
            return Decision(Action.IGNORE, "No valid transaction amount detected")
        }

        val incorrect = EntryIntegrityEngine.evaluateIncorrectness(
            amount = parseResult.amount,
            type = parseResult.transactionType,
            bankName = parseResult.bankName,
            merchant = parseResult.merchantName,
            rawText = combined
        )
        if (incorrect != null) {
            return Decision(Action.IGNORE, incorrect)
        }

        if (!hasExplicitTx && !hasRef && parseResult.confidenceScore < 0.55f) {
            return Decision(Action.IGNORE, "Low-confidence alert without UTR/reference or debit/credit verb")
        }

        if (isPromotionalCashbackWording(lower) && !hasExplicitCreditSettlement(lower)) {
            return Decision(Action.IGNORE, "Cashback/reward promo — not a settled credit transaction")
        }

        return Decision(Action.REQUEST_USER_APPROVAL, "Valid debit/credit transaction")
    }

    fun hasExplicitTransactionVerb(lowerText: String): Boolean {
        if (EXPLICIT_DEBIT_VERBS.any { lowerText.contains(it) }) return true
        if (EXPLICIT_CREDIT_VERBS.any { lowerText.contains(it) }) return true
        if (Regex("""\b(?:upi|imps|neft|rtgs)\s+(?:ref|txn|transaction)\b""").containsMatchIn(lowerText)) return true
        if (Regex("""\b(?:txn|transaction)\s+(?:id|ref)\b""").containsMatchIn(lowerText)) return true
        return false
    }

    fun isBalanceOnlyAlert(
        lowerText: String,
        transactionAmount: Double?,
        hasExplicitTx: Boolean,
        hasReference: Boolean
    ): Boolean {
        if (hasExplicitTx || hasReference) return false
        val mentionsBalance = BALANCE_ONLY_MARKERS.any { lowerText.contains(it) } ||
            (lowerText.contains("balance") && !lowerText.contains("minimum balance"))
        if (!mentionsBalance) return false
        // Balance-only: no separate payment line, or amount equals extracted balance with no verb
        return transactionAmount == null || !hasExplicitTx
    }

    private fun matchesPromoWithoutSettlement(lower: String): Boolean {
        val promoHit = PROMO_PATTERNS.any { it.containsMatchIn(lower) }
        if (!promoHit) return false
        return !hasExplicitTransactionVerb(lower)
    }

    private fun isPromotionalCashbackWording(lower: String): Boolean {
        return lower.contains("cashback on your next") ||
            lower.contains("earn cashback") ||
            lower.contains("get cashback") ||
            lower.contains("cashback offer") ||
            lower.contains("cashback rewards") ||
            (lower.contains("cashback") && lower.contains("next transaction"))
    }

    private fun hasExplicitCreditSettlement(lower: String): Boolean {
        return EXPLICIT_CREDIT_VERBS.any { lower.contains(it) } ||
            lower.contains("credited to your account") ||
            lower.contains("has been credited")
    }

    private fun isUnsettledPaymentRequest(lower: String): Boolean {
        return UNSETTLED_REQUEST_MARKERS.any { lower.contains(it) }
    }
}
