package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.data.model.CustomExpenseTag
import com.example.data.model.DebitCategory

/**
 * Curated India-market expense tags with bundled brand artwork (PNG in res/drawable).
 * One merchant per tag — no combined labels (e.g. use "Amazon" and "Flipkart" separately).
 */
data class KnownExpenseTag(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    @DrawableRes val brandIconRes: Int,
    val backgroundHex: String = "#FFFFFF"
)

data class ExpenseTagVisual(
    val displayName: String,
    @DrawableRes val brandIconRes: Int? = null,
    val monogram: String? = null,
    val backgroundColor: Color = Color.White,
    val foregroundColor: Color = Color.White,
    val storedIconName: String? = null,
    val isKnownBrand: Boolean = false
)

private fun tag(
    id: String,
    displayName: String,
    drawable: Int,
    aliases: List<String> = emptyList(),
    bg: String = "#FFFFFF"
) = KnownExpenseTag(
    id = id,
    displayName = displayName,
    aliases = (aliases + listOf(displayName)).map { it.lowercase() }.distinct(),
    brandIconRes = drawable,
    backgroundHex = bg
)

/** Master catalog — each entry is a single brand / merchant. */
val knownExpenseTags: List<KnownExpenseTag> = listOf(
    tag("amazon", "Amazon", R.drawable.brand_amazon, listOf("amzn"), bg = "#FFF4E6"),
    tag("flipkart", "Flipkart", R.drawable.brand_flipkart, listOf("fk"), bg = "#E8F2FF"),
    tag("myntra", "Myntra", R.drawable.brand_myntra, bg = "#FDE8F0"),
    tag("ajio", "Ajio", R.drawable.brand_ajio, bg = "#F3F4F6"),
    tag("meesho", "Meesho", R.drawable.brand_meesho, bg = "#FCE7F3"),
    tag("nykaa", "Nykaa", R.drawable.brand_nykaa, bg = "#FDF2F8"),
    tag("tatacliq", "Tata CLiQ", R.drawable.brand_tatacliq, listOf("tata cliq", "tatacliq")),
    tag("croma", "Croma", R.drawable.brand_croma),
    tag("reliance_digital", "Reliance Digital", R.drawable.brand_reliance_digital, listOf("reliance digital", "reliancedigital")),
    tag("dmart", "DMart", R.drawable.brand_dmart, listOf("d-mart")),
    tag("bigbasket", "BigBasket", R.drawable.brand_bigbasket, listOf("big basket")),
    tag("jiomart", "JioMart", R.drawable.brand_jiomart, listOf("jio mart")),
    tag("zomato", "Zomato", R.drawable.brand_zomato, bg = "#FEE2E2"),
    tag("swiggy", "Swiggy", R.drawable.brand_swiggy, bg = "#FFEDD5"),
    tag("zepto", "Zepto", R.drawable.brand_zepto),
    tag("blinkit", "Blinkit", R.drawable.brand_blinkit, listOf("grofers")),
    tag("instamart", "Instamart", R.drawable.brand_instamart, listOf("swiggy instamart")),
    tag("dunzo", "Dunzo", R.drawable.brand_dunzo),
    tag("dominos", "Domino's", R.drawable.brand_dominos, listOf("domino", "dominos pizza")),
    tag("mcdonalds", "McDonald's", R.drawable.brand_mcdonalds, listOf("mcd", "mcdonald")),
    tag("kfc", "KFC", R.drawable.brand_kfc),
    tag("starbucks", "Starbucks", R.drawable.brand_starbucks),
    tag("restaurant", "Restaurant", R.drawable.brand_restaurant, listOf("restaurant", "dining", "cafe", "autumn cafe")),
    tag("uber", "Uber", R.drawable.brand_uber, bg = "#F3F4F6"),
    tag("ola", "Ola", R.drawable.brand_ola, listOf("ola cabs", "olacabs"), bg = "#DCFCE7"),
    tag("rapido", "Rapido", R.drawable.brand_rapido, bg = "#FEF9C3"),
    tag("namma_yatri", "Namma Yatri", R.drawable.brand_namma_yatri, listOf("nammayatri")),
    tag("redbus", "redBus", R.drawable.brand_redbus, listOf("red bus")),
    tag("irctc", "IRCTC", R.drawable.brand_irctc, listOf("indian railways", "train ticket")),
    tag("makemytrip", "MakeMyTrip", R.drawable.brand_makemytrip, listOf("mmt")),
    tag("goibibo", "Goibibo", R.drawable.brand_goibibo),
    tag("indigo", "IndiGo", R.drawable.brand_indigo, listOf("6e", "goindigo")),
    tag("air_india", "Air India", R.drawable.brand_air_india, listOf("airindia")),
    tag("metro", "Metro", R.drawable.brand_metro, listOf("dmrc", "bmrc", "delhi metro")),
    tag("iocl", "Indian Oil", R.drawable.brand_iocl, listOf("indian oil", "ioc")),
    tag("hpcl", "HPCL", R.drawable.brand_hpcl, listOf("hp petrol", "hindustan petroleum")),
    tag("bpcl", "BPCL", R.drawable.brand_bpcl, listOf("bharat petroleum")),
    tag("shell", "Shell", R.drawable.brand_shell),
    tag("netflix", "Netflix", R.drawable.brand_netflix),
    tag("prime_video", "Prime Video", R.drawable.brand_prime_video, listOf("amazon prime", "prime")),
    tag("hotstar", "JioHotstar", R.drawable.brand_hotstar, listOf("disney hotstar", "hotstar")),
    tag("sonyliv", "SonyLIV", R.drawable.brand_sonyliv, listOf("sony liv")),
    tag("spotify", "Spotify", R.drawable.brand_spotify),
    tag("youtube", "YouTube", R.drawable.brand_youtube, listOf("yt premium", "youtube premium")),
    tag("jio", "Jio", R.drawable.brand_jio, listOf("reliance jio")),
    tag("airtel", "Airtel", R.drawable.brand_airtel),
    tag("vi", "Vi", R.drawable.brand_vi, listOf("vodafone", "vodafone idea", "idea")),
    tag("bsnl", "BSNL", R.drawable.brand_bsnl),
    tag("phonepe", "PhonePe", R.drawable.brand_phonepe, listOf("phone pe")),
    tag("paytm", "Paytm", R.drawable.brand_paytm),
    tag("googlepay", "Google Pay", R.drawable.brand_googlepay, listOf("gpay", "google pay", "tez")),
    tag("cred", "CRED", R.drawable.brand_cred),
    tag("pharmeasy", "PharmEasy", R.drawable.brand_pharmeasy),
    tag("tata_1mg", "Tata 1mg", R.drawable.brand_tata_1mg, listOf("1mg", "tata 1mg")),
    tag("apollo", "Apollo Pharmacy", R.drawable.brand_apollo, listOf("apollo 247", "apollo pharmacy")),
    tag("byjus", "BYJU'S", R.drawable.brand_byjus, listOf("byju")),
    tag("unacademy", "Unacademy", R.drawable.brand_unacademy),
    tag("hdfc", "HDFC Bank", R.drawable.brand_hdfc, listOf("hdfc bank", "hdfc bank loan", "hdfc credit card", "hdfc emi"), bg = "#EFF6FF"),
    tag("sbi", "State Bank of India", R.drawable.brand_sbi, listOf("sbi", "sbi bank", "sbi loan", "state bank of india", "sbi card"), bg = "#E0F2FE"),
    tag("icici", "ICICI Bank", R.drawable.brand_icici, listOf("icici", "icici bank", "icici loan", "icici bank emi"), bg = "#FFF7ED"),
    tag("axis", "Axis Bank", R.drawable.brand_axis, listOf("axis", "axis bank", "axis bank loan", "axis emi"), bg = "#FDF2F8"),
    tag("kotak", "Kotak Mahindra Bank", R.drawable.brand_kotak, listOf("kotak", "kotak bank", "kotak loan"), bg = "#FEF2F2"),
    tag("bajaj", "Bajaj Finserv", R.drawable.brand_bajaj, listOf("bajaj", "bajaj finserv", "bajaj finance"), bg = "#F0F9FF"),
    tag("medplus", "MedPlus", R.drawable.brand_medplus, listOf("med plus", "optival health")),
    tag("netmeds", "Netmeds", R.drawable.brand_netmeds, listOf("net meds")),
    tag("orange_health", "Orange Health", R.drawable.brand_orange, listOf("orange health", "orange pharmacy")),
    tag("wellness_forever", "Wellness Forever", R.drawable.brand_medplus, listOf("wellness forever")),
    tag("apollo_hospitals", "Apollo Hospitals", R.drawable.brand_apollo, listOf("apollo hospital", "apollo hospitals")),
    tag("fortis", "Fortis Hospital", R.drawable.brand_fortis, listOf("fortis", "fortis healthcare")),
    tag("max_healthcare", "Max Healthcare", R.drawable.brand_max, listOf("max hospital", "max healthcare")),
    tag("manipal", "Manipal Hospital", R.drawable.brand_manipal, listOf("manipal", "manipal hospital")),
    tag("narayana_health", "Narayana Health", R.drawable.brand_manipal, listOf("narayana hrudayalaya", "narayana health")),
    tag("kims", "KIMS Hospital", R.drawable.brand_fortis, listOf("kims hospital", "kims")),
    tag("yashoda", "Yashoda Hospital", R.drawable.brand_manipal, listOf("yashoda hospital", "yashoda")),
    tag("medanta", "Medanta", R.drawable.brand_max, listOf("medanta hospital", "medanta")),
    tag("rainbow", "Rainbow Children's Hospital", R.drawable.brand_manipal, listOf("rainbow hospital")),
    tag("cloudnine", "Cloudnine Hospital", R.drawable.brand_manipal, listOf("cloudnine")),
)

private val tagById = knownExpenseTags.associateBy { it.id }

/** Subcategory / merchant picks shown per expense category (single brand each). */
val expenseTagsByDebitCategory: Map<String, List<String>> = mapOf(
    DebitCategory.SHOPPING.name to listOf(
        "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq", "croma", "reliance_digital"
    ),
    DebitCategory.GROCERIES.name to listOf(
        "bigbasket", "blinkit", "zepto", "jiomart", "dmart", "instamart"
    ),
    DebitCategory.OUTSIDE_FOOD.name to listOf(
        "zomato", "swiggy", "dominos", "mcdonalds", "kfc", "starbucks", "restaurant", "dunzo"
    ),
    DebitCategory.TRAVEL.name to listOf(
        "uber", "ola", "rapido", "namma_yatri", "metro", "redbus", "irctc", "indigo", "air_india"
    ),
    DebitCategory.TOURS.name to listOf(
        "makemytrip", "goibibo", "indigo", "air_india", "irctc", "redbus"
    ),
    DebitCategory.FUEL.name to listOf("iocl", "hpcl", "bpcl", "shell"),
    DebitCategory.OTT.name to listOf(
        "netflix", "prime_video", "hotstar", "sonyliv", "spotify", "youtube"
    ),
    DebitCategory.EMI.name to listOf(
        "hdfc", "sbi", "icici", "axis", "kotak", "bajaj", "cred", "paytm", "phonepe"
    ),
    DebitCategory.RECHARGE.name to listOf("jio", "airtel", "vi", "bsnl"),
    DebitCategory.INTERNET.name to listOf("jio", "airtel", "vi", "bsnl"),
    DebitCategory.ELECTRICITY.name to listOf("paytm", "phonepe", "googlepay"),
    DebitCategory.PHARMACY.name to listOf(
        "apollo", "medplus", "tata_1mg", "pharmeasy", "netmeds", "orange_health", "wellness_forever"
    ),
    DebitCategory.HOSPITAL.name to listOf(
        "apollo_hospitals", "fortis", "max_healthcare", "manipal", "narayana_health", "kims", "yashoda", "medanta", "rainbow", "cloudnine"
    ),
    DebitCategory.SCHOOL_FEES.name to listOf("byjus", "unacademy"),
    DebitCategory.HOSTEL_FEES.name to listOf("byjus", "unacademy"),
    DebitCategory.GENERAL.name to listOf("amazon", "flipkart", "phonepe", "paytm", "googlepay", "cred"),
)

fun expenseTagsForDebitCategory(categoryKey: String): List<KnownExpenseTag> {
    val key = categoryKey.uppercase()
    val ids = expenseTagsByDebitCategory[key]
        ?: expenseTagsByDebitCategory[DebitCategory.GENERAL.name]
        ?: emptyList()
    return ids.mapNotNull { tagById[it] }
}

fun normalizeTagToken(raw: String): String {
    return raw.lowercase()
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Prefer a single merchant; legacy combined titles use the first segment only. */
fun primaryTagLabel(rawTitle: String): String {
    val trimmed = rawTitle.trim()
    if (trimmed.contains("/") || trimmed.contains("|")) {
        return trimmed.split(Regex("[/|]")).first().trim()
    }
    if (trimmed.contains(",")) {
        return trimmed.split(",").first().trim()
    }
    return trimmed
}

fun resolveKnownExpenseTag(rawTitle: String, categoryKey: String = ""): KnownExpenseTag? {
    val title = primaryTagLabel(rawTitle)
    if (title.isBlank()) return null

    knownExpenseTags.find { it.displayName.equals(title, ignoreCase = true) }?.let { return it }

    val normalized = normalizeTagToken(title)
    if (normalized.isBlank()) return null

    val categoryTags = expenseTagsForDebitCategory(categoryKey).ifEmpty { knownExpenseTags }
    var best: KnownExpenseTag? = null
    var bestScore = 0
    for (tag in categoryTags) {
        for (alias in tag.aliases) {
            val score = when {
                normalized == alias -> alias.length + 300
                normalized.contains(alias) && alias.length >= 3 -> alias.length + 80
                alias.contains(normalized) && normalized.length >= 4 -> normalized.length + 40
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = tag
            }
        }
    }
    if (best != null) return best

    return knownExpenseTags.firstOrNull { tag ->
        tag.aliases.any { alias ->
            normalized == alias || (alias.length >= 3 && normalized.contains(alias))
        }
    }
}

fun resolveExpenseTagVisual(
    rawTitle: String,
    categoryKey: String = "",
    customExpenseTags: List<CustomExpenseTag> = emptyList()
): ExpenseTagVisual? {
    val title = rawTitle.trim()
    if (title.isBlank()) return null

    val known = resolveKnownExpenseTag(title, categoryKey)
    if (known != null) {
        return ExpenseTagVisual(
            displayName = known.displayName,
            brandIconRes = known.brandIconRes,
            backgroundColor = parseHexColor(known.backgroundHex),
            isKnownBrand = true
        )
    }

    val primary = primaryTagLabel(title)
    val custom = customExpenseTags.find { it.name.equals(primary, ignoreCase = true) }
        ?: customExpenseTags.find { primary.contains(it.name, ignoreCase = true) }
    if (custom != null) {
        return ExpenseTagVisual(
            displayName = custom.name,
            monogram = custom.name.take(2).uppercase(),
            backgroundColor = parseHexColor(custom.colorHex),
            foregroundColor = Color.White,
            storedIconName = custom.iconName,
            isKnownBrand = false
        )
    }

    return null
}

fun curatedTagSuggestionsForCategory(categoryKey: String): List<String> {
    return expenseTagsForDebitCategory(categoryKey).map { it.displayName }
}
