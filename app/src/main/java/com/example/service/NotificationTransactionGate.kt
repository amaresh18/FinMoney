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
        // Loan & Credit Line Marketing
        Regex("""\b(?:pre[- ]?approved|preapproved|personal\s+loan|instant\s+loan|loan\s+offer|apply\s+(?:for\s+)?loan|eligible\s+for\s+(?:a\s+)?loan|credit\s+line\s+of|instant\s+cash\s+up\s+to|zero\s+processing\s+fee|interest\s+rate\s+starting)\b""", RegexOption.IGNORE_CASE),
        // Credit Card Marketing & Limit Offers
        Regex("""\b(?:apply\s+(?:for\s+)?(?:credit\s+)?card|lifetime\s+free\s+credit\s+card|upgrade\s+(?:your\s+)?(?:credit\s+)?card|credit\s+limit\s+increase|limit\s+increas(?:ed|e)\s+to|enhancement\s+on\s+your\s+card|annual\s+fee\s+waiver)\b""", RegexOption.IGNORE_CASE),
        // Cashback, Reward & Scratch Card Marketing
        Regex("""\b(?:cashback\s+on\s+your|earn\s+(?:up\s+to\s+)?(?:cashback|rs|₹|inr)|win\s+(?:up\s+to\s+)?(?:cashback|rs|₹|inr)|won\s+a\s+(?:scratch|cashback|reward)|scratch\s+card|spin\s+to\s+win|lucky\s+draw|claim\s+(?:your\s+)?(?:cashback|reward|voucher)|rewards?\s+unlocked|exclusive\s+offer|special\s+offer|cashback\s+offer|stand\s+a\s+chance)\b""", RegexOption.IGNORE_CASE),
        // Discount, Sale & Coupon Promos
        Regex("""\b(?:flat\s+\d+%\s*off|\d+%\s*off|upto\s*₹|up to\s*rs|save\s+up\s+to|discount\s+on|use\s+code\b|coupon\s+code|voucher\s+inside|coupon\s+inside|deals\s+starting|festival\s+sale|mega\s+sale|best\s+deals?|hurry|don't\s+miss|limited\s+time|limited\s+period)\b""", RegexOption.IGNORE_CASE),
        // Upcoming Bill Due & Bill Generation Notices (NOT yet paid)
        Regex("""\b(?:bill\s+generated|bill\s+is\s+generated|bill\s+of\s+(?:rs|₹|inr)|bill\s+due|bill\s+is\s+due|due\s+date\s+is|payment\s+due\s+of|due\s+on\b|pay\s+before\b|avoid\s+late\s+fee|statement\s+(?:is\s+)?ready|minimum\s+amount\s+due|total\s+amount\s+due|reminder:\s*pay)\b""", RegexOption.IGNORE_CASE),
        // Recharge, Plan Expiry & Data Pack Alerts
        Regex("""\b(?:recharge\s+with|recharge\s+now|recharge\s+of\s+(?:rs|₹|inr)|plan\s+expir(?:ed|ing|es)|pack\s+expir(?:ed|ing|es)|validity\s+expir(?:ed|ing|es)|daily\s+data\s+(?:pack\s+)?exhausted|100%\s+daily\s+data|quota\s+exhausted|unlimited\s+calls\s+and|topup\s+now)\b""", RegexOption.IGNORE_CASE),
        // Referral Loops & Engagement
        Regex("""\b(?:refer\s+(?:and\s+|&)?earn|refer\s+your\s+friend|invite\s+friend|rate\s+us|rate\s+our\s+app|update\s+available|new\s+features)\b""", RegexOption.IGNORE_CASE),
        // Call-to-action imperative marketing without actual debit/credit
        Regex("""\b(?:click\s+here|tap\s+to\s+(?:claim|avail|apply|view|pay)|avail\s+(?:the\s+)?offer|complete\s+your\s+kyc\s+to)\b""", RegexOption.IGNORE_CASE)
    )

    private val UNSETTLED_REQUEST_MARKERS = listOf(
        "payment request", "collect request", "has requested", "is requesting",
        "money request", "upi request", "pay request", "awaiting your approval",
        "approve the request", "requesting rs", "requesting ₹", "requesting inr", "please pay",
        "reminder to pay", "payment pending", "awaiting payment", "pay by clicking",
        "tap to pay", "pending approval"
    )

    private val EXPLICIT_DEBIT_VERBS = listOf(
        "debited", "debit of", "is debited", "has been debited", "was debited",
        "debited by", "debited with", "debited for", "debited from",
        "paid to", "paid at", "paid for", "paid rs", "paid ₹", "paid inr",
        "you paid", "payment successful", "successfully paid",
        "spent on", "spent at", "spent rs", "spent ₹", "spent inr",
        "sent to", "sent rs", "sent ₹", "sent inr", "money sent to",
        "transferred to", "transferred rs", "transferred inr", "transferred ₹",
        "withdrawn from", "withdrawal of", "cash withdrawal", "atm cash",
        "charged to", "charged rs", "charged ₹", "charged inr",
        "autopay of", "mandate of", "imps to", "neft to", "upi to"
    )

    private val EXPLICIT_CREDIT_VERBS = listOf(
        "credited", "credit of", "is credited", "has been credited", "was credited",
        "credited with", "credited to", "credited by", "credited in", "inward credit",
        "received from", "received in", "received rs", "received ₹", "received inr",
        "recd rs", "recd ₹", "recd.", "rec'd rs", "rec'd ₹", "money received from", "payment received from",
        "you received", "you have received", "deposited to", "deposited in",
        "refund of", "refund credited", "refunded to your", "reversal credited", "reversed to",
        "cashback credited", "cashback received", "cashback of rs", "cashback of ₹", "cashback of inr",
        "salary credited", "salary of", "salary deposit", "stipend credited",
        "transferred to your", "transferred to you", "sent you", "paid you",
        "added to account", "added to your account", "added to wallet"
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

        if (isUnsettledPaymentRequest(lower)) {
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

        if (!hasExplicitTx && !hasRef && parseResult.confidenceScore < 0.60f) {
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
        if (Regex("""\b(?:dr|cr)[:.]|\b(?:dr|cr)\s""").containsMatchIn(lowerText)) return true
        if (Regex("""(?:upi|imps|neft|rtgs)[\s\:\/\-]+(?:dr|cr)""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)) return true
        if (Regex("""\b(?:upi|imps|neft|rtgs)\s+(?:ref|txn|transaction)\b""").containsMatchIn(lowerText)) return true
        if (Regex("""\b(?:txn|transaction)\s+(?:id|ref|done|successful|completed)\b""").containsMatchIn(lowerText)) return true
        if (Regex("""\b(?:paid|sent|received)\s+(?:inr|rs\.?|₹|\$)\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(lowerText)) return true
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

        // Uncompromising promotional markers that are NEVER actual bank debits or credits
        val strictPromos = listOf(
            "scratch card", "spin to win", "lucky draw", "win up to", "won a scratch",
            "pre-approved", "preapproved", "personal loan", "instant loan", "apply now",
            "eligible for loan", "discount on", "save up to", "use code", "coupon code",
            "deals starting", "festival sale", "mega sale", "bill is due", "bill due",
            "bill generated", "recharge with", "recharge now", "plan expires", "pack expired",
            "data exhausted", "refer and earn", "refer your friend", "cashback on your next",
            "earn cashback", "get cashback", "cashback offer", "claim your", "rewards unlocked"
        )
        if (strictPromos.any { lower.contains(it) }) {
            return true
        }

        // Only permit through if there is an explicit settled bank account / card action
        val hasSettledAccountDebitOrCredit = Regex("""\b(?:a\/c|account|acct|card)\b.*?\b(?:debited|credited)\b""").containsMatchIn(lower) ||
            Regex("""\b(?:debited\s+from|credited\s+to|refund\s+credited)\b""").containsMatchIn(lower)

        return !hasSettledAccountDebitOrCredit
    }

    private fun isPromotionalCashbackWording(lower: String): Boolean {
        return lower.contains("cashback on your next") ||
            lower.contains("earn cashback") ||
            lower.contains("get cashback") ||
            lower.contains("cashback offer") ||
            lower.contains("cashback rewards") ||
            lower.contains("win cashback") ||
            lower.contains("claim cashback") ||
            lower.contains("cashback unlocked") ||
            (lower.contains("cashback") && (lower.contains("next") || lower.contains("order") || lower.contains("offer")))
    }

    private fun hasExplicitCreditSettlement(lower: String): Boolean {
        return lower.contains("cashback credited") ||
            lower.contains("cashback received") ||
            lower.contains("cashback of inr") ||
            lower.contains("cashback of rs") ||
            lower.contains("cashback of ₹") ||
            lower.contains("credited to your account") ||
            lower.contains("credited to account") ||
            lower.contains("has been credited")
    }

    private fun isUnsettledPaymentRequest(lower: String): Boolean {
        return UNSETTLED_REQUEST_MARKERS.any { lower.contains(it) }
    }
}
