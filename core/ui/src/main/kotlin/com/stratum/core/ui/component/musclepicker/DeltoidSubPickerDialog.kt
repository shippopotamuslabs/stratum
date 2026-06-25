package com.stratum.core.ui.component.musclepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumFilterChip
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
internal fun DeltoidSubPickerDialog(
    selectedLabels: Set<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = StratumThemeAccessor.shapes.panel,
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = "Select Shoulder Focus",
                style = StratumThemeAccessor.typography.titleLarge,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DELTOID_LABELS.forEach { (_, label) ->
                    val isSelected = label in selectedLabels
                    StratumFilterChip(
                        label = label,
                        selected = isSelected,
                        enabled = enabled || isSelected,
                        onSelectedChange = { onToggle(label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "$label, ${if (isSelected) "selected" else "not selected"}"
                            },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                StratumButton(text = "Done", onClick = onDismiss)
            }
        }
    }
}
