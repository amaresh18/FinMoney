package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomExpenseTag
import com.example.data.model.DebitItem
import com.example.ui.theme.IndBlueLight
import com.example.ui.theme.IndBorderSubtle
import com.example.ui.theme.IndNavyHeader

fun debitSubcategoryTitle(item: DebitItem): String? {
    val catLabel = debitCategoryDisplayLabel(item)
    val title = item.title.trim()
    if (title.isBlank()) return null
    if (title.equals(catLabel, ignoreCase = true)) return null
    if (title.equals(item.category, ignoreCase = true)) return null
    return title
}

@Composable
fun ExpenseTagBadge(
    visual: ExpenseTagVisual,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp,
    monogramSize: TextUnit = 13.sp,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(shape)
            .background(visual.backgroundColor)
            .border(1.dp, IndBorderSubtle, shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            visual.brandIconRes != null -> {
                Image(
                    painter = painterResource(visual.brandIconRes),
                    contentDescription = visual.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
            !visual.storedIconName.isNullOrBlank() -> {
                Icon(
                    imageVector = categoryIconFromStoredName(visual.storedIconName),
                    contentDescription = visual.displayName,
                    tint = visual.foregroundColor,
                    modifier = Modifier.size(badgeSize * 0.48f)
                )
            }
            else -> {
                Text(
                    text = visual.monogram ?: visual.displayName.take(2).uppercase(),
                    color = visual.foregroundColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = monogramSize,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
fun DebitExpenseLeadingBadge(
    item: DebitItem,
    customCategories: List<com.example.data.model.CustomCategory>,
    customExpenseTags: List<CustomExpenseTag> = emptyList(),
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp
) {
    val subTitle = debitSubcategoryTitle(item)
    val tagVisual = subTitle?.let { title ->
        resolveExpenseTagVisual(title, item.category, customExpenseTags)
            ?: ExpenseTagVisual(
                displayName = primaryTagLabel(title),
                monogram = primaryTagLabel(title).split(Regex("\\s+")).take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .joinToString("").ifBlank { title.take(2).uppercase() },
                backgroundColor = IndBlueLight,
                foregroundColor = IndNavyHeader,
                isKnownBrand = false
            )
    }
    if (tagVisual != null) {
        ExpenseTagBadge(
            visual = tagVisual,
            modifier = modifier,
            badgeSize = badgeSize
        )
    } else {
        val categoryLabel = debitCategoryDisplayLabel(item)
        val storedIcon = debitItemStoredIcon(item, customCategories)
        val customMeta = customCategoryForDebit(item, customCategories)
        CategoryIconBadge(
            categoryKey = categoryLabel,
            modifier = modifier,
            badgeSize = badgeSize,
            storedIconName = storedIcon,
            customColor = customMeta?.colorHex?.let { customCategoryTint(it) }
        )
    }
}
