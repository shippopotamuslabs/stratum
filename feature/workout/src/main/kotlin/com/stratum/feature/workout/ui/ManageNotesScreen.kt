package com.stratum.feature.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTextFieldOptions
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import java.util.UUID

/** Matches [com.stratum.domain.lift.MAX_LIFT_NOTE_LENGTH]; keep in sync (UI must not import domain). */
private const val LIFT_NOTE_FIELD_MAX_LENGTH = 2000

/**
 * One editable row. [key] is stable across reorder/delete; [id] is the persisted row id
 * (null for appended drafts). Bodies are compared trimmed of blanks only at save time.
 */
data class EditableNote(
    val key: String,
    val id: String? = null,
    val body: String = "",
)

internal fun editableFromLoaded(loaded: List<NoteDraftUi>): List<EditableNote> =
    loaded.mapIndexed { index, note ->
        EditableNote(
            key = note.id ?: "new-$index",
            id = note.id,
            body = note.body,
        )
    }

/** True when the drafts differ from [loaded] after dropping blank drafts. */
internal fun noteDraftsDirty(loaded: List<NoteDraftUi>, drafts: List<EditableNote>): Boolean {
    val clean = drafts.filter { it.body.isNotBlank() }
    if (clean.size != loaded.size) return true
    return clean.zip(loaded).any { (draft, original) ->
        draft.id != original.id || draft.body != original.body
    }
}

internal fun editableToDrafts(drafts: List<EditableNote>): List<NoteDraftUi> =
    drafts.map { NoteDraftUi(id = it.id, body = it.body) }

/**
 * Full-screen multi-note editor (hybrid step two — the sheet stays as entry/summary).
 * Stable scroll + IME under [StratumScaffold]; Save is enabled only when a tab is dirty.
 */
@Composable
fun ManageNotesScreen(
    target: EditingNotesTarget,
    liftNotes: WorkoutLiftNotesActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(target.workoutLiftId, target.liftId) {
        mutableStateOf<NoteListsUi?>(null)
    }
    var selectedTab by remember(target) { mutableStateOf(target.tab) }
    var sessionDrafts by remember(target.workoutLiftId) {
        mutableStateOf(emptyList<EditableNote>())
    }
    var workoutDrafts by remember(target.workoutLiftId) {
        mutableStateOf(emptyList<EditableNote>())
    }
    var liftDrafts by remember(target.workoutLiftId) {
        mutableStateOf(emptyList<EditableNote>())
    }

    LaunchedEffect(target.workoutLiftId, target.liftId) {
        val lists = liftNotes.onLoadNoteLists(target.workoutLiftId, target.liftId)
        loaded = lists
        sessionDrafts = editableFromLoaded(lists.session)
        workoutDrafts = editableFromLoaded(lists.workout)
        liftDrafts = editableFromLoaded(lists.lift)
    }

    BackHandler(onBack = onClose)

    val loadedLists = loaded
    val currentDrafts =
        when (selectedTab) {
            LiftNoteTab.Session -> sessionDrafts
            LiftNoteTab.Workout -> workoutDrafts
            LiftNoteTab.Lift -> liftDrafts
        }
    val setDrafts: (List<EditableNote>) -> Unit =
        when (selectedTab) {
            LiftNoteTab.Session -> ({ sessionDrafts = it })
            LiftNoteTab.Workout -> ({ workoutDrafts = it })
            LiftNoteTab.Lift -> ({ liftDrafts = it })
        }
    val sessionDirty = loadedLists?.let { noteDraftsDirty(it.session, sessionDrafts) } ?: false
    val workoutDirty = loadedLists?.let { noteDraftsDirty(it.workout, workoutDrafts) } ?: false
    val liftDirty = loadedLists?.let { noteDraftsDirty(it.lift, liftDrafts) } ?: false
    val isDirty = sessionDirty || workoutDirty || liftDirty

    StratumScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            StratumTopBar(
                title = target.liftName,
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onClose,
                        contentDescription = stringResource(CoreUiR.string.lift_notes_back_cd),
                    )
                },
                actions = {
                    StratumTopBarTextButton(
                        text = stringResource(CoreUiR.string.lift_notes_save),
                        enabled = isDirty,
                        onClick = {
                            val lists = loadedLists
                            if (lists != null) {
                                liftNotes.onSave(
                                    LiftNoteListsSaveRequest(
                                        workoutLiftId = target.workoutLiftId,
                                        liftId = target.liftId,
                                        sessionNotes =
                                            editableToDrafts(sessionDrafts)
                                                .takeIf { noteDraftsDirty(lists.session, sessionDrafts) },
                                        workoutNotes =
                                            editableToDrafts(workoutDrafts)
                                                .takeIf { noteDraftsDirty(lists.workout, workoutDrafts) },
                                        liftNotes =
                                            editableToDrafts(liftDrafts)
                                                .takeIf { noteDraftsDirty(lists.lift, liftDrafts) },
                                    ),
                                )
                                onClose()
                            }
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StratumSegmentedControl(
                segments =
                    listOf(
                        tabLabel(LiftNoteTab.Session, sessionDrafts.size),
                        tabLabel(LiftNoteTab.Workout, workoutDrafts.size),
                        tabLabel(LiftNoteTab.Lift, liftDrafts.size),
                    ),
                selectedIndex = selectedTab.ordinal,
                onSelectedIndexChange = { index -> selectedTab = LiftNoteTab.entries[index] },
                leadingIconRes = listOf(null, CoreUiR.drawable.ic_push_pin, CoreUiR.drawable.ic_push_pin),
                modified = listOf(sessionDirty, workoutDirty, liftDirty),
            )
            StratumText(
                text =
                    when (selectedTab) {
                        LiftNoteTab.Session -> stringResource(CoreUiR.string.lift_notes_caption_session)
                        LiftNoteTab.Workout -> stringResource(CoreUiR.string.lift_notes_caption_workout)
                        LiftNoteTab.Lift -> stringResource(CoreUiR.string.lift_notes_caption_lift)
                    },
                muted = true,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentDrafts.isEmpty()) {
                    item(key = "empty") {
                        StratumText(
                            text = stringResource(CoreUiR.string.lift_notes_summary_empty),
                            muted = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(currentDrafts, key = { it.key }) { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        StratumTextField(
                            value = draft.body,
                            onValueChange = { value ->
                                setDrafts(
                                    currentDrafts.map { row ->
                                        if (row.key == draft.key) {
                                            row.copy(body = value.take(LIFT_NOTE_FIELD_MAX_LENGTH))
                                        } else {
                                            row
                                        }
                                    },
                                )
                            },
                            label = stringResource(CoreUiR.string.lift_notes_field_label),
                            singleLine = false,
                            options =
                                StratumTextFieldOptions(
                                    minLines = 4,
                                    maxLines = 8,
                                    showClearButton = true,
                                ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f),
                        )
                        StratumIconButton(
                            onClick = { setDrafts(currentDrafts.filterNot { it.key == draft.key }) },
                        ) {
                            Icon(
                                painter = painterResource(CoreUiR.drawable.ic_delete),
                                contentDescription = stringResource(CoreUiR.string.lift_notes_delete_note_cd),
                                tint = StratumThemeAccessor.colors.textPrimary,
                            )
                        }
                    }
                }
                item(key = "add") {
                    StratumOutlinedButton(
                        text = stringResource(CoreUiR.string.lift_notes_add_note),
                        onClick = {
                            setDrafts(
                                currentDrafts + EditableNote(key = UUID.randomUUID().toString()),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: LiftNoteTab, count: Int): String {
    val base =
        when (tab) {
            LiftNoteTab.Session -> stringResource(CoreUiR.string.lift_notes_tab_session)
            LiftNoteTab.Workout -> stringResource(CoreUiR.string.lift_notes_tab_workout)
            LiftNoteTab.Lift -> stringResource(CoreUiR.string.lift_notes_tab_lift)
        }
    return if (count == 0) base else "$base ($count)"
}
