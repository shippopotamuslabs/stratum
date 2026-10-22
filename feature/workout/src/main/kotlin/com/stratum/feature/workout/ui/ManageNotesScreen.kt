package com.stratum.feature.workout.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumSwipeToRevealRow
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumTextFieldOptions
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.component.StratumTopBarTextButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassChromePadding
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

/** Preview-seam initial drafts for the three note tabs, derived from one [NoteListsUi]. */
private data class InitialNotesDrafts(
    val session: List<EditableNote>,
    val workout: List<EditableNote>,
    val lift: List<EditableNote>,
)

private fun initialNotesDrafts(initialLists: NoteListsUi?): InitialNotesDrafts =
    InitialNotesDrafts(
        session = initialLists?.session?.let(::editableFromLoaded).orEmpty(),
        workout = initialLists?.workout?.let(::editableFromLoaded).orEmpty(),
        lift = initialLists?.lift?.let(::editableFromLoaded).orEmpty(),
    )

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
    /**
     * Preview-only initial lists (screenshot tests capture before `LaunchedEffect` loads
     * complete). Production callers leave this null; the load effect still refreshes.
     */
    initialLists: NoteListsUi? = null,
) {
    var loaded by remember(target.workoutLiftId, target.liftId) {
        mutableStateOf(initialLists)
    }
    var selectedTab by remember(target) { mutableStateOf(target.tab) }
    val initialDrafts = remember(target.workoutLiftId, initialLists) {
        initialNotesDrafts(initialLists)
    }
    var sessionDrafts by remember(target.workoutLiftId, initialLists) {
        mutableStateOf(initialDrafts.session)
    }
    var workoutDrafts by remember(target.workoutLiftId, initialLists) {
        mutableStateOf(initialDrafts.workout)
    }
    var liftDrafts by remember(target.workoutLiftId, initialLists) {
        mutableStateOf(initialDrafts.lift)
    }
    /** Key of the note added via "Add Note" awaiting initial focus + keyboard. */
    var pendingFocusKey by remember(target.workoutLiftId) {
        mutableStateOf<String?>(null)
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

    // Glass (§12.8, STR-157): hosted inside the session screen's composition, so
    // the route's glass host reaches this screen too. Under glass the top bar
    // overlays and the content column carries the top clearance, keeping the
    // tab header beneath the glass; solid hosts keep the in-flow geometry.
    val chrome = rememberGlassChromePadding()
    StratumTabScaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        horizontalPadding = 0.dp,
        overlayTopBar = chrome.active,
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
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = chrome.top)
                    .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            StratumSegmentedControl(
                segments =
                    listOf(
                        tabLabel(LiftNoteTab.Session, sessionDrafts.size),
                        tabLabel(LiftNoteTab.Workout, workoutDrafts.size),
                        tabLabel(LiftNoteTab.Lift, liftDrafts.size),
                    ),
                selectedIndex = selectedTab.ordinal,
                onSelectedIndexChange = { index ->
                    selectedTab = LiftNoteTab.entries[index]
                    // A tab switch strands any unconsumed add-note focus request.
                    pendingFocusKey = null
                },
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
            if (currentDrafts.isNotEmpty()) {
                StratumText(
                    text = stringResource(CoreUiR.string.lift_notes_swipe_hint),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
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
                    val focusRequester = remember(draft.key) { FocusRequester() }
                    StratumSwipeToRevealRow(
                        onActionClick = {
                            setDrafts(currentDrafts.filterNot { it.key == draft.key })
                        },
                        actionContentDescription =
                            stringResource(CoreUiR.string.lift_notes_delete_note_cd),
                        // Transparent so the red reveal shows through the corner notches where
                        // the field's rounded outline curves away — otherwise a surfaceBase
                        // sliver sits between the note edge and the red. Closed-state corner
                        // notches fall outside the row clip below and stay invisible.
                        contentBackgroundColor = Color.Transparent,
                        // Clip to the field shape so the revealed red background conforms
                        // to the note's rounded corners instead of peeking out square.
                        modifier = Modifier.clip(StratumThemeAccessor.shapes.panel),
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
                            // No floating label: its top strip sits outside the field container
                            // and would leak the red reveal above the note. The placeholder
                            // captions the empty state instead (still in the a11y tree).
                            label = null,
                            placeholder = stringResource(CoreUiR.string.lift_notes_field_label),
                            singleLine = false,
                            options =
                                StratumTextFieldOptions(
                                    minLines = 4,
                                    maxLines = 8,
                                    selectAllOnFocus = true,
                                ),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                        )
                    }
                    if (pendingFocusKey == draft.key) {
                        LaunchedEffect(draft.key) {
                            pendingFocusKey = null
                            focusRequester.requestFocus()
                        }
                    }
                }
                item(key = "add") {
                    StratumOutlinedButton(
                        text = stringResource(CoreUiR.string.lift_notes_add_note),
                        onClick = {
                            val key = UUID.randomUUID().toString()
                            setDrafts(currentDrafts + EditableNote(key = key))
                            pendingFocusKey = key
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
