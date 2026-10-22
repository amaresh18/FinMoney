package com.example.data.bank

import com.example.data.model.DebitCategory

enum class MerchantIconType {
    FOOD_DINING,
    GROCERY_QUICK,
    TRAVEL_CAB,
    TRAVEL_FLIGHT,
    TRAVEL_TRAIN,
    SHOPPING_RETAIL,
    FUEL_STATION,
    STREAMING_OTT,
    HEALTH_PHARMACY,
    RECHARGE_UTILITY,
    INVESTMENT_FINTECH,
    GENERIC_MERCHANT
}

data class EnrichedMerchant(
    val cleanName: String,
    val subtitle: String,
    val categoryKey: String,
    val brandColorHex: Long,
    val brandTextColorHex: Long = 0xFFFFFFFF,
    val iconType: MerchantIconType = MerchantIconType.GENERIC_MERCHANT,
    val monogram: String = "",
    val isVerifiedBrand: Boolean = false
)

/**
 * Enterprise-grade Merchant Enrichment Engine.
 * Transforms ugly raw SMS/UPI merchant strings, corporate registration names, and VPA handles
 * into beautiful, clean brand titles, categories, subtitles, and visual identities.
 *
 * Examples:
 * - "BUNDL TECHNOLOGIES PVT LTD" -> "Swiggy" (Food Delivery, OUTSIDE_FOOD, #FC8019)
 * - "swiggy.payu@icici" -> "Swiggy" (Food Delivery, OUTSIDE_FOOD, #FC8019)
 * - "KIRANAKART TECH" -> "Zepto" (Quick Commerce, GROCERIES, #7B2CBF)
 * - "ANI TECHNOLOGIES" -> "Ola Cabs" (Rides & Commute, TRAVEL, #64DD17)
 * - "AVENUE SUPERMARTS" -> "D-Mart" (Supermarket, GROCERIES, #00897B)
 * - "HINDUSTAN PETROLEUM CORP" -> "HPCL Fuel" (Fuel Station, FUEL, #0D47A1)
 */
object MerchantEnrichmentEngine {

    private val TELECOM_BRAND_NAMES = setOf("Airtel", "Jio", "Vi")

    private data class BrandDefinition(
        val cleanName: String,
        val subtitle: String,
        val categoryKey: String,
        val brandColorHex: Long,
        val iconType: MerchantIconType,
        val monogram: String,
        val aliases: List<String>
    )

    private val BRAND_REGISTRY = listOf(
        // --- Food Delivery & Restaurants ---
        BrandDefinition(
            cleanName = "Swiggy",
            subtitle = "Food Delivery",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFFC8019,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "SW",
            aliases = listOf("swiggy", "bundl technologies", "swiggypay", "swiggydineout", "dineout")
        ),
        BrandDefinition(
            cleanName = "Zomato",
            subtitle = "Food & Dining",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFE23744,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "ZO",
            aliases = listOf("zomato", "zomato food", "zomato gold", "zomato feeding india")
        ),
        BrandDefinition(
            cleanName = "Starbucks",
            subtitle = "Coffee & Cafe",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFF00704A,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "SB",
            aliases = listOf("starbucks", "tata starbucks")
        ),
        BrandDefinition(
            cleanName = "McDonald's",
            subtitle = "Fast Food",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFDA291C,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "MC",
            aliases = listOf("mcdonald", "mcdonalds", "hardcastle", "connaught plaza")
        ),
        BrandDefinition(
            cleanName = "Domino's Pizza",
            subtitle = "Pizza & Dining",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFF006491,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "DO",
            aliases = listOf("domino", "dominos", "jubilant foodworks", "jubilant")
        ),
        BrandDefinition(
            cleanName = "KFC",
            subtitle = "Fast Food",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFA3080C,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "KF",
            aliases = listOf("kfc", "sapphire foods", "devyani international", "yum restaurants")
        ),
        BrandDefinition(
            cleanName = "Pizza Hut",
            subtitle = "Pizza & Pasta",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFEE3124,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "PH",
            aliases = listOf("pizzahut", "pizza hut")
        ),
        BrandDefinition(
            cleanName = "Burger King",
            subtitle = "Burgers & Fries",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFD62300,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "BK",
            aliases = listOf("burger king", "burgerking", "restaurant brands asia")
        ),
        BrandDefinition(
            cleanName = "Chai Point",
            subtitle = "Chai & Snacks",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFF2E7D32,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "CP",
            aliases = listOf("chaipoint", "chai point", "mountain trail foods")
        ),
        BrandDefinition(
            cleanName = "Chaayos",
            subtitle = "Tea & Cafe",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFEF6C00,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "CH",
            aliases = listOf("chaayos", "sunshine teahouse")
        ),
        BrandDefinition(
            cleanName = "Subway",
            subtitle = "Sandwiches & Salads",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFF008C15,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "SU",
            aliases = listOf("subway")
        ),
        BrandDefinition(
            cleanName = "EatClub / Box8",
            subtitle = "Cloud Kitchen",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFF6200EA,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "EC",
            aliases = listOf("box8", "eatclub", "mojopizza", "poncho hospitality")
        ),
        BrandDefinition(
            cleanName = "Haldiram's",
            subtitle = "Sweets & Dining",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFC62828,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "HR",
            aliases = listOf("haldiram", "haldirams")
        ),

        // --- Quick Commerce & Groceries ---
        BrandDefinition(
            cleanName = "Blinkit",
            subtitle = "Quick Commerce",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFFF8CB46,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "BL",
            aliases = listOf("blinkit", "grofers", "blink commerce", "blnkcommerce")
        ),
        BrandDefinition(
            cleanName = "Zepto",
            subtitle = "10-Min Delivery",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFF7B2CBF,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "ZE",
            aliases = listOf("zepto", "kiranakart", "kirana kart")
        ),
        BrandDefinition(
            cleanName = "Swiggy Instamart",
            subtitle = "Instant Groceries",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFFFC8019,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "IM",
            aliases = listOf("instamart", "swiggy instamart")
        ),
        BrandDefinition(
            cleanName = "BigBasket",
            subtitle = "Online Supermarket",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFF689F38,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "BB",
            aliases = listOf("bigbasket", "bb daily", "supermarket grocery supplies", "innoventive retail")
        ),
        BrandDefinition(
            cleanName = "D-Mart",
            subtitle = "Supermarket",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFF00897B,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "DM",
            aliases = listOf("dmart", "d-mart", "avenue supermarts", "asl")
        ),
        BrandDefinition(
            cleanName = "JioMart",
            subtitle = "Groceries & Retail",
            categoryKey = DebitCategory.GROCERIES.name,
            brandColorHex = 0xFF0078D4,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "JM",
            aliases = listOf("jiomart", "jio mart", "reliance fresh", "reliance smart", "smart bazaar")
        ),
        BrandDefinition(
            cleanName = "Country Delight",
            subtitle = "Dairy & Produce",
            categoryKey = DebitCategory.MILK.name,
            brandColorHex = 0xFF2E7D32,
            iconType = MerchantIconType.GROCERY_QUICK,
            monogram = "CD",
            aliases = listOf("country delight", "behtar technologies")
        ),

        // --- Rides & Commute ---
        BrandDefinition(
            cleanName = "Uber",
            subtitle = "Rides & Commute",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF000000,
            iconType = MerchantIconType.TRAVEL_CAB,
            monogram = "UB",
            aliases = listOf("uber", "uber india", "uber bv", "uber rides")
        ),
        BrandDefinition(
            cleanName = "Ola Cabs",
            subtitle = "Cabs & Auto",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF64DD17,
            iconType = MerchantIconType.TRAVEL_CAB,
            monogram = "OL",
            aliases = listOf("ola", "olacabs", "ani technologies")
        ),
        BrandDefinition(
            cleanName = "Rapido",
            subtitle = "Bike & Auto Taxi",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFFFBC02D,
            iconType = MerchantIconType.TRAVEL_CAB,
            monogram = "RA",
            aliases = listOf("rapido", "roppen transportation", "roppen")
        ),
        BrandDefinition(
            cleanName = "Namma Yatri",
            subtitle = "Auto & Cab",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF00C853,
            iconType = MerchantIconType.TRAVEL_CAB,
            monogram = "NY",
            aliases = listOf("namma yatri", "moving tech innovative")
        ),
        BrandDefinition(
            cleanName = "IRCTC",
            subtitle = "Train Ticketing",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF0D47A1,
            iconType = MerchantIconType.TRAVEL_TRAIN,
            monogram = "IR",
            aliases = listOf("irctc", "indian railways", "cris")
        ),
        BrandDefinition(
            cleanName = "MakeMyTrip",
            subtitle = "Travel & Hotels",
            categoryKey = DebitCategory.TOURS.name,
            brandColorHex = 0xFFE53935,
            iconType = MerchantIconType.TRAVEL_FLIGHT,
            monogram = "MMT",
            aliases = listOf("makemytrip", "mmt", "makemytrip india")
        ),
        BrandDefinition(
            cleanName = "IndiGo",
            subtitle = "Airline Flights",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF00205B,
            iconType = MerchantIconType.TRAVEL_FLIGHT,
            monogram = "6E",
            aliases = listOf("indigo", "interglobe aviation", "goindigo")
        ),
        BrandDefinition(
            cleanName = "Air India",
            subtitle = "Airlines",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFFB71C1C,
            iconType = MerchantIconType.TRAVEL_FLIGHT,
            monogram = "AI",
            aliases = listOf("air india", "tata sia airlines", "airindia")
        ),
        BrandDefinition(
            cleanName = "Metro Rail",
            subtitle = "Metro Transit",
            categoryKey = DebitCategory.TRAVEL.name,
            brandColorHex = 0xFF1976D2,
            iconType = MerchantIconType.TRAVEL_TRAIN,
            monogram = "MR",
            aliases = listOf("dmrc", "bmrc", "delhi metro", "namma metro", "maha metro", "cmrl")
        ),

        // --- Shopping & Lifestyle ---
        BrandDefinition(
            cleanName = "Amazon",
            subtitle = "E-Commerce",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFFFF9900,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "AM",
            aliases = listOf("amazon", "amazonpay", "amazon pay", "amazon seller", "amazon retail")
        ),
        BrandDefinition(
            cleanName = "Flipkart",
            subtitle = "Online Shopping",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF2874F0,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "FK",
            aliases = listOf("flipkart", "flipkart internet", "flipkart payments")
        ),
        BrandDefinition(
            cleanName = "Myntra",
            subtitle = "Fashion & Apparel",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFFFF3F6C,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "MY",
            aliases = listOf("myntra", "myntra designs")
        ),
        BrandDefinition(
            cleanName = "Ajio",
            subtitle = "Fashion & Trends",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF2C4152,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "AJ",
            aliases = listOf("ajio", "reliance retail ajio")
        ),
        BrandDefinition(
            cleanName = "Nykaa",
            subtitle = "Beauty & Cosmetics",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFFFC2779,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "NY",
            aliases = listOf("nykaa", "fsn e-commerce")
        ),
        BrandDefinition(
            cleanName = "Tata CLiQ",
            subtitle = "Luxury & Electronics",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF880E4F,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "TC",
            aliases = listOf("tatacliq", "tata cliq", "tata unistore")
        ),
        BrandDefinition(
            cleanName = "Meesho",
            subtitle = "Value Shopping",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFFE91E63,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "ME",
            aliases = listOf("meesho", "fashnear technologies")
        ),
        BrandDefinition(
            cleanName = "Zara",
            subtitle = "Fashion & Apparel",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF111111,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "ZA",
            aliases = listOf("zara", "inditex trent")
        ),
        BrandDefinition(
            cleanName = "H&M",
            subtitle = "Fashion",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFFC62828,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "HM",
            aliases = listOf("h&m", "hennes & mauritz")
        ),
        BrandDefinition(
            cleanName = "Decathlon",
            subtitle = "Sports & Fitness",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF0082C3,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "DC",
            aliases = listOf("decathlon", "decathlon sports")
        ),
        BrandDefinition(
            cleanName = "IKEA",
            subtitle = "Home & Furniture",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF0058A3,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "IK",
            aliases = listOf("ikea", "ingka centres")
        ),
        BrandDefinition(
            cleanName = "Lenskart",
            subtitle = "Eyewear",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF002244,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "LK",
            aliases = listOf("lenskart", "lenskart solutions")
        ),
        BrandDefinition(
            cleanName = "Croma",
            subtitle = "Electronics",
            categoryKey = DebitCategory.SHOPPING.name,
            brandColorHex = 0xFF00838F,
            iconType = MerchantIconType.SHOPPING_RETAIL,
            monogram = "CR",
            aliases = listOf("croma", "infiniti retail")
        ),

        // --- Fuel & Energy ---
        BrandDefinition(
            cleanName = "HPCL Fuel",
            subtitle = "Petrol & Diesel",
            categoryKey = DebitCategory.FUEL.name,
            brandColorHex = 0xFF0D47A1,
            iconType = MerchantIconType.FUEL_STATION,
            monogram = "HP",
            aliases = listOf("hindustan petroleum", "hpcl", "hp petrol")
        ),
        BrandDefinition(
            cleanName = "BPCL Fuel",
            subtitle = "Petrol & Speed",
            categoryKey = DebitCategory.FUEL.name,
            brandColorHex = 0xFFFBC02D,
            iconType = MerchantIconType.FUEL_STATION,
            monogram = "BP",
            aliases = listOf("bharat petroleum", "bpcl", "speed petrol")
        ),
        BrandDefinition(
            cleanName = "Indian Oil",
            subtitle = "Petrol & Gas",
            categoryKey = DebitCategory.FUEL.name,
            brandColorHex = 0xFFE65100,
            iconType = MerchantIconType.FUEL_STATION,
            monogram = "IO",
            aliases = listOf("indian oil", "iocl", "xp95", "indane")
        ),
        BrandDefinition(
            cleanName = "Shell Fuel",
            subtitle = "Premium Petrol",
            categoryKey = DebitCategory.FUEL.name,
            brandColorHex = 0xFFFFD500,
            iconType = MerchantIconType.FUEL_STATION,
            monogram = "SH",
            aliases = listOf("shell petrol", "shell india", "shell lubricants")
        ),

        // --- OTT & Subscriptions ---
        BrandDefinition(
            cleanName = "Netflix",
            subtitle = "OTT Streaming",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFFE50914,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "NF",
            aliases = listOf("netflix", "netflix entertainment")
        ),
        BrandDefinition(
            cleanName = "Spotify",
            subtitle = "Music Streaming",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFF1DB954,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "SP",
            aliases = listOf("spotify", "spotify india")
        ),
        BrandDefinition(
            cleanName = "YouTube Premium",
            subtitle = "Video Streaming",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFFFF0000,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "YT",
            aliases = listOf("youtube", "google youtube", "google *youtube")
        ),
        BrandDefinition(
            cleanName = "Disney+ Hotstar",
            subtitle = "OTT & Live Sports",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFF113CCF,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "HS",
            aliases = listOf("hotstar", "disney hotstar", "novi digital")
        ),
        BrandDefinition(
            cleanName = "Amazon Prime",
            subtitle = "Video & Delivery",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFF00A8E1,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "PR",
            aliases = listOf("prime video", "amazon digital services", "amazon prime")
        ),
        BrandDefinition(
            cleanName = "Apple Services",
            subtitle = "iCloud & App Store",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFF222222,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "AP",
            aliases = listOf("apple.com/bill", "itunes", "apple services")
        ),
        BrandDefinition(
            cleanName = "Google Play / One",
            subtitle = "Apps & Cloud Storage",
            categoryKey = DebitCategory.OTT.name,
            brandColorHex = 0xFF4285F4,
            iconType = MerchantIconType.STREAMING_OTT,
            monogram = "GO",
            aliases = listOf("google play", "google storage", "google *play")
        ),

        // --- Health & Pharmacy ---
        BrandDefinition(
            cleanName = "Apollo Pharmacy",
            subtitle = "Medicines & Health",
            categoryKey = DebitCategory.PHARMACY.name,
            brandColorHex = 0xFFE65100,
            iconType = MerchantIconType.HEALTH_PHARMACY,
            monogram = "AP",
            aliases = listOf("apollo", "apollo pharmacy", "apollo hospitals")
        ),
        BrandDefinition(
            cleanName = "Tata 1mg",
            subtitle = "Online Medicines",
            categoryKey = DebitCategory.PHARMACY.name,
            brandColorHex = 0xFFFF6F61,
            iconType = MerchantIconType.HEALTH_PHARMACY,
            monogram = "1M",
            aliases = listOf("1mg", "tata 1mg", "tata 1 mg", "tata digital 1mg")
        ),
        BrandDefinition(
            cleanName = "PharmEasy",
            subtitle = "Pharmacy & Lab Tests",
            categoryKey = DebitCategory.PHARMACY.name,
            brandColorHex = 0xFF00A896,
            iconType = MerchantIconType.HEALTH_PHARMACY,
            monogram = "PE",
            aliases = listOf("pharmeasy", "api holdings")
        ),
        BrandDefinition(
            cleanName = "MedPlus",
            subtitle = "Pharmacy",
            categoryKey = DebitCategory.PHARMACY.name,
            brandColorHex = 0xFFC2185B,
            iconType = MerchantIconType.HEALTH_PHARMACY,
            monogram = "MP",
            aliases = listOf("medplus", "optival health")
        ),

        // --- Telecom & Utilities ---
        BrandDefinition(
            cleanName = "Airtel",
            subtitle = "Mobile & Fiber Bill",
            categoryKey = DebitCategory.RECHARGE.name,
            brandColorHex = 0xFFE50914,
            iconType = MerchantIconType.RECHARGE_UTILITY,
            monogram = "AT",
            aliases = listOf("airtel", "bharti airtel", "airtel payment", "airtel fiber")
        ),
        BrandDefinition(
            cleanName = "Jio",
            subtitle = "Prepaid / Postpaid",
            categoryKey = DebitCategory.RECHARGE.name,
            brandColorHex = 0xFF0F3E99,
            iconType = MerchantIconType.RECHARGE_UTILITY,
            monogram = "JIO",
            aliases = listOf("jio", "reliance jio", "jio prepaid", "jio fiber")
        ),
        BrandDefinition(
            cleanName = "Vi",
            subtitle = "Vodafone Idea Bill",
            categoryKey = DebitCategory.RECHARGE.name,
            brandColorHex = 0xFFE60000,
            iconType = MerchantIconType.RECHARGE_UTILITY,
            monogram = "VI",
            aliases = listOf("vodafone", "idea", "vodafone idea", "vi prepaid")
        ),
        BrandDefinition(
            cleanName = "Electricity Board",
            subtitle = "Power Utility Bill",
            categoryKey = DebitCategory.ELECTRICITY.name,
            brandColorHex = 0xFFFF8F00,
            iconType = MerchantIconType.RECHARGE_UTILITY,
            monogram = "EL",
            aliases = listOf("bescom", "tata power", "adani electricity", "msedcl", "cesc", "tneb", "torrent power")
        ),

        // --- Entertainment & Events ---
        BrandDefinition(
            cleanName = "BookMyShow",
            subtitle = "Movies & Live Events",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFC2185B,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "BMS",
            aliases = listOf("bookmyshow", "bms", "bigtree entertainment")
        ),
        BrandDefinition(
            cleanName = "PVR INOX",
            subtitle = "Cinema Theatres",
            categoryKey = DebitCategory.OUTSIDE_FOOD.name,
            brandColorHex = 0xFFFFB300,
            iconType = MerchantIconType.FOOD_DINING,
            monogram = "PVR",
            aliases = listOf("pvr", "inox", "pvr inox")
        ),

        // --- Fintech & Investments ---
        BrandDefinition(
            cleanName = "Zerodha",
            subtitle = "Stocks & Mutual Funds",
            categoryKey = DebitCategory.INVESTMENT.name,
            brandColorHex = 0xFF387ED1,
            iconType = MerchantIconType.INVESTMENT_FINTECH,
            monogram = "ZD",
            aliases = listOf("zerodha", "zerodha broking")
        ),
        BrandDefinition(
            cleanName = "Groww",
            subtitle = "Mutual Funds & Stocks",
            categoryKey = DebitCategory.INVESTMENT.name,
            brandColorHex = 0xFF00D09C,
            iconType = MerchantIconType.INVESTMENT_FINTECH,
            monogram = "GW",
            aliases = listOf("groww", "nextbillion technology")
        ),
        BrandDefinition(
            cleanName = "CRED",
            subtitle = "Credit Card Payments",
            categoryKey = DebitCategory.GENERAL.name,
            brandColorHex = 0xFF000000,
            iconType = MerchantIconType.INVESTMENT_FINTECH,
            monogram = "CR",
            aliases = listOf("cred", "dreamplug", "dreamplug technologies")
        )
    )

    /**
     * True when the notification title is the mobile carrier label (SIM), not the payer/payee.
     * Common on dual-SIM devices when bank SMS is grouped under "Airtel" / "Jio" / "Vi".
     */
    fun isCarrierNotificationTitle(title: String): Boolean {
        val t = title.trim().lowercase()
        if (t.isBlank()) return false
        val carrierTokens = listOf(
            "airtel", "jio", "vi", "vodafone", "idea", "bsnl", "mtnl", "aircel",
            "reliance jio", "vodafone idea"
        )
        if (carrierTokens.any { t == it || t.startsWith("$it ") || t.endsWith(" $it") || t.contains("message from $it") }) {
            return true
        }
        return t.matches(Regex("""^(ad|vm|vk|jd|bp|ax)-?(airtel|jiop|jio|vod|idea|bsnl)"""))
    }

    /** Telecom brands only when the message is clearly a recharge / mobile bill payment. */
    fun isTelecomRechargeOrBillContext(text: String): Boolean {
        val l = text.lowercase()
        val hints = listOf(
            "recharge", "recharged", "topup", "top-up", "top up",
            "postpaid", "post paid", "prepaid", "pre paid",
            "mobile bill", "phone bill", "dth", "talktime", "talk time",
            "bill payment", "bill paid", "pay bill", "utility bill",
            "plan renewed", "plan renewal", "validity", "data pack", "data plan",
            "fiber bill", "broadband bill", "wifi bill", "xstream",
            "airtel black", "airtel thanks", "jio recharge", "myjio"
        )
        return hints.any { l.contains(it) }
    }

    /**
     * Maps a SIM/carrier notification title to a telecom brand when the body is a real recharge or bill.
     */
    fun telecomBrandFromCarrierTitle(title: String): String? {
        val t = title.trim().lowercase()
        return when {
            t.contains("airtel") -> "Airtel"
            t.contains("jio") -> "Jio"
            t == "vi" || t.contains("vodafone") || t.contains("idea") -> "Vi"
            else -> null
        }
    }

    /** Payee/body explicitly names a telecom operator (not just the SIM notification title). */
    fun telecomBrandFromPayee(payee: String): String? {
        val p = payee.trim().lowercase()
        if (p.isBlank()) return null
        return when {
            p.contains("airtel") || p.contains("bharti") -> "Airtel"
            p.contains("jio") -> "Jio"
            p.contains("vodafone") || p == "vi" || p.contains("idea") -> "Vi"
            else -> null
        }
    }

    private fun matchBrandInCorpus(corpus: String, allowTelecom: Boolean): EnrichedMerchant? {
        if (corpus.isBlank()) return null
        for (brand in BRAND_REGISTRY) {
            if (!allowTelecom && TELECOM_BRAND_NAMES.contains(brand.cleanName)) continue
            for (alias in brand.aliases) {
                if (corpus.contains(alias)) {
                    return EnrichedMerchant(
                        cleanName = brand.cleanName,
                        subtitle = brand.subtitle,
                        categoryKey = brand.categoryKey,
                        brandColorHex = brand.brandColorHex,
                        iconType = brand.iconType,
                        monogram = brand.monogram,
                        isVerifiedBrand = true
                    )
                }
            }
        }
        return null
    }

    /**
     * Enriches a raw merchant text / VPA handle into a clean brand representation.
     */
    fun enrich(
        rawMerchant: String,
        rawText: String = "",
        senderOrTitle: String = ""
    ): EnrichedMerchant {
        val payeeIsTelecom = telecomBrandFromPayee(rawMerchant) != null
        val allowTelecom = isTelecomRechargeOrBillContext(rawText) || payeeIsTelecom
        // Keep an explicit payee like "Airtel" / "Bharti Airtel". Strip only SIM notification titles.
        val safeMerchant = when {
            payeeIsTelecom -> rawMerchant
            isCarrierNotificationTitle(rawMerchant) -> ""
            else -> rawMerchant
        }
        val safeTitle = if (isCarrierNotificationTitle(senderOrTitle)) "" else senderOrTitle

        // 1. Prefer brands found in transaction body (not carrier notification title)
        val bodyCorpus = "$safeMerchant $rawText".lowercase()
        matchBrandInCorpus(bodyCorpus, allowTelecom)?.let { return it }

        // 2. Sender/title only when not a carrier label
        if (safeTitle.isNotBlank()) {
            matchBrandInCorpus(safeTitle.lowercase(), allowTelecom && isTelecomRechargeOrBillContext(safeTitle))?.let { return it }
        }

        val cleanedInput = cleanRawMerchantString(safeMerchant.ifBlank { rawMerchant })

        // 3. Fallback heuristic cleaning if no brand matched
        val fallbackTitle = formatFallbackMerchantName(cleanedInput)
        val defaultMonogram = fallbackTitle.take(2).uppercase().ifBlank { "TX" }

        return EnrichedMerchant(
            cleanName = fallbackTitle.ifBlank { "Merchant Payment" },
            subtitle = "Direct Payment",
            categoryKey = DebitCategory.GENERAL.name,
            brandColorHex = 0xFF475569, // Slate 600
            iconType = MerchantIconType.GENERIC_MERCHANT,
            monogram = defaultMonogram,
            isVerifiedBrand = false
        )
    }

    /**
     * Cleans raw VPA handles, strip corporate suffixes, POS codes, and noise.
     */
    fun cleanRawMerchantString(raw: String): String {
        var clean = raw.trim()

        // 1. Strip VPA handles: e.g. "swiggy.payu@icici" -> "swiggy.payu"
        if (clean.contains("@")) {
            clean = clean.substringBefore("@")
        }

        // 2. Strip standard POS & UPI prefixes: "POS 428192 SWIGGY BANGALORE"
        clean = clean.replace(Regex("""^(?:POS|ECOMM|TXN|UPI|IMPS|CARD|AT|TO|INFO:)\s*""", RegexOption.IGNORE_CASE), "")

        // 3. Strip corporate clutter suffixes
        val clutter = listOf(
            "PVT LTD", "PRIVATE LIMITED", "LTD", "LIMITED", "LLP", "INC", "CORP",
            "TECHNOLOGIES", "TECHNOLOGY", "SOLUTIONS", "SERVICES", "ENTERPRISES",
            "INDIA", "BANGALORE", "MUMBAI", "DELHI", "HYDERABAD", "CHENNAI", "PUNE",
            "ONLINE", "RETAIL", "PAYMENTS", "PAY", "STORE", "OUTLET", "BRANCH"
        )
        for (word in clutter) {
            clean = clean.replace(Regex("""\b$word\b""", RegexOption.IGNORE_CASE), "")
        }

        // 4. Remove excessive punctuation and repeated whitespace
        clean = clean.replace(Regex("""[._\-/]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return clean
    }

    private fun formatFallbackMerchantName(text: String): String {
        if (text.isBlank()) return "Merchant"
        return text.split(" ")
            .filter { it.isNotBlank() }
            .take(3)
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}
