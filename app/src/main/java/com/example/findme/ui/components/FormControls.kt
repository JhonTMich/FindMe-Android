package com.example.findme.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.findme.ui.theme.*

/** Corner radius shared by every text field, select field and menu so forms read as one system. */
val FieldShape = RoundedCornerShape(16.dp)
private val MenuShape = RoundedCornerShape(18.dp)
private val FieldContainer = AppBackground.copy(alpha = .35f)
private val FieldContainerFocused = AppBackground.copy(alpha = .55f)

/** Colours for every [OutlinedTextField] in the app; keeps borders, labels and icons consistent. */
@Composable
fun nearTextFieldColors(): TextFieldColors {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, disabledTextColor = TextMuted,
        focusedContainerColor = FieldContainerFocused, unfocusedContainerColor = FieldContainer,
        disabledContainerColor = FieldContainer.copy(alpha = .2f), errorContainerColor = FieldContainer,
        cursorColor = primary, errorCursorColor = error,
        focusedBorderColor = primary, unfocusedBorderColor = CardBorder,
        disabledBorderColor = CardBorder.copy(alpha = .5f), errorBorderColor = error,
        focusedLabelColor = primary, unfocusedLabelColor = TextSecondary, disabledLabelColor = TextMuted, errorLabelColor = error,
        focusedLeadingIconColor = primary, unfocusedLeadingIconColor = TextSecondary, disabledLeadingIconColor = TextMuted,
        focusedTrailingIconColor = TextSecondary, unfocusedTrailingIconColor = TextSecondary, errorTrailingIconColor = error,
        focusedPlaceholderColor = TextMuted, unfocusedPlaceholderColor = TextMuted,
        focusedSupportingTextColor = TextMuted, unfocusedSupportingTextColor = TextMuted, errorSupportingTextColor = error
    )
}

/**
 * A read-only field that opens a menu or a picker. It mirrors the outlined text field
 * (height, radius, label above the value) instead of looking like a button.
 */
@Composable
fun SelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector = Icons.Default.ExpandMore
) {
    val primary = MaterialTheme.colorScheme.primary
    val border by animateColorAsState(if (expanded) primary else CardBorder, tween(180), label = "selectBorder")
    val width by animateDpAsState(if (expanded) 2.dp else 1.dp, tween(180), label = "selectBorderWidth")
    val rotation by animateFloatAsState(if (expanded && trailing == Icons.Default.ExpandMore) 180f else 0f, tween(200), label = "selectChevron")
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(FieldShape)
            .background(if (expanded) FieldContainerFocused else FieldContainer)
            .border(width, border, FieldShape)
            .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading?.let { Icon(it, null, tint = if (expanded) primary else TextSecondary, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                color = if (expanded) primary else TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (enabled) TextPrimary else TextMuted)
        }
        Icon(trailing, null, tint = if (expanded) primary else TextMuted,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
    }
}

/**
 * Wraps a [SelectField] with a menu as wide as the field, so the options line up under it.
 * [content] receives a callback that closes the menu.
 */
@Composable
fun SelectMenuField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { width = with(density) { it.width.toDp() } }) {
        SelectField(label, value, onClick = { expanded = true }, expanded = expanded, enabled = enabled, leading = leading)
        SelectMenu(expanded, onDismiss = { expanded = false }, width = width) { content { expanded = false } }
    }
}

@Composable
fun SelectMenu(expanded: Boolean, onDismiss: () -> Unit, width: Dp, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = maxOf(width, 180.dp)).heightIn(max = 360.dp),
        offset = DpOffset(0.dp, 6.dp), shape = MenuShape, containerColor = CardBg,
        tonalElevation = 0.dp, shadowElevation = 12.dp, border = BorderStroke(1.dp, CardBorder)
    ) {
        Spacer(Modifier.height(2.dp))
        content()
        Spacer(Modifier.height(2.dp))
    }
}

/** A menu row with a clear selected state: tinted background, accent text and a check mark. */
@Composable
fun SelectMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    showCheck: Boolean = true
) {
    val primary = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) primary else TextPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        onClick = onClick,
        leadingIcon = leading,
        trailingIcon = if (selected && showCheck) { { Icon(Icons.Default.Check, null, tint = primary, modifier = Modifier.size(20.dp)) } } else null,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) primary.copy(alpha = .12f) else Color.Transparent)
            .semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 12.dp)
    )
}

/** Chip colours shared by single-choice rows across the app. */
@Composable
fun nearChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = FieldContainer,
    labelColor = TextSecondary,
    iconColor = TextSecondary,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
fun nearChipBorder(selected: Boolean): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = true, selected = selected,
    borderColor = CardBorder, selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .55f),
    borderWidth = 1.dp, selectedBorderWidth = 1.dp
)

/** Switches with a visible "off" state: the default thumb almost disappeared on the night background. */
@Composable
fun nearSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = TextSecondary,
    uncheckedTrackColor = FieldContainer,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline
)

/**
 * Two or three mutually exclusive options shown as one segmented control, so the choice reads as
 * a switch between modes rather than as loose chips.
 */
@Composable
fun <T> SegmentedChoice(values: List<T>, selected: T, change: (T) -> Unit, modifier: Modifier = Modifier, label: (T) -> String) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FieldContainer)
        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            val active = value == selected
            val background by animateColorAsState(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                tween(180), label = "segment")
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(background)
                .then(if (active) Modifier.border(1.dp, primary.copy(alpha = .45f), RoundedCornerShape(12.dp)) else Modifier)
                .selectable(active, role = Role.RadioButton, onClick = { change(value) })
                .padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label(value), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (active) primary else TextSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

/** A single-choice chip with the shared colours and a check mark on the active option. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    FilterChip(selected = selected, onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp),
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leading ?: if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null,
        shape = RoundedCornerShape(12.dp), colors = nearChipColors(), border = nearChipBorder(selected))
}
