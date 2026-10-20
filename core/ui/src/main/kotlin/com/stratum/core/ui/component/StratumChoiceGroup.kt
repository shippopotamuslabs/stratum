package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Vertical single-select list for longer option labels. Each row is a full-width tappable
 * surface with an accent stripe when selected. Tapping the active row clears the selection.
 */
@Composable
fun StratumChoiceGroup(
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        options.forEach { option ->
            StratumChoiceRow(
                label = option,
                selected = option == selectedOption,
                enabled = enabled,
                onClick = {
                    onOptionSelected(if (option == selectedOption) null else option)
                },
            )
        }
    }
}

@Composable
private fun StratumChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val rowShape = RoundedCornerShape(shapes.chipCorner)
    val borderColor = when {
        !enabled -> colors.borderSubtle
        selected -> colors.accent
        else -> colors.borderSubtle
    }
    val background = when {
        !enabled -> colors.surfaceSunken
        else -> colors.surfaceRaised
    }
    val labelColor = when {
        !enabled -> colors.textMuted
        selected -> colors.textPrimary
        else -> colors.textMuted
    }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinChoiceRowHeight)
            .clip(rowShape)
            .background(background)
            .border(shapes.borderWidth, borderColor, rowShape)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label + if (selected) ", selected" else ""
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(AccentStripeWidth)
                .heightIn(min = MinChoiceRowHeight)
                .background(if (selected && enabled) colors.accent else colors.surfaceRaised),
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
            style = StratumThemeAccessor.typography.bodyLarge,
            color = labelColor,
        )
    }
}

private val MinChoiceRowHeight = 48.dp
private val AccentStripeWidth = 4.dp
