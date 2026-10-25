package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Supported recognizable high-fidelity bank and merchant brands.
 */
enum class BrandType {
    // App Brand
    FINMONEY,

    // Banks
    HDFC,
    SBI,
    ICICI,
    AXIS,
    KOTAK,
    PNB,
    BOB,
    CANARA,
    UNION_BANK,
    IDFC_FIRST,
    INDUSIND,
    YES_BANK,
    FEDERAL_BANK,
    JUPITER,
    FI_MONEY,
    RBL_BANK,

    // UPI & Payments
    GPAY,
    PHONEPE,
    PAYTM,
    SUPER_MONEY,
    CRED,
    BHIM,
    PLUXEE,
    AMAZON_PAY,

    // Food & Dining
    SWIGGY,
    ZOMATO,
    MCDONALDS,
    DOMINOS,
    STARBUCKS,

    // Quick Commerce & Groceries
    BLINKIT,
    ZEPTO,
    BIGBASKET,
    INSTAMART,
    DMART,
    SMART_SUPERMARKET,

    // Travel & Commute
    UBER,
    OLA,
    RAPIDO,
    IRCTC,

    // E-Commerce
    AMAZON,
    FLIPKART,
    MYNTRA,
    AJIO,

    // Entertainment
    NETFLIX,
    SPOTIFY,
    YOUTUBE,

    // Telecom & Utilities
    AIRTEL,
    JIO,
    VI,
    ELECTRICITY_BESCOM,

    // Fuel
    HPCL,
    BPCL,
    IOCL,

    // Generic Fallback
    GENERIC_BANK,
    GENERIC_MERCHANT
}

/**
 * Detects the specific brand from bank name, merchant name, description, or package.
 */
fun identifyBrand(nameOrText: String, fallbackCategory: String = ""): BrandType {
    val enriched = com.example.data.bank.MerchantEnrichmentEngine.enrich(nameOrText)
    val lower = "$nameOrText ${enriched.cleanName} $fallbackCategory".lowercase(Locale.ROOT)

    return when {
        // App Brand
        lower.contains("finmoney") || lower.contains("fin money") -> BrandType.FINMONEY

        // Banks
        lower.contains("hdfc") -> BrandType.HDFC
        lower.contains("sbi") || lower.contains("state bank") -> BrandType.SBI
        lower.contains("icici") -> BrandType.ICICI
        lower.contains("axis") -> BrandType.AXIS
        lower.contains("kotak") || lower.contains("811") -> BrandType.KOTAK
        lower.contains("pnb") || lower.contains("punjab national") -> BrandType.PNB
        lower.contains("baroda") || lower.contains("bob") -> BrandType.BOB
        lower.contains("canara") -> BrandType.CANARA
        lower.contains("union bank") || lower.contains("union") -> BrandType.UNION_BANK
        lower.contains("idfc") -> BrandType.IDFC_FIRST
        lower.contains("indusind") -> BrandType.INDUSIND
        lower.contains("yes bank") -> BrandType.YES_BANK
        lower.contains("jupiter") -> BrandType.JUPITER
        lower.contains("fi money") || lower.contains("co.fi") -> BrandType.FI_MONEY
        lower.contains("federal") -> BrandType.FEDERAL_BANK
        lower.contains("rbl") -> BrandType.RBL_BANK

        // Food & Quick Commerce
        lower.contains("swiggy") -> BrandType.SWIGGY
        lower.contains("zomato") -> BrandType.ZOMATO
        lower.contains("blinkit") -> BrandType.BLINKIT
        lower.contains("zepto") -> BrandType.ZEPTO
        lower.contains("bigbasket") || lower.contains("bbdaily") -> BrandType.BIGBASKET
        lower.contains("instamart") -> BrandType.INSTAMART
        lower.contains("dmart") -> BrandType.DMART
        lower.contains("smart supermarket") || lower.contains("smart bazaar") || lower.contains("smart point") || lower.contains("reliance smart") -> BrandType.SMART_SUPERMARKET
        lower.contains("mcdonald") -> BrandType.MCDONALDS
        lower.contains("domino") -> BrandType.DOMINOS
        lower.contains("starbuck") -> BrandType.STARBUCKS

        // Travel
        lower.contains("uber") -> BrandType.UBER
        lower.contains("ola") || lower.contains("olacabs") -> BrandType.OLA
        lower.contains("rapido") -> BrandType.RAPIDO
        lower.contains("irctc") || lower.contains("train ticket") -> BrandType.IRCTC

        // E-Commerce
        lower.contains("amazon pay") -> BrandType.AMAZON_PAY
        lower.contains("amazon") -> BrandType.AMAZON
        lower.contains("flipkart") -> BrandType.FLIPKART
        lower.contains("myntra") -> BrandType.MYNTRA
        lower.contains("ajio") -> BrandType.AJIO

        // UPI & Wallets
        lower.contains("super money") || lower.contains("supermoney") || lower.contains("super.money") -> BrandType.SUPER_MONEY
        lower.contains("gpay") || lower.contains("google pay") || lower.contains("paisa") -> BrandType.GPAY
        lower.contains("phonepe") -> BrandType.PHONEPE
        lower.contains("paytm") -> BrandType.PAYTM
        lower.contains("cred") -> BrandType.CRED
        lower.contains("bhim") -> BrandType.BHIM
        lower.contains("pluxee") || lower.contains("sodexo") -> BrandType.PLUXEE

        // Entertainment
        lower.contains("netflix") -> BrandType.NETFLIX
        lower.contains("spotify") -> BrandType.SPOTIFY
        lower.contains("youtube") -> BrandType.YOUTUBE

        // Telecom & Power
        lower.contains("airtel") -> BrandType.AIRTEL
        lower.contains("jio") -> BrandType.JIO
        lower.contains("vodafone") || lower.contains("vi ") -> BrandType.VI
        lower.contains("bescom") || lower.contains("cesc") || lower.contains("electricity") || lower.contains("power") -> BrandType.ELECTRICITY_BESCOM

        // Fuel
        lower.contains("hpcl") || lower.contains("hindustan petroleum") -> BrandType.HPCL
        lower.contains("bpcl") || lower.contains("bharat petroleum") -> BrandType.BPCL
        lower.contains("iocl") || lower.contains("indian oil") -> BrandType.IOCL

        // General bank keywords
        lower.contains("bank") || lower.contains("a/c") || lower.contains("account") || lower.contains("card") -> BrandType.GENERIC_BANK

        else -> BrandType.GENERIC_MERCHANT
    }
}

@Composable
fun BrandLogoView(
    name: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    fallbackCategory: String = ""
) {
    BrandLogo(
        name = name,
        modifier = modifier,
        badgeSize = badgeSize,
        shape = shape,
        fallbackCategory = fallbackCategory
    )
}

/**
 * Renders the authentic, recognizable brand logo with official brand colors and vector geometry.
 */
@Composable
fun BrandLogo(
    name: String,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    fallbackCategory: String = ""
) {
    val brand = remember(name, fallbackCategory) { identifyBrand(name, fallbackCategory) }

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when (brand) {
            BrandType.FINMONEY -> FinMoneyAppLogo(badgeSize, shape)
            BrandType.HDFC -> HdfcLogo(badgeSize)
            BrandType.SBI -> SbiLogo(badgeSize)
            BrandType.ICICI -> IciciLogo(badgeSize)
            BrandType.AXIS -> AxisLogo(badgeSize)
            BrandType.KOTAK -> KotakLogo(badgeSize)
            BrandType.PNB -> PnbLogo(badgeSize)
            BrandType.BOB -> BobLogo(badgeSize)
            BrandType.CANARA -> CanaraLogo(badgeSize)
            BrandType.UNION_BANK -> UnionBankLogo(badgeSize)
            BrandType.IDFC_FIRST -> IdfcFirstLogo(badgeSize)
            BrandType.INDUSIND -> IndusIndLogo(badgeSize)
            BrandType.YES_BANK -> YesBankLogo(badgeSize)
            BrandType.FEDERAL_BANK -> FederalBankLogo(badgeSize)
            BrandType.JUPITER -> JupiterLogo(badgeSize)
            BrandType.FI_MONEY -> FiMoneyLogo(badgeSize)
            BrandType.RBL_BANK -> RblLogo(badgeSize)

            BrandType.SWIGGY -> SwiggyLogo(badgeSize)
            BrandType.ZOMATO -> ZomatoLogo(badgeSize)
            BrandType.BLINKIT -> BlinkitLogo(badgeSize)
            BrandType.ZEPTO -> ZeptoLogo(badgeSize)
            BrandType.BIGBASKET -> BigBasketLogo(badgeSize)
            BrandType.INSTAMART -> InstamartLogo(badgeSize)
            BrandType.DMART -> DmartLogo(badgeSize)
            BrandType.SMART_SUPERMARKET -> SmartSupermarketLogo(badgeSize)
            BrandType.MCDONALDS -> McDonaldsLogo(badgeSize)
            BrandType.DOMINOS -> DominosLogo(badgeSize)
            BrandType.STARBUCKS -> StarbucksLogo(badgeSize)

            BrandType.UBER -> UberLogo(badgeSize)
            BrandType.OLA -> OlaLogo(badgeSize)
            BrandType.RAPIDO -> RapidoLogo(badgeSize)
            BrandType.IRCTC -> IrctcLogo(badgeSize)

            BrandType.AMAZON -> AmazonLogo(badgeSize)
            BrandType.AMAZON_PAY -> AmazonPayLogo(badgeSize)
            BrandType.FLIPKART -> FlipkartLogo(badgeSize)
            BrandType.MYNTRA -> MyntraLogo(badgeSize)
            BrandType.AJIO -> AjioLogo(badgeSize)

            BrandType.GPAY -> GPayLogo(badgeSize)
            BrandType.PHONEPE -> PhonePeLogo(badgeSize)
            BrandType.PAYTM -> PaytmLogo(badgeSize)
            BrandType.SUPER_MONEY -> SuperMoneyLogo(badgeSize)
            BrandType.CRED -> CredLogo(badgeSize)
            BrandType.BHIM -> BhimLogo(badgeSize)
            BrandType.PLUXEE -> PluxeeLogo(badgeSize)

            BrandType.NETFLIX -> NetflixLogo(badgeSize)
            BrandType.SPOTIFY -> SpotifyLogo(badgeSize)
            BrandType.YOUTUBE -> YouTubeLogo(badgeSize)

            BrandType.AIRTEL -> AirtelLogo(badgeSize)
            BrandType.JIO -> JioLogo(badgeSize)
            BrandType.VI -> ViLogo(badgeSize)
            BrandType.ELECTRICITY_BESCOM -> ElectricityLogo(badgeSize)

            BrandType.HPCL -> FuelBrandLogo(badgeSize, "HP", Color(0xFF003882), Color(0xFFDA291C))
            BrandType.BPCL -> FuelBrandLogo(badgeSize, "BP", Color(0xFF005696), Color(0xFFFFD100))
            BrandType.IOCL -> FuelBrandLogo(badgeSize, "IO", Color(0xFFF37021), Color(0xFF004386))

            BrandType.GENERIC_BANK -> GenericBankLogo(name, badgeSize)
            BrandType.GENERIC_MERCHANT -> GenericMerchantLogo(name, fallbackCategory, badgeSize)
        }
    }
}

// ==============================================================================
// 1. BANK LOGOS (Authentic Visual Insignia)
// ==============================================================================

@Composable
fun FinMoneyAppLogo(size: Dp, shape: Shape = RoundedCornerShape(10.dp)) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_icon_logo),
        contentDescription = "FinMoney",
        modifier = Modifier
            .size(size)
            .clip(shape)
    )
}

/** App icon + optional "FinMoney" title for headers and onboarding. */
@Composable
fun FinMoneyWordmarkRow(
    iconSize: Dp = 32.dp,
    iconCornerRadius: Dp = 8.dp,
    showTitle: Boolean = true,
    titleFontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    titleColor: Color = Color.Unspecified,
    titleFontWeight: FontWeight = FontWeight.ExtraBold,
    letterSpacing: androidx.compose.ui.unit.TextUnit = (-0.5).sp,
    horizontalSpacing: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
    ) {
        FinMoneyAppLogo(iconSize, RoundedCornerShape(iconCornerRadius))
        if (showTitle) {
            Text(
                text = "FinMoney",
                fontSize = titleFontSize,
                fontWeight = titleFontWeight,
                color = if (titleColor == Color.Unspecified) Color.White else titleColor,
                letterSpacing = letterSpacing
            )
        }
    }
}

/**
 * HDFC Bank: Navy blue background (#004C8F) with the iconic white center square
 * and 4 red corner blocks (#ED1C24).
 */
@Composable
fun HdfcLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height

        // Background: HDFC Deep Blue
        drawRect(color = Color(0xFF004C8F))

        // Center white square
        val squareSize = w * 0.44f
        val left = (w - squareSize) / 2f
        val top = (h - squareSize) / 2f
        drawRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(squareSize, squareSize)
        )

        // 4 Red corner/bracket blocks of the official HDFC logo
        val blockW = squareSize * 0.35f
        val blockH = squareSize * 0.35f
        val redColor = Color(0xFFED1C24)

        // Top-Left red block
        drawRect(redColor, topLeft = Offset(left, top), size = Size(blockW, blockH))
        // Top-Right red block
        drawRect(redColor, topLeft = Offset(left + squareSize - blockW, top), size = Size(blockW, blockH))
        // Bottom-Left red block
        drawRect(redColor, topLeft = Offset(left, top + squareSize - blockH), size = Size(blockW, blockH))
        // Bottom-Right red block
        drawRect(redColor, topLeft = Offset(left + squareSize - blockW, top + squareSize - blockH), size = Size(blockW, blockH))

        // Inner Navy core
        val coreSize = squareSize * 0.45f
        drawRect(
            color = Color(0xFF004C8F),
            topLeft = Offset((w - coreSize) / 2f, (h - coreSize) / 2f),
            size = Size(coreSize, coreSize)
        )
    }
}

/**
 * State Bank of India (SBI): Iconic SBI Blue circle with white keyhole and vertical slot.
 */
@Composable
fun SbiLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)

        // SBI Blue background
        drawRect(color = Color(0xFF0072BB))

        // White circular body
        val outerRadius = w * 0.32f
        drawCircle(
            color = Color.White,
            radius = outerRadius,
            center = center,
            style = Stroke(width = w * 0.08f)
        )

        // Inner keyhole circle
        val innerHoleRadius = w * 0.12f
        drawCircle(
            color = Color.White,
            radius = innerHoleRadius,
            center = center
        )

        // Keyhole vertical slit extending downward to bottom of ring
        val slitWidth = w * 0.08f
        val slitHeight = w * 0.22f
        drawRect(
            color = Color(0xFF0072BB),
            topLeft = Offset(center.x - slitWidth / 2f, center.y),
            size = Size(slitWidth, slitHeight)
        )
    }
}

/**
 * ICICI Bank: Iconic maroon/garnet background (#A01F24) with bold orange and white insignia.
 */
@Composable
fun IciciLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF8B181B), Color(0xFFBA262B), Color(0xFFE65100))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "i",
                color = Color(0xFFFF9800),
                fontSize = (size.value * 0.52f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = "CICI",
                color = Color.White,
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

/**
 * Axis Bank: Burgundy background (#97144D) with white tilted 'A' / chevron pyramid.
 */
@Composable
fun AxisLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height

        // Axis Burgundy Red
        drawRect(color = Color(0xFF97144D))

        // White geometric inverted triangle / arrowhead 'A'
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.25f)
            lineTo(w * 0.75f, h * 0.72f)
            lineTo(w * 0.62f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.48f)
            lineTo(w * 0.38f, h * 0.72f)
            close()
        }
        drawPath(path, color = Color.White)

        // Axis diagonal cross slash
        val slash = Path().apply {
            moveTo(w * 0.45f, h * 0.52f)
            lineTo(w * 0.82f, h * 0.52f)
            lineTo(w * 0.76f, h * 0.62f)
            lineTo(w * 0.40f, h * 0.62f)
            close()
        }
        drawPath(slash, color = Color(0xFFFF80AB))
    }
}

/**
 * Kotak Mahindra Bank: Vibrant Red background (#ED1C24) with white infinity emblem.
 */
@Composable
fun KotakLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFED1C24)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val w = this.size.width
            val h = this.size.height

            // White infinity symbol loops
            val strokeWidth = w * 0.16f
            val loopRadius = w * 0.22f

            // Left loop
            drawCircle(
                color = Color.White,
                radius = loopRadius,
                center = Offset(w * 0.32f, h * 0.5f),
                style = Stroke(width = strokeWidth)
            )
            // Right loop
            drawCircle(
                color = Color.White,
                radius = loopRadius,
                center = Offset(w * 0.68f, h * 0.5f),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

/**
 * Punjab National Bank (PNB): Deep crimson with gold circle and 'pnb'.
 */
@Composable
fun PnbLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFA20000)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.7f)) {
            drawCircle(
                color = Color(0xFFFFB81C),
                style = Stroke(width = this.size.width * 0.12f)
            )
        }
        Text(
            text = "pnb",
            color = Color(0xFFFFB81C),
            fontSize = (size.value * 0.35f).sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Bank of Baroda: Vermilion orange background (#F26522) with white Baroda Sun rays.
 */
@Composable
fun BobLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF26522)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val center = Offset(this.size.width * 0.45f, this.size.height * 0.55f)
            drawCircle(color = Color.White, radius = this.size.width * 0.2f, center = center)
            // Radiating sun arcs
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(this.size.width * 0.15f, this.size.height * 0.2f),
                size = Size(this.size.width * 0.7f, this.size.height * 0.7f),
                style = Stroke(width = this.size.width * 0.12f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Canara Bank: Royal Blue (#005A9C) with yellow triangle chevron.
 */
@Composable
fun CanaraLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height
        drawRect(Color(0xFF005A9C))

        // Interlocking yellow & cyan triangles
        val t1 = Path().apply {
            moveTo(w * 0.3f, h * 0.25f)
            lineTo(w * 0.7f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.55f)
            close()
        }
        drawPath(t1, color = Color(0xFFFFD100))

        val t2 = Path().apply {
            moveTo(w * 0.5f, h * 0.45f)
            lineTo(w * 0.7f, h * 0.75f)
            lineTo(w * 0.3f, h * 0.75f)
            close()
        }
        drawPath(t2, color = Color(0xFF00ADEF))
    }
}

/**
 * Union Bank of India: Deep Blue with Red stylized interlocking U.
 */
@Composable
fun UnionBankLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF003366)),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-3).dp), verticalAlignment = Alignment.CenterVertically) {
            Text("U", color = Color(0xFFD2232A), fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.Black)
            Text("B", color = Color.White, fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * IDFC FIRST Bank: Maroon background with white IDFC block.
 */
@Composable
fun IdfcFirstLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9E1B32)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("IDFC", color = Color.White, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
            Text("FIRST", color = Color(0xFFFFD54F), fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/**
 * IndusInd Bank: Rich maroon-brown background with Indus bull monogram.
 */
@Composable
fun IndusIndLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF851419)),
        contentAlignment = Alignment.Center
    ) {
        Text("IndusInd", color = Color.White, fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Yes Bank: Royal Blue with red compass arrow.
 */
@Composable
fun YesBankLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF002D62)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YES", color = Color.White, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Black)
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFFED1C24), modifier = Modifier.size(size * 0.35f))
        }
    }
}

/**
 * Federal Bank: Navy blue with gold emblem.
 */
@Composable
fun FederalBankLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF002B49)),
        contentAlignment = Alignment.Center
    ) {
        Text("FEDERAL", color = Color(0xFFFFC72C), fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Jupiter Money: Obsidian black with warm glowing Jupiter orange emblem.
 */
@Composable
fun JupiterLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E24)),
        contentAlignment = Alignment.Center
    ) {
        Text("J", color = Color(0xFFFF6D00), fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Fi Money: Clean dark mint background with white Fi.
 */
@Composable
fun FiMoneyLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00382E)),
        contentAlignment = Alignment.Center
    ) {
        Text("fi", color = Color(0xFF26E07F), fontSize = (size.value * 0.52f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * RBL Bank: White with blue and red RBL badge.
 */
@Composable
fun RblLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF003366)),
        contentAlignment = Alignment.Center
    ) {
        Text("RBL", color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Black)
    }
}

// ==============================================================================
// 2. FOOD & DINING LOGOS (Swiggy, Zomato, etc.)
// ==============================================================================

/**
 * Swiggy: Authentic Swiggy Orange (#FC8019) with the crisp white Swiggy delivery pin/S logo.
 */
@Composable
fun SwiggyLogo(size: Dp) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val w = this.size.width
        val h = this.size.height

        // Swiggy vibrant orange background
        drawRect(color = Color(0xFFFC8019))

        // Iconic Swiggy delivery locator pin with 'S' curve
        val path = Path().apply {
            // Pin outer head
            moveTo(w * 0.50f, h * 0.18f)
            cubicTo(w * 0.68f, h * 0.18f, w * 0.72f, h * 0.34f, w * 0.70f, h * 0.44f)
            cubicTo(w * 0.68f, h * 0.55f, w * 0.50f, h * 0.72f, w * 0.50f, h * 0.82f)
            cubicTo(w * 0.50f, h * 0.72f, w * 0.32f, h * 0.55f, w * 0.30f, h * 0.44f)
            cubicTo(w * 0.28f, h * 0.34f, w * 0.32f, h * 0.18f, w * 0.50f, h * 0.18f)
            close()
        }
        drawPath(path, color = Color.White)

        // Inner cutout for the 'S' swirl
        val innerHole = Path().apply {
            moveTo(w * 0.50f, h * 0.28f)
            cubicTo(w * 0.58f, h * 0.28f, w * 0.60f, h * 0.36f, w * 0.58f, h * 0.42f)
            cubicTo(w * 0.56f, h * 0.46f, w * 0.50f, h * 0.50f, w * 0.46f, h * 0.54f)
            cubicTo(w * 0.42f, h * 0.58f, w * 0.44f, h * 0.64f, w * 0.50f, h * 0.66f)
            cubicTo(w * 0.54f, h * 0.66f, w * 0.56f, h * 0.62f, w * 0.56f, h * 0.62f)
        }
        drawPath(
            innerHole,
            color = Color(0xFFFC8019),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Zomato: Authentic Zomato Crimson (#CB202D) with crisp white bold 'zomato' typography.
 */
@Composable
fun ZomatoLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFCB202D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "zomato",
                color = Color.White,
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

/**
 * McDonald's: Red background (#DA291C) with famous Golden Arches 'M' (#FFC72C).
 */
@Composable
fun McDonaldsLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFDA291C)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "M",
            color = Color(0xFFFFC72C),
            fontSize = (size.value * 0.65f).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif
        )
    }
}

/**
 * Domino's Pizza: Red & Blue domino tile with white pips.
 */
@Composable
fun DominosLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height

        // Top half blue
        drawRect(Color(0xFF006491), topLeft = Offset.Zero, size = Size(w, h * 0.5f))
        // Bottom half red
        drawRect(Color(0xFFE31837), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))

        // Center line
        drawLine(Color.White, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = w * 0.05f)

        // White pips
        val pipRadius = w * 0.08f
        drawCircle(Color.White, radius = pipRadius, center = Offset(w * 0.5f, h * 0.25f))
        drawCircle(Color.White, radius = pipRadius, center = Offset(w * 0.35f, h * 0.75f))
        drawCircle(Color.White, radius = pipRadius, center = Offset(w * 0.65f, h * 0.75f))
    }
}

/**
 * Starbucks: Deep Forest Green (#006241) with siren star.
 */
@Composable
fun StarbucksLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006241)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

// ==============================================================================
// 3. QUICK COMMERCE & GROCERY (Blinkit, Zepto, BigBasket)
// ==============================================================================

/**
 * Blinkit: Authentic Blinkit Yellow (#F8CB46) with dark green (#0C831F) bold 'blinkit'.
 */
@Composable
fun BlinkitLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8CB46)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "blink",
                color = Color(0xFF0C831F),
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "it",
                color = Color(0xFFE23744),
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Zepto: Authentic Deep Royal Purple (#2B0054) with hot pink-magenta (#FF3269) speed 'z'.
 */
@Composable
fun ZeptoLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B0054)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "zepto",
            color = Color(0xFFFF3269),
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
    }
}

/**
 * BigBasket: Deep Crimson Red (#E23744) with iconic 'bb'.
 */
@Composable
fun BigBasketLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE23744)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("b", color = Color(0xFF84C225), fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Black)
            Text("b", color = Color.White, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Instamart (Swiggy): Swiggy Orange with instant clock icon.
 */
@Composable
fun InstamartLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFC8019)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/**
 * DMart: Dark Green (#006A4E) with yellow cart.
 */
@Composable
fun DmartLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF006A4E)),
        contentAlignment = Alignment.Center
    ) {
        Text("D", color = Color(0xFFFFD100), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Black)
        Text("Mart", color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
    }
}

/**
 * Smart Supermarket / Smart Bazaar: Vibrant Blue (#004B91) with red accent dot & crisp white SMART.
 */
@Composable
fun SmartSupermarketLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF004B91)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SMART",
                    color = Color.White,
                    fontSize = (size.value * 0.26f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(size * 0.12f)
                        .clip(CircleShape)
                        .background(Color(0xFFED1C24))
                )
            }
            Text(
                text = "SUPERMARKET",
                color = Color(0xFFFFD100),
                fontSize = (size.value * 0.14f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ==============================================================================
// 4. TRAVEL & COMMUTE (Uber, Ola, Rapido)
// ==============================================================================

/**
 * Uber: Sleek Jet Black (#000000) with crisp white bold 'Uber' typography.
 */
@Composable
fun UberLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Uber",
            color = Color.White,
            fontSize = (size.value * 0.32f).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5).sp
        )
    }
}

/**
 * Ola: Black background with electric Lime Neon Green (#22C55E) ring and dot.
 */
@Composable
fun OlaLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.65f)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width * 0.35f
            drawCircle(
                color = Color(0xFF22C55E),
                radius = radius,
                center = center,
                style = Stroke(width = this.size.width * 0.16f)
            )
            drawCircle(
                color = Color.White,
                radius = radius * 0.45f,
                center = center
            )
        }
    }
}

/**
 * Rapido: Bright Yellow (#FACC15) with black bike speed icon.
 */
@Composable
fun RapidoLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFACC15)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.TwoWheeler, contentDescription = null, tint = Color.Black, modifier = Modifier.size(size * 0.65f))
    }
}

/**
 * IRCTC: Maroon (#8B0000) with train locomotive icon.
 */
@Composable
fun IrctcLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8B0000)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Train, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

// ==============================================================================
// 5. E-COMMERCE (Amazon, Flipkart, Myntra)
// ==============================================================================

/**
 * Amazon: Dark Obsidian (#131921) with famous golden-amber smile arrow curve (#FF9900).
 */
@Composable
fun AmazonLogo(size: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = this.size.width
        val h = this.size.height

        drawRect(Color(0xFF131921))

        // Golden smile arc curve
        val smilePath = Path().apply {
            moveTo(w * 0.22f, h * 0.60f)
            cubicTo(w * 0.40f, h * 0.78f, w * 0.60f, h * 0.78f, w * 0.76f, h * 0.60f)
        }
        drawPath(
            smilePath,
            color = Color(0xFFFF9900),
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        )

        // Arrowhead on right of smile
        val arrow = Path().apply {
            moveTo(w * 0.74f, h * 0.54f)
            lineTo(w * 0.84f, h * 0.62f)
            lineTo(w * 0.76f, h * 0.68f)
            close()
        }
        drawPath(arrow, color = Color(0xFFFF9900))
    }
}

@Composable
fun AmazonPayLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131921)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("amazon", color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
            Text("pay", color = Color(0xFFFF9900), fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Flipkart: Bright Yellow (#FFE500) with royal blue shopping bag & 'f'.
 */
@Composable
fun FlipkartLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFE500)),
        contentAlignment = Alignment.Center
    ) {
        Text("f", color = Color(0xFF2874F0), fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
    }
}

/**
 * Myntra: Multi-color gradient ribbon 'M'.
 */
@Composable
fun MyntraLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "M",
            fontSize = (size.value * 0.65f).sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFE11964)
        )
    }
}

@Composable
fun AjioLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C4152)),
        contentAlignment = Alignment.Center
    ) {
        Text("AJIO", color = Color.White, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ==============================================================================
// 6. UPI & WALLETS (Google Pay, PhonePe, Paytm, CRED)
// ==============================================================================

/**
 * Google Pay: Crisp white background with the official 4-color overlapping arc ribbons.
 */
@Composable
fun GPayLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.7f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.16f

            // Blue arc (top right)
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Green arc (bottom right)
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Yellow arc (bottom left)
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Red arc (top left)
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.2f, h * 0.2f),
                size = Size(w * 0.6f, h * 0.6f),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Super.money: Authentic deep violet backdrop with electric lime 'super' & white 'money'.
 */
@Composable
fun SuperMoneyLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1E1035), Color(0xFF3B0B5E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "super",
                color = Color(0xFFCCFF00),
                fontSize = (size.value * 0.28f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "money",
                color = Color.White,
                fontSize = (size.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

/**
 * PhonePe: PhonePe Deep Purple (#5F259F) with white Devanagari 'पे'.
 */
@Composable
fun PhonePeLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF5F259F)),
        contentAlignment = Alignment.Center
    ) {
        Text("पे", color = Color.White, fontSize = (size.value * 0.58f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Paytm: Pure white with dark blue 'Pay' + cyan 'tm'.
 */
@Composable
fun PaytmLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pay", color = Color(0xFF002E6E), fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Black)
            Text("tm", color = Color(0xFF00BAF2), fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * CRED: Matte Jet Black (#0F0F0F) with white minimalist geometric shield.
 */
@Composable
fun CredLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * BHIM UPI: Green & Orange arrows.
 */
@Composable
fun BhimLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF003366)),
        contentAlignment = Alignment.Center
    ) {
        Text("BHIM", color = Color(0xFF00BA51), fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * Pluxee (formerly Sodexo): Vibrant Emerald / Mint Green (#00EB5E) background with bold dark navy typography & brand badge.
 */
@Composable
fun PluxeeLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF002D36)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "plux",
                color = Color(0xFF00EB5E),
                fontSize = (size.value * 0.30f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "ee",
                color = Color.White,
                fontSize = (size.value * 0.30f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

// ==============================================================================
// 7. ENTERTAINMENT & TELECOM
// ==============================================================================

/**
 * Netflix: Black (#000000) with iconic Red ribbon 'N' (#E50914).
 */
@Composable
fun NetflixLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("N", color = Color(0xFFE50914), fontSize = (size.value * 0.65f).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
    }
}

/**
 * Spotify: Spotify Green (#1ED760) with black sound waves.
 */
@Composable
fun SpotifyLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1ED760)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.Black, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun YouTubeLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF0000)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/**
 * Airtel: Vibrant Red (#ED1B24) with white swoosh.
 */
@Composable
fun AirtelLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFED1B24)),
        contentAlignment = Alignment.Center
    ) {
        Text("airtel", color = Color.White, fontSize = (size.value * 0.28f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Jio: Deep Blue (#0A2885) with bold white 'Jio'.
 */
@Composable
fun JioLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A2885)),
        contentAlignment = Alignment.Center
    ) {
        Text("Jio", color = Color.White, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ViLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFED1C24)),
        contentAlignment = Alignment.Center
    ) {
        Text("Vi", color = Color(0xFFFFD100), fontSize = (size.value * 0.52f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ElectricityLogo(size: Dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0284C7)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.ElectricBolt, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun FuelBrandLogo(size: Dp, code: String, bg: Color, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = accent, modifier = Modifier.size(size * 0.35f))
            Text(code, color = Color.White, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Black)
        }
    }
}

// ==============================================================================
// 8. FALLBACK BADGES
// ==============================================================================

@Composable
fun GenericBankLogo(name: String, size: Dp) {
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "BK" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F2B48)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color(0xFF60A5FA),
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun GenericMerchantLogo(name: String, category: String, size: Dp) {
    val initial = name.firstOrNull()?.uppercase() ?: "M"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color(0xFF475569),
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
