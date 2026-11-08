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
import com.example.data.model.getDisplayTitle
import androidx.compose.foundation.clickable
import com.example.ui.theme.IndBlue
import com.example.ui.theme.IndBlueDark
import com.example.ui.theme.IndBlueLight
import com.example.ui.theme.IndBorderSubtle
import com.example.ui.theme.IndNavyHeader
import com.example.ui.theme.IndTextSecondary

fun debitSubcategoryTitle(item: DebitItem): String? {
    val catLabel = debitCategoryDisplayLabel(item)
    val resolvedTitle = item.getDisplayTitle()
    if (resolvedTitle.isBlank()) return null
    if (resolvedTitle.equals(catLabel, ignoreCase = true)) return null
    if (resolvedTitle.equals(item.category, ignoreCase = true)) return null
    return resolvedTitle
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

/**
 * Axio-style multiple tags row displayed underneath an expense/debit title.
 * Formats tags as modern, compact hashtag pills with clean typography and rounded borders.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AxioExpenseTagsRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 3,
    onTagClick: ((String) -> Unit)? = null
) {
    if (tags.isEmpty()) return

    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
    ) {
        val visibleTags = tags.take(maxVisible)
        visibleTags.forEach { rawTag ->
            val cleanTag = rawTag.trim().removePrefix("#").trim()
            if (cleanTag.isNotBlank()) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = IndBlueLight.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, IndBlue.copy(alpha = 0.35f)),
                    modifier = Modifier.then(
                        if (onTagClick != null) Modifier.clickable { onTagClick(cleanTag) } else Modifier
                    )
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "#$cleanTag",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndBlueDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (tags.size > maxVisible) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, IndBorderSubtle)
            ) {
                Text(
                    text = "+${tags.size - maxVisible}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = IndTextSecondary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

