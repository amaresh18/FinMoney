package com.example

import com.example.model.DefaultDebitCategories
import org.junit.Assert.*
import org.junit.Test

class FinMoneyUnitTest {

    @Test
    fun testDefaultCategoriesIncludeAllRequestedItems() {
        val categories = DefaultDebitCategories.list
        assertTrue(categories.contains("Groceries"))
        assertTrue(categories.contains("Recharge"))
        assertTrue(categories.contains("Chit Payments"))
        assertTrue(categories.contains("Fuel"))
    }

    @Test
    fun testCategoryDeduplication() {
        val defaultList = listOf("Chit Payments", "EMI", "Groceries", "Fuel")
        val customList = listOf("groceries", "Groceries", "Gym", "gym", "Fuel ")

        val merged = (defaultList + customList).distinctBy { it.trim().lowercase() }
        assertEquals(5, merged.size)
        assertTrue(merged.any { it.equals("Gym", ignoreCase = true) })
    }

    @Test
    fun testFlexibleCashCalculation() {
        val income = 75000.0
        val debits = 25000.0
        val flexibleCash = income - debits
        assertEquals(50000.0, flexibleCash, 0.001)

        val daysRemaining = 25
        val dailySafeSpend = flexibleCash / daysRemaining
        assertEquals(2000.0, dailySafeSpend, 0.001)
    }
}
