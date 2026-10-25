package com.example.ui.components

import com.example.data.model.CategorySummary
import com.example.data.model.CustomCategory
import com.example.data.model.DebitCategory
import com.example.data.model.DebitItem

const val CUSTOM_CATEGORY_FILTER_PREFIX = "CUSTOM:"

fun debitCategoryFilterKey(item: DebitItem): String {
    val cat = item.category.trim()
    if (cat.equals(DebitCategory.CUSTOM.name, ignoreCase = true) || cat.equals("Custom", ignoreCase = true)) {
        val name = item.customCategoryName.trim().ifBlank { "Custom" }
        return "$CUSTOM_CATEGORY_FILTER_PREFIX$name"
    }
    return cat.ifBlank { DebitCategory.GENERAL.name }
}

fun debitCategoryDisplayLabel(item: DebitItem): String {
    val cat = item.category.trim()
    if (cat.equals(DebitCategory.CUSTOM.name, ignoreCase = true) || cat.equals("Custom", ignoreCase = true)) {
        return item.customCategoryName.trim().ifBlank { "Custom" }
    }
    return cat.ifBlank { "General" }
}

fun matchesDebitCategoryFilter(item: DebitItem, filterKey: String): Boolean {
    if (filterKey.equals("ALL", ignoreCase = true)) return true
    return debitCategoryFilterKey(item).equals(filterKey, ignoreCase = true)
}

fun iconKeyForDebitFilter(filterKey: String, customCategories: List<CustomCategory>): String {
    if (!filterKey.startsWith(CUSTOM_CATEGORY_FILTER_PREFIX, ignoreCase = true)) return filterKey
    val name = filterKey.substring(CUSTOM_CATEGORY_FILTER_PREFIX.length)
    return customCategories.find { it.name.equals(name, ignoreCase = true) }?.iconName?.takeIf { it.isNotBlank() }
        ?: filterKey
}

fun debitMatchesCategorySummary(item: DebitItem, summary: CategorySummary): Boolean {
    return debitCategoryFilterKey(item).equals(summary.categoryKey, ignoreCase = true)
}

fun debitItemStoredIcon(item: DebitItem, customCategories: List<CustomCategory>): String? {
    if (!item.category.equals(DebitCategory.CUSTOM.name, ignoreCase = true)) return null
    val name = item.customCategoryName.trim()
    return customCategories.find { it.name.equals(name, ignoreCase = true) }?.iconName?.takeIf { it.isNotBlank() }
}

fun categorySummaryStoredIcon(summary: CategorySummary): String? {
    if (!summary.categoryKey.startsWith(CUSTOM_CATEGORY_FILTER_PREFIX, ignoreCase = true)) return null
    return summary.iconKey.takeIf { it.isNotBlank() && it != summary.categoryKey }
}

fun customCategoryTint(hex: String): androidx.compose.ui.graphics.Color? {
    return try {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex))
    } catch (_: Throwable) {
        null
    }
}

fun customCategoryForName(name: String, customCategories: List<CustomCategory>): CustomCategory? {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return null
    return customCategories.find { it.name.equals(trimmed, ignoreCase = true) }
}

fun customCategoryForDebit(item: DebitItem, customCategories: List<CustomCategory>): CustomCategory? {
    val cat = item.category.trim()
    if (!cat.equals(DebitCategory.CUSTOM.name, ignoreCase = true) && !cat.equals("Custom", ignoreCase = true)) {
        return null
    }
    return customCategoryForName(item.customCategoryName, customCategories)
}

fun customCategoryForFilterKey(filterKey: String, customCategories: List<CustomCategory>): CustomCategory? {
    if (!filterKey.startsWith(CUSTOM_CATEGORY_FILTER_PREFIX, ignoreCase = true)) return null
    val name = filterKey.substring(CUSTOM_CATEGORY_FILTER_PREFIX.length)
    return customCategoryForName(name, customCategories)
}
