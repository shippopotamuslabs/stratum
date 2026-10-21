package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

enum class LiftNoteTab {
    Session,
    Workout,
    Lift,
}

internal sealed interface LiftOptionsSheetMode {
    data object Options : LiftOptionsSheetMode
    data class ManageNotes(val tab: LiftNoteTab) : LiftOptionsSheetMode
}

/**
 * Sheet summary for the hybrid notes flow: per-tab counts with a first-line preview and
 * an entry point into the full-screen [ManageNotesScreen], which owns all editing.
 */
@Composable
internal fun ManageNotesSummaryContent(
    lift: RenderedLiftUi,
    initialTab: LiftNoteTab,
    loadNoteLists: suspend (workoutLiftId: String, liftId: String) -> NoteListsUi,
    onEditNotes: (LiftNoteTab) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Preview-only initial lists (screenshot tests capture before `LaunchedEffect` loads
     * complete). Production callers leave this null; the load effect still refreshes.
     */
    initialLists: NoteListsUi? = null,
) {
    var selectedTab by remember(lift.workoutLiftId, initialTab) { mutableStateOf(initialTab) }
    var lists by remember(lift.workoutLiftId, lift.liftId) {
        mutableStateOf(initialLists)
    }

    LaunchedEffect(lift.workoutLiftId, lift.liftId) {
        lists = loadNoteLists(lift.workoutLiftId, lift.liftId)
    }

    val loaded = lists
    val tabLists =
        listOf(
            loaded?.session.orEmpty(),
            loaded?.workout.orEmpty(),
            loaded?.lift.orEmpty(),
        )
    val pin = CoreUiR.drawable.ic_push_pin

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            StratumIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_close),
                    contentDescription = stringResource(CoreUiR.string.lift_notes_close_cd),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
        }
        StratumSegmentedControl(
            segments =
                listOf(
                    summaryLabel(LiftNoteTab.Session, tabLists[0].size),
                    summaryLabel(LiftNoteTab.Workout, tabLists[1].size),
                    summaryLabel(LiftNoteTab.Lift, tabLists[2].size),
                ),
            selectedIndex = selectedTab.ordinal,
            onSelectedIndexChange = { index ->
                selectedTab = LiftNoteTab.entries[index]
            },
            leadingIconRes = listOf(null, pin, pin),
            modified = listOf(false, false, false),
        )
        val preview = tabLists[selectedTab.ordinal]
        if (preview.isEmpty()) {
            StratumText(
                text = stringResource(CoreUiR.string.lift_notes_summary_empty),
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            ) {
                preview.take(2).forEach { note ->
                    StratumText(
                        text = note.body.lines().firstOrNull().orEmpty(),
                        style = StratumThemeAccessor.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        StratumButton(
            text = stringResource(CoreUiR.string.lift_notes_edit_notes),
            onClick = { onEditNotes(selectedTab) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun summaryLabel(tab: LiftNoteTab, count: Int): String {
    val base =
        when (tab) {
            LiftNoteTab.Session -> stringResource(CoreUiR.string.lift_notes_tab_session)
            LiftNoteTab.Workout -> stringResource(CoreUiR.string.lift_notes_tab_workout)
            LiftNoteTab.Lift -> stringResource(CoreUiR.string.lift_notes_tab_lift)
        }
    return if (count == 0) base else "$base ($count)"
}

@Composable
internal fun PastSessionNotesSheet(
    notes: List<PastSessionNotesGroupUi>,
    onDismiss: () -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(CoreUiR.string.lift_notes_past_title),
    ) {
        if (notes.isEmpty()) {
            StratumText(
                text = stringResource(CoreUiR.string.lift_notes_past_empty),
                muted = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
            ) {
                notes.forEach { group ->
                    PastSessionNotesGroup(group)
                }
            }
        }
    }
}

@Composable
private fun PastSessionNotesGroup(group: PastSessionNotesGroupUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StratumText(
                text = group.dateLabel,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            if (group.workoutName.isNotBlank()) {
                StratumText(
                    text = group.workoutName,
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
        }
        group.bodies.forEach { body ->
            StratumText(
                text = body,
                style = StratumThemeAccessor.typography.bodyLarge,
            )
        }
    }
}
