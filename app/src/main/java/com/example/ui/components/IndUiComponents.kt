package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

/**
 * Single Centralized Entity for All App Elements' Design Properties & Common Reusable Components.
 * Follows INDmoney top-grade premium product design standards.
 * Ensures all popups and UI elements auto-size properly and align responsively across any screen size.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndDialog(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.Star,
    iconTint: Color = AppTheme.colors.primary,
    headerGradient: List<Color>? = null,
    testTag: String = "ind_dialog",
    confirmButtonText: String? = "Save",
    confirmButtonTag: String = "dialog_confirm_button",
    onConfirm: (() -> Unit)? = null,
    dismissButtonText: String? = "Cancel",
    onDismissClicked: (() -> Unit)? = onDismiss,
    isConfirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag(testTag),
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, AppTheme.colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = if (headerGradient != null) {
                                Brush.linearGradient(headerGradient)
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        AppTheme.colors.primaryLight.copy(alpha = 0.4f),
                                        AppTheme.colors.surface
                                    )
                                )
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(iconTint.copy(alpha = 0.15f))
                                    .border(1.dp, iconTint.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = AppTheme.typography.titleLarge,
                                    color = AppTheme.colors.secondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = AppTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.cardSecondary)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = AppTheme.colors.borderSubtle)

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    content()
                }

                // Actions Footer
                if (confirmButtonText != null || dismissButtonText != null) {
                    HorizontalDivider(color = AppTheme.colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButtonText != null && onDismissClicked != null) {
                            IndSecondaryButton(
                                text = dismissButtonText,
                                onClick = onDismissClicked,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                        }

                        if (confirmButtonText != null && onConfirm != null) {
                            IndPrimaryButton(
                                text = confirmButtonText,
                                onClick = onConfirm,
                                enabled = isConfirmEnabled,
                                testTag = confirmButtonTag,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard INDmoney Premium Text Field
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val baseModifier = if (testTag != null) Modifier.testTag(testTag) else Modifier

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = baseModifier.fillMaxWidth(),
            label = { Text(label, style = AppTheme.typography.bodySmall) },
            placeholder = if (placeholder != null) {
                { Text(placeholder, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textMuted) }
            } else null,
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = label,
                        tint = if (isError) AppTheme.colors.error else AppTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.primary,
                unfocusedBorderColor = AppTheme.colors.border,
                focusedLabelColor = AppTheme.colors.primary,
                unfocusedLabelColor = AppTheme.colors.textSecondary,
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface,
                cursorColor = AppTheme.colors.primary,
                errorBorderColor = AppTheme.colors.error,
                errorLabelColor = AppTheme.colors.error
            )
        )

        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = AppTheme.typography.caption,
                color = AppTheme.colors.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Standard INDmoney Category Dropdown Selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndCategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    categories: List<String>,
    label: String = "Category",
    testTag: String = "category_dropdown"
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        val (icon, bgColor, tintColor) = getCategoryStyling(selectedCategory)

        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = AppTheme.typography.bodySmall) },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = selectedCategory,
                        tint = tintColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.primary,
                unfocusedBorderColor = AppTheme.colors.border,
                focusedLabelColor = AppTheme.colors.primary,
                unfocusedLabelColor = AppTheme.colors.textSecondary,
                focusedContainerColor = AppTheme.colors.surface,
                unfocusedContainerColor = AppTheme.colors.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag(testTag)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 260.dp)
                .background(AppTheme.colors.surface)
        ) {
            categories.forEach { cat ->
                val (catIcon, catBg, catTint) = getCategoryStyling(cat)
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(catBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = cat,
                                    tint = catTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = cat,
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = if (cat.equals(selectedCategory, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Standard Primary Pill Button
 */
@Composable
fun IndPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    backgroundColor: Color = AppTheme.colors.primary,
    contentColor: Color = Color.White,
    testTag: String? = null
) {
    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = baseModifier
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Standard Secondary Button
 */
@Composable
fun IndSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = baseModifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AppTheme.colors.textPrimary
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
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = AppTheme.typography.labelLarge,
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Standard Section Card Wrapper for Grouping Form Fields / Parameters
 */
@Composable
fun IndSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    backgroundColor: Color = AppTheme.colors.cardSecondary,
    borderColor: Color = AppTheme.colors.borderSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = AppTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.secondary
                    )
                }
            }
            content()
        }
    }
}
