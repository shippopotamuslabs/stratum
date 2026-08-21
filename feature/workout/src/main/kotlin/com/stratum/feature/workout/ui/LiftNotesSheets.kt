package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTextFieldOptions
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Matches [com.stratum.domain.lift.MAX_LIFT_NOTE_LENGTH]; keep in sync (UI must not import domain). */
private const val LIFT_NOTE_FIELD_MAX_LENGTH = 2000

internal enum class LiftNoteTab {
    Session,
    Workout,
    Lift,
}

internal sealed interface LiftOptionsSheetMode {
    data object Options : LiftOptionsSheetMode
    data class ManageNotes(val tab: LiftNoteTab) : LiftOptionsSheetMode
}

@Composable
internal fun ManageLiftNotesContent(
    lift: RenderedLiftUi,
    initialTab: LiftNoteTab,
    loadSessionNote: suspend (workoutLiftId: String) -> String,
    onSave: (LiftNotesSaveRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember(lift.workoutLiftId, initialTab) { mutableStateOf(initialTab) }
    val loadedWorkout = lift.workoutPinnedNote.orEmpty()
    val loadedLiftNote = lift.liftPinnedNote.orEmpty()
    var loadedSession by remember(lift.workoutLiftId) { mutableStateOf("") }
    var sessionDraft by remember(lift.workoutLiftId) { mutableStateOf("") }
    var workoutDraft by remember(lift.workoutLiftId, loadedWorkout) { mutableStateOf(loadedWorkout) }
    var liftDraft by remember(lift.workoutLiftId, loadedLiftNote) { mutableStateOf(loadedLiftNote) }

    LaunchedEffect(lift.workoutLiftId) {
        val body = loadSessionNote(lift.workoutLiftId)
        if (sessionDraft == loadedSession) {
            loadedSession = body
            sessionDraft = body
        } else {
            loadedSession = body
        }
    }

    val sessionLabels =
        listOf(
            stringResource(CoreUiR.string.lift_notes_tab_session),
            stringResource(CoreUiR.string.lift_notes_tab_workout),
            stringResource(CoreUiR.string.lift_notes_tab_lift),
        )
    val pin = CoreUiR.drawable.ic_push_pin
    val dirty =
        sessionDraft != loadedSession ||
            workoutDraft != loadedWorkout ||
            liftDraft != loadedLiftNote

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            segments = sessionLabels,
            selectedIndex = selectedTab.ordinal,
            onSelectedIndexChange = { index ->
                selectedTab = LiftNoteTab.entries[index]
            },
            leadingIconRes = listOf(null, pin, pin),
        )
        val (draft, onDraftChange, caption) =
            when (selectedTab) {
                LiftNoteTab.Session ->
                    Triple(
                        sessionDraft,
                        { value: String -> sessionDraft = value.take(LIFT_NOTE_FIELD_MAX_LENGTH) },
                        stringResource(CoreUiR.string.lift_notes_caption_session),
                    )
                LiftNoteTab.Workout ->
                    Triple(
                        workoutDraft,
                        { value: String -> workoutDraft = value.take(LIFT_NOTE_FIELD_MAX_LENGTH) },
                        stringResource(CoreUiR.string.lift_notes_caption_workout),
                    )
                LiftNoteTab.Lift ->
                    Triple(
                        liftDraft,
                        { value: String -> liftDraft = value.take(LIFT_NOTE_FIELD_MAX_LENGTH) },
                        stringResource(CoreUiR.string.lift_notes_caption_lift),
                    )
            }
        StratumTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = stringResource(CoreUiR.string.lift_notes_field_label),
            singleLine = false,
            options =
                StratumTextFieldOptions(
                    minLines = 4,
                    maxLines = 8,
                    showClearButton = true,
                    supportingText = caption,
                ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        StratumButton(
            text = stringResource(CoreUiR.string.lift_notes_save),
            enabled = dirty,
            onClick = {
                onSave(
                    LiftNotesSaveRequest(
                        workoutLiftId = lift.workoutLiftId,
                        liftId = lift.liftId,
                        sessionDraft = sessionDraft,
                        workoutDraft = workoutDraft,
                        liftDraft = liftDraft,
                        loadedSession = loadedSession,
                        loadedWorkout = loadedWorkout,
                        loadedLift = loadedLiftNote,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun PastSessionNotesSheet(
    notes: List<PastSessionNoteUi>,
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                notes.forEach { note ->
                    PastSessionNoteRow(note)
                }
            }
        }
    }
}

@Composable
private fun PastSessionNoteRow(note: PastSessionNoteUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StratumText(
                text = note.dateLabel,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
            if (note.workoutName.isNotBlank()) {
                StratumText(
                    text = note.workoutName,
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
        }
        StratumText(
            text = note.body,
            style = StratumThemeAccessor.typography.bodyLarge,
        )
    }
}
