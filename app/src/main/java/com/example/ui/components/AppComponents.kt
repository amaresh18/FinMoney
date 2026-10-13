package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ThemeConstants

/**
 * Standardized reusable UI components for INDmoney-styled design language.
 */
object AppComponents

// ==========================================
// 1. BUTTONS
// ==========================================

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    gradient: List<Color>? = null,
    backgroundColor: Color = ThemeConstants.Colors.IndBlue
) {
    val shape = RoundedCornerShape(ThemeConstants.Dimensions.RadiusPill)
    val brush = if (gradient != null && gradient.size >= 2) {
        Brush.horizontalGradient(gradient)
    } else {
        Brush.horizontalGradient(listOf(backgroundColor, backgroundColor))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(if (enabled) 2.dp else 0.dp, shape = shape)
            .clip(shape)
            .background(
                brush = if (enabled) brush else Brush.horizontalGradient(
                    listOf(
                        ThemeConstants.Colors.Border,
                        ThemeConstants.Colors.Border
                    )
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else ThemeConstants.Colors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = ThemeConstants.Typography.TitleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else ThemeConstants.Colors.TextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    backgroundColor: Color = ThemeConstants.Colors.IndBlueLight,
    contentColor: Color = ThemeConstants.Colors.IndBlue
) {
    val shape = RoundedCornerShape(ThemeConstants.Dimensions.RadiusPill)

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = shape,
        color = if (enabled) backgroundColor else ThemeConstants.Colors.CardSecondary,
        contentColor = if (enabled) contentColor else ThemeConstants.Colors.TextMuted
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else ThemeConstants.Colors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = ThemeConstants.Typography.TitleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    borderColor: Color = ThemeConstants.Colors.Border,
    textColor: Color = ThemeConstants.Colors.TextPrimary
) {
    val shape = RoundedCornerShape(ThemeConstants.Dimensions.RadiusPill)

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = shape,
        border = BorderStroke(1.dp, if (enabled) borderColor else ThemeConstants.Colors.BorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) textColor else ThemeConstants.Colors.TextMuted
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) textColor else ThemeConstants.Colors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = ThemeConstants.Typography.TitleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
fun AppIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = ThemeConstants.Colors.TextSecondary,
    backgroundColor: Color = ThemeConstants.Colors.CardSecondary,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

// ==========================================
// 2. INPUT FIELDS
// ==========================================

@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = ThemeConstants.Typography.BodyMedium) },
            placeholder = { Text(placeholder, style = ThemeConstants.Typography.BodyMedium.copy(color = ThemeConstants.Colors.TextMuted)) },
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isError) ThemeConstants.Colors.IndRed else ThemeConstants.Colors.TextSecondary
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(ThemeConstants.Dimensions.RadiusMedium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ThemeConstants.Colors.IndBlue,
                unfocusedBorderColor = ThemeConstants.Colors.Border,
                errorBorderColor = ThemeConstants.Colors.IndRed,
                focusedContainerColor = ThemeConstants.Colors.Surface,
                unfocusedContainerColor = ThemeConstants.Colors.Surface,
                focusedLabelColor = ThemeConstants.Colors.IndBlue,
                unfocusedLabelColor = ThemeConstants.Colors.TextSecondary
            ),
            textStyle = ThemeConstants.Typography.BodyLarge
        )
        val err = errorMessage
        if (isError && !err.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = err,
                style = ThemeConstants.Typography.BodySmall.copy(color = ThemeConstants.Colors.IndRed),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun AppDropdownField(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    getCategoryIcon: ((String) -> ImageVector)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        AppOutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            label = label,
            readOnly = true,
            leadingIcon = leadingIcon ?: getCategoryIcon?.invoke(selectedOption),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Option",
                        tint = ThemeConstants.Colors.TextSecondary
                    )
                }
            },
            modifier = Modifier.clickable { expanded = !expanded }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(ThemeConstants.Colors.Surface)
                .clip(RoundedCornerShape(ThemeConstants.Dimensions.RadiusMedium))
                .border(1.dp, ThemeConstants.Colors.Border, RoundedCornerShape(ThemeConstants.Dimensions.RadiusMedium))
        ) {
            options.forEach { option ->
                val optionIcon = getCategoryIcon?.invoke(option)
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (optionIcon != null) {
                                Icon(
                                    imageVector = optionIcon,
                                    contentDescription = null,
                                    tint = ThemeConstants.Colors.IndBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = option,
                                style = ThemeConstants.Typography.BodyLarge.copy(
                                    fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == selectedOption) ThemeConstants.Colors.IndBlue else ThemeConstants.Colors.TextPrimary
                                )
                            )
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AppDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Date",
    modifier: Modifier = Modifier,
    placeholder: String = "YYYY-MM-DD"
) {
    AppOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.DateRange,
        modifier = modifier
    )
}

// ==========================================
// 3. CARDS & CONTAINERS
// ==========================================

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ThemeConstants.Colors.Surface,
    borderColor: Color = ThemeConstants.Colors.Border,
    cornerRadius: Dp = ThemeConstants.Dimensions.RadiusLarge,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val cardModifier = if (onClick != null) {
        modifier
            .shadow(elevation, shape = shape)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(ThemeConstants.Dimensions.CardPadding)
    } else {
        modifier
            .shadow(elevation, shape = shape)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(ThemeConstants.Dimensions.CardPadding)
    }

    Column(modifier = cardModifier) {
        content()
    }
}

@Composable
fun AppSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ThemeConstants.Colors.IndBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ThemeConstants.Colors.IndBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    style = ThemeConstants.Typography.TitleLarge
                )
            }
            if (actionText != null && onActionClick != null) {
                Text(
                    text = actionText,
                    style = ThemeConstants.Typography.TitleSmall.copy(
                        color = ThemeConstants.Colors.IndBlue,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.clickable(onClick = onActionClick)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun AppMetricCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = ThemeConstants.Colors.IndGreenLight,
    badgeTextColor: Color = ThemeConstants.Colors.IndGreenDark,
    icon: ImageVector? = null
) {
    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ThemeConstants.Typography.BodyMedium.copy(color = ThemeConstants.Colors.TextSecondary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = amount,
                    style = ThemeConstants.Typography.DisplayMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = ThemeConstants.Typography.BodySmall.copy(color = ThemeConstants.Colors.TextMuted)
                    )
                }
            }
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = ThemeConstants.Typography.LabelSmall.copy(color = badgeTextColor)
                    )
                }
            } else if (icon != null) {
                AppHeaderBadge(icon = icon, size = 40.dp)
            }
        }
    }
}

// ==========================================
// 4. DIALOGS & OTHER ATOMIC COMPONENTS
// ==========================================

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    confirmButtonText: String = "Save",
    onConfirm: () -> Unit,
    dismissButtonText: String? = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(ThemeConstants.Dimensions.ScreenPaddingHorizontal),
            shape = RoundedCornerShape(ThemeConstants.Dimensions.RadiusDialog),
            color = ThemeConstants.Colors.Surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ThemeConstants.Dimensions.DialogPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (icon != null) {
                            AppHeaderBadge(icon = icon, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                text = title,
                                style = ThemeConstants.Typography.TitleLarge
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = ThemeConstants.Typography.BodySmall
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ThemeConstants.Colors.TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Body Content
                content()

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (dismissButtonText != null) {
                        AppOutlinedButton(
                            text = dismissButtonText,
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppPrimaryButton(
                        text = confirmButtonText,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeaderBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = ThemeConstants.Colors.IndBlueLight,
    iconTint: Color = ThemeConstants.Colors.IndBlue
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
fun AppChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ThemeConstants.Dimensions.RadiusSmall))
            .background(
                if (isSelected) ThemeConstants.Colors.IndBlueLight else ThemeConstants.Colors.CardSecondary
            )
            .border(
                1.dp,
                if (isSelected) ThemeConstants.Colors.IndBlue else ThemeConstants.Colors.Border,
                RoundedCornerShape(ThemeConstants.Dimensions.RadiusSmall)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) ThemeConstants.Colors.IndBlue else ThemeConstants.Colors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = ThemeConstants.Typography.LabelMedium.copy(
                    color = if (isSelected) ThemeConstants.Colors.IndBlue else ThemeConstants.Colors.TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    color: Color = ThemeConstants.Colors.BorderSubtle,
    thickness: Dp = 1.dp
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/**
 * A responsive container that automatically adapts sizing, max-width constraints,
 * and centering based on the user's screen size (compact vs medium/expanded screens).
 */
@Composable
fun AdaptiveScreenContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 840.dp,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}
