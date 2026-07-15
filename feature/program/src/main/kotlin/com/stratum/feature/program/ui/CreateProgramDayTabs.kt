package com.stratum.feature.program.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.R

private val DayTabBarHeight = 48.dp
private val DayTabIndicatorHeight = 2.dp

@Composable
internal fun DayTabsRow(
    days: List<CreateProgramDayUi>,
    selectedDayId: String?,
    canAddDay: Boolean,
    onSelectDay: (String) -> Unit,
    onAddDay: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val colors = StratumThemeAccessor.colors
    val borderWidth = StratumThemeAccessor.shapes.borderWidth

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DayTabBarHeight)
                .drawBehind {
                    val y = size.height - borderWidth.toPx() / 2f
                    drawLine(
                        color = colors.borderSubtle,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = borderWidth.toPx(),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEach { day ->
                DayTab(
                    name = day.name,
                    selected = day.id == selectedDayId,
                    onSelect = { onSelectDay(day.id) },
                )
            }
            if (canAddDay) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .clickable(onClick = onAddDay)
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.create_program_add_day) + " +",
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTab(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors

    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .defaultMinSize(minWidth = 72.dp)
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                }
                .clickable(onClick = onSelect)
                .drawBehind {
                    if (selected) {
                        val stroke = DayTabIndicatorHeight.toPx()
                        val y = size.height - stroke / 2f
                        drawLine(
                            color = colors.accent,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke,
                        )
                    }
                }
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StratumText(
            text = name,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = !selected,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "Day tabs light", showBackground = true)
@Composable
internal fun DayTabsRowPreviewLight() {
    StratumTheme(darkTheme = false) {
        DayTabsRowPreviewContent()
    }
}

@Preview(name = "Day tabs dark", showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun DayTabsRowPreviewDark() {
    StratumTheme(darkTheme = true) {
        DayTabsRowPreviewContent()
    }
}

@Composable
private fun DayTabsRowPreviewContent() {
    var days by remember {
        mutableStateOf(
            listOf(
                CreateProgramDayUi(id = "a", name = "Workout A", position = 0, isRest = false, editState = null),
                CreateProgramDayUi(id = "r1", name = "Rest", position = 1, isRest = true, editState = null),
                CreateProgramDayUi(id = "b", name = "Workout B", position = 2, isRest = false, editState = null),
                CreateProgramDayUi(id = "r2", name = "Rest", position = 3, isRest = true, editState = null),
            ),
        )
    }
    var selectedId by remember { mutableStateOf("a") }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .padding(vertical = 8.dp),
    ) {
        DayTabsRow(
            days = days,
            selectedDayId = selectedId,
            canAddDay = true,
            onSelectDay = { selectedId = it },
            onAddDay = {
                val next = days.size + 1
                days = days +
                    CreateProgramDayUi(
                        id = "new-$next",
                        name = "Workout $next",
                        position = days.size,
                        isRest = false,
                        editState = null,
                    )
            },
        )
    }
}
