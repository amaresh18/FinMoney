package com.example

import com.example.data.bank.MerchantEnrichmentEngine
import com.example.data.bank.MerchantIconType
import com.example.data.model.DebitCategory
import org.junit.Assert.*
import org.junit.Test

class MerchantEnrichmentEngineTest {

    @Test
    fun `test Swiggy VPA and corporate entity names are enriched to Swiggy`() {
        val result1 = MerchantEnrichmentEngine.enrich("swiggy.payu@icici")
        assertEquals("Swiggy", result1.cleanName)
        assertEquals("Food Delivery", result1.subtitle)
        assertEquals(DebitCategory.OUTSIDE_FOOD.name, result1.categoryKey)
        assertTrue(result1.isVerifiedBrand)
        assertEquals(0xFFFC8019, result1.brandColorHex)
        assertEquals(MerchantIconType.FOOD_DINING, result1.iconType)

        val result2 = MerchantEnrichmentEngine.enrich("BUNDL TECHNOLOGIES PVT LTD")
        assertEquals("Swiggy", result2.cleanName)
    }

    @Test
    fun `test Zepto corporate entity name is enriched to Zepto`() {
        val result = MerchantEnrichmentEngine.enrich("KIRANAKART TECHNOLOGIES PVT LTD")
        assertEquals("Zepto", result.cleanName)
        assertEquals("10-Min Delivery", result.subtitle)
        assertEquals(DebitCategory.GROCERIES.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test Blinkit VPA and brand name is enriched to Blinkit`() {
        val result = MerchantEnrichmentEngine.enrich("blnkcommerce@icici")
        assertEquals("Blinkit", result.cleanName)
        assertEquals(DebitCategory.GROCERIES.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test Ola corporate name ANI Technologies is enriched to Ola Cabs`() {
        val result = MerchantEnrichmentEngine.enrich("ANI TECHNOLOGIES PVT LTD")
        assertEquals("Ola Cabs", result.cleanName)
        assertEquals(DebitCategory.TRAVEL.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test DMart corporate entity Avenue Supermarts is enriched to D-Mart`() {
        val result = MerchantEnrichmentEngine.enrich("AVENUE SUPERMARTS LIMITED")
        assertEquals("D-Mart", result.cleanName)
        assertEquals(DebitCategory.GROCERIES.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test McDonald corporate entity Hardcastle is enriched to McDonalds`() {
        val result = MerchantEnrichmentEngine.enrich("HARDCASTLE RESTAURANTS PVT LTD")
        assertEquals("McDonald's", result.cleanName)
        assertEquals(DebitCategory.OUTSIDE_FOOD.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test Dominos corporate entity Jubilant FoodWorks is enriched to Dominos Pizza`() {
        val result = MerchantEnrichmentEngine.enrich("JUBILANT FOODWORKS LIMITED")
        assertEquals("Domino's Pizza", result.cleanName)
        assertEquals(DebitCategory.OUTSIDE_FOOD.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test Starbucks corporate entity Tata Starbucks is enriched to Starbucks`() {
        val result = MerchantEnrichmentEngine.enrich("TATA STARBUCKS PVT LTD")
        assertEquals("Starbucks", result.cleanName)
        assertEquals(DebitCategory.OUTSIDE_FOOD.name, result.categoryKey)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `test Fuel entities HPCL and BPCL are enriched with correct categories`() {
        val hpcl = MerchantEnrichmentEngine.enrich("HINDUSTAN PETROLEUM CORP LTD")
        assertEquals("HPCL Fuel", hpcl.cleanName)
        assertEquals(DebitCategory.FUEL.name, hpcl.categoryKey)

        val bpcl = MerchantEnrichmentEngine.enrich("BHARAT PETROLEUM CORP")
        assertEquals("BPCL Fuel", bpcl.cleanName)
        assertEquals(DebitCategory.FUEL.name, bpcl.categoryKey)
    }

    @Test
    fun `carrier notification title does not override merchant in SMS body`() {
        val sms = "Dear Customer, INR 450.00 debited from A/C **1234 to SWIGGY on 09-OCT-26. Avl Bal: INR 35,420.50."
        val result = MerchantEnrichmentEngine.enrich(
            rawMerchant = "Swiggy",
            rawText = sms,
            senderOrTitle = "Airtel"
        )
        assertEquals("Swiggy", result.cleanName)
        assertTrue(result.isVerifiedBrand)
    }

    @Test
    fun `airtel title alone does not classify random debit as Airtel recharge`() {
        val result = MerchantEnrichmentEngine.enrich(
            rawMerchant = "",
            rawText = "INR 820 debited from A/C XX5678. Avl Bal Rs 14,230.",
            senderOrTitle = "Airtel"
        )
        assertNotEquals("Airtel", result.cleanName)
    }

    @Test
    fun `test unknown merchant is formatted into clean title-cased fallback`() {
        val result = MerchantEnrichmentEngine.enrich("RAMESH GENERAL PROVISION STORE PVT LTD")
        assertEquals("Ramesh General Provision", result.cleanName)
        assertEquals(DebitCategory.GENERAL.name, result.categoryKey)
        assertFalse(result.isVerifiedBrand)
    }
}
