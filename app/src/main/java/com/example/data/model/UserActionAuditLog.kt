package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Audit log recording each user action on incoming SMS/notifications:
 * 1. Approved messages (original text, parsed data, approved category)
 * 2. Rejected messages (original text, reason for rejection, parsed data)
 * 3. Approved next edited (changes to category, amount, subcategory, notes)
 *
 * Stored locally and exported to help AI Studio analyze real user actions
 * and improve classification algorithms.
 */
@Entity(
    tableName = "user_action_audit_logs",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class UserActionAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String = "",
    val eventType: String = "", // "MESSAGE_APPROVED", "MESSAGE_REJECTED", "APPROVED_NEXT_EDITED"
    val originalMessage: String = "",
    val sender: String = "",
    val amount: Double = 0.0,
    val currency: String = "₹",
    val type: String = "DEBIT",
    val merchant: String = "",
    val accountNumber: String = "",
    val referenceNumber: String = "",
    val initialCategory: String = "",
    val finalCategory: String = "",
    val initialSubCategory: String = "",
    val finalSubCategory: String = "",
    val initialAmount: Double = 0.0,
    val finalAmount: Double = 0.0,
    val initialNotes: String = "",
    val finalNotes: String = "",
    val diffSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
