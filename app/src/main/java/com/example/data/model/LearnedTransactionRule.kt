package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted self-learning rules created automatically from user interactions:
 * - Edits to expense titles, categories, tags, or subcategories.
 * - Repeated rejections of non-expense notifications or specific merchants.
 * - Explicit approvals with custom category overrides.
 */
@Entity(
    tableName = "learned_transaction_rules",
    indices = [
        Index(value = ["patternKey", "userId"], unique = true)
    ]
)
data class LearnedTransactionRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String = "",
    val patternKey: String, // Normalized pattern key, e.g. "swiggy", "netflix", "user@upi"
    val merchantDisplayName: String = "", // e.g. "Swiggy Food"
    val matchType: String = "MERCHANT", // MERCHANT, SENDER, KEYWORD, VPA
    val learnedCategory: String = "", // e.g. "OUTSIDE_FOOD"
    val learnedCustomCategoryName: String = "", // e.g. "Dining"
    val learnedTitle: String = "", // e.g. "Swiggy Dinner"
    val learnedTags: String = "", // e.g. "Dinner,Food"
    val isAutoIgnored: Boolean = false, // Set true if user repeatedly rejects this pattern
    val rejectCount: Int = 0,
    val approveCount: Int = 0,
    val editCount: Int = 0,
    val notes: String = "",
    val lastAppliedTimestamp: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
