package com.stratum.feature.program.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDurationStepper
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.workoutlog.ProvideStratumOverlayDock
import com.stratum.core.ui.component.workoutlog.RepRangeDraftIssue
import com.stratum.core.ui.component.workoutlog.RepRangeDraftValidation
import com.stratum.core.ui.component.workoutlog.RepRangeKeyboardField
import com.stratum.core.ui.component.workoutlog.StratumOverlayDockHostState
import com.stratum.core.ui.component.workoutlog.StratumRepRangeKeyboardContent
import com.stratum.core.ui.component.workoutlog.NumericEditBuffer
import com.stratum.core.ui.component.workoutlog.backspaceNumericBuffer
import com.stratum.core.ui.component.workoutlog.insertNumericKey
import com.stratum.core.ui.component.workoutlog.parseRepRangeDraft
import com.stratum.core.ui.component.workoutlog.setLogKeyboardContentHeight
import com.stratum.core.ui.component.workoutlog.toggleRepRangeField
import com.stratum.core.ui.component.workoutlog.validateRepRangeDrafts
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.domain.program.ProgressionRepRangePolicy
import com.stratum.feature.program.R

private const val MIN_REST_SECONDS = 0
private const val MAX_REST_SECONDS = 600
private const val REST_SECONDS_STEP = 30

sealed class EditWorkoutDockSession {
    data class RepRange(
        val setId: String,
        val field: RepRangeKeyboardField,
        /** Atomic band commit — avoid two sequential setReps(min) / setReps(max) rounds. */
        val onCommitBand: (repMin: Int, repMax: Int) -> Unit,
    ) : EditWorkoutDockSession()

    data class Rest(
        val setId: String,
        val onApplyToSet: (restSeconds: Int) -> Unit,
        val onApplyToAllSets: (restSeconds: Int) -> Unit,
        /** Myo rows edit intra-cluster mini rest only (exit rest is lift default). */
        val isMiniRest: Boolean = false,
    ) : EditWorkoutDockSession()
}

/**
 * Edit-workout bottom dock: slim rep-range pad or rest-time editor.
 *
 * **Leave policy (phone form UX):** when leaving a rep-range session (hide, back, save,
 * another set, rest), either **commit** valid drafts or **block** and keep red errors —
 * never silently discard typed values.
 *
 * Same-set min↔max uses [setRepField] only (no flush). Rest uses explicit Apply; closing
 * rest without Apply discards the stepper draft (explicit commit model).
 */
@Stable
class EditWorkoutDockHostState(
    val overlay: StratumOverlayDockHostState = StratumOverlayDockHostState(),
) {
    var session: EditWorkoutDockSession? by mutableStateOf(null)
        private set

    var minText: String by mutableStateOf("")
        private set

    var maxText: String by mutableStateOf("")
        private set

    /** Caret indices for [minText] / [maxText] (`0..length`). */
    var minCursor: Int by mutableIntStateOf(0)
        private set
    var maxCursor: Int by mutableIntStateOf(0)
        private set

    /**
     * After a failed leave while drafts are invalid, blank sides also show errors.
     * Cleared when drafts become valid again.
     */
    var requireCompleteBand: Boolean by mutableStateOf(false)
        private set

    var restSeconds: Int by mutableIntStateOf(DEFAULT_REST_SECONDS)
        private set

    val isOpen: Boolean get() = session != null

    val repRangeValidation: RepRangeDraftValidation
        get() =
            if (session is EditWorkoutDockSession.RepRange) {
                validateRepRangeDrafts(
                    minText = minText,
                    maxText = maxText,
                    requireCompleteBand = requireCompleteBand,
                )
            } else {
                RepRangeDraftValidation(
                    minInvalid = false,
                    maxInvalid = false,
                    issue = RepRangeDraftIssue.None,
                )
            }

    /**
     * Open rep pad for [setId]. Same-set field changes only call [setRepField] (caller should
     * prefer that path). Switching from another session uses [tryCommitPendingOrBlock].
     *
     * @return false if current rep drafts are invalid and leave was blocked.
     */
    fun openRepRange(
        setId: String,
        field: RepRangeKeyboardField,
        minText: String,
        maxText: String,
        onCommitBand: (repMin: Int, repMax: Int) -> Unit,
    ): Boolean {
        val current = session as? EditWorkoutDockSession.RepRange
        if (current != null && current.setId == setId) {
            setRepField(field)
            return true
        }
        if (!tryCommitPendingOrBlock()) return false
        this.minText = minText
        this.maxText = maxText
        minCursor = minText.length
        maxCursor = maxText.length
        requireCompleteBand = false
        session =
            EditWorkoutDockSession.RepRange(
                setId = setId,
                field = field,
                onCommitBand = onCommitBand,
            )
        overlay.requestReveal()
        return true
    }

    /**
     * Open rest dock. Commits or blocks an open rep session first.
     * Re-tapping the same set’s rest keeps the in-progress stepper value.
     *
     * @return false if rep drafts blocked leave.
     */
    fun openRest(
        setId: String,
        restSeconds: Int,
        onApplyToSet: (Int) -> Unit,
        onApplyToAllSets: (Int) -> Unit,
        isMiniRest: Boolean = false,
    ): Boolean {
        val currentRest = session as? EditWorkoutDockSession.Rest
        if (currentRest != null && currentRest.setId == setId) {
            // Same rest chip: keep draft seconds; refresh apply targets.
            session =
                currentRest.copy(
                    onApplyToSet = onApplyToSet,
                    onApplyToAllSets = onApplyToAllSets,
                    isMiniRest = isMiniRest,
                )
            overlay.requestReveal()
            return true
        }
        if (!tryCommitPendingOrBlock()) return false
        this.restSeconds = restSeconds
        requireCompleteBand = false
        session =
            EditWorkoutDockSession.Rest(
                setId = setId,
                onApplyToSet = onApplyToSet,
                onApplyToAllSets = onApplyToAllSets,
                isMiniRest = isMiniRest,
            )
        overlay.requestReveal()
        return true
    }

    fun setRepField(field: RepRangeKeyboardField) {
        val current = session as? EditWorkoutDockSession.RepRange ?: return
        if (current.field != field) {
            session = current.copy(field = field)
            overlay.requestReveal()
        }
    }

    fun setCursor(
        field: RepRangeKeyboardField,
        index: Int,
    ) {
        if (session !is EditWorkoutDockSession.RepRange) return
        when (field) {
            RepRangeKeyboardField.Min -> minCursor = index.coerceIn(0, minText.length)
            RepRangeKeyboardField.Max -> maxCursor = index.coerceIn(0, maxText.length)
        }
    }

    fun activeBuffer(): NumericEditBuffer? {
        val current = session as? EditWorkoutDockSession.RepRange ?: return null
        return when (current.field) {
            RepRangeKeyboardField.Min ->
                NumericEditBuffer(text = minText, cursor = minCursor).coerced()
            RepRangeKeyboardField.Max ->
                NumericEditBuffer(text = maxText, cursor = maxCursor).coerced()
        }
    }

    /**
     * Applies a pad edit to the active rep-range draft only (no domain commit — band commits
     * on leave via [tryCommitPendingOrBlock]).
     */
    fun applyPadEditToDrafts(updated: NumericEditBuffer) {
        val current = session as? EditWorkoutDockSession.RepRange ?: return
        val coerced = updated.coerced()
        when (current.field) {
            RepRangeKeyboardField.Min -> {
                minText = coerced.text
                minCursor = coerced.cursor
            }
            RepRangeKeyboardField.Max -> {
                maxText = coerced.text
                maxCursor = coerced.cursor
            }
        }
        clearCompleteBandFlagIfValid()
    }

    fun updateRestSeconds(seconds: Int) {
        restSeconds = seconds
    }

    fun isRepSessionFor(setId: String): Boolean {
        val current = session as? EditWorkoutDockSession.RepRange ?: return false
        return current.setId == setId
    }

    fun isRepFieldActive(
        setId: String,
        field: RepRangeKeyboardField,
    ): Boolean {
        val current = session as? EditWorkoutDockSession.RepRange ?: return false
        return current.setId == setId && current.field == field
    }

    fun isRestActive(setId: String): Boolean {
        val current = session as? EditWorkoutDockSession.Rest ?: return false
        return current.setId == setId
    }

    /**
     * Leave the current dock session:
     * - **Rep range valid** → commit band, close → true
     * - **Rep range invalid** → keep open + errors → false
     * - **Rest** → close without apply (Apply is explicit) → true
     * - **No session** → true
     *
     * Call from hide, system/toolbar back, save, and before opening a different dock target.
     */
    fun tryCommitPendingOrBlock(): Boolean {
        when (val current = session) {
            is EditWorkoutDockSession.RepRange -> {
                val validation =
                    validateRepRangeDrafts(
                        minText = minText,
                        maxText = maxText,
                        requireCompleteBand = true,
                    )
                if (!validation.isValid) {
                    requireCompleteBand = true
                    return false
                }
                current.onCommitBand(parseRepRangeDraft(minText), parseRepRangeDraft(maxText))
                close()
                return true
            }
            is EditWorkoutDockSession.Rest -> {
                close()
                return true
            }
            null -> return true
        }
    }

    /** Hide keyboard / rest X — same leave policy as [tryCommitPendingOrBlock] for reps. */
    fun dismiss() {
        tryCommitPendingOrBlock()
    }

    /**
     * Best-effort leave when the host is disposed (e.g. Create Program unmounts the embed).
     * Valid rep drafts are committed; invalid drafts cannot show UI so they are dropped and
     * the session is closed. Prefer [tryCommitPendingOrBlock] when the UI is still interactive.
     */
    fun commitIfValidThenClose() {
        when (val current = session) {
            is EditWorkoutDockSession.RepRange -> {
                val validation =
                    validateRepRangeDrafts(
                        minText = minText,
                        maxText = maxText,
                        requireCompleteBand = true,
                    )
                if (validation.isValid) {
                    current.onCommitBand(parseRepRangeDraft(minText), parseRepRangeDraft(maxText))
                }
                close()
            }
            is EditWorkoutDockSession.Rest -> close()
            null -> Unit
        }
    }

    fun applyRestToSet() {
        val current = session as? EditWorkoutDockSession.Rest ?: return
        current.onApplyToSet(restSeconds)
        close()
    }

    fun applyRestToAllSets() {
        val current = session as? EditWorkoutDockSession.Rest ?: return
        current.onApplyToAllSets(restSeconds)
        close()
    }

    private fun clearCompleteBandFlagIfValid() {
        if (!requireCompleteBand) return
        val live =
            validateRepRangeDrafts(
                minText = minText,
                maxText = maxText,
                requireCompleteBand = true,
            )
        if (live.isValid) {
            requireCompleteBand = false
        }
    }

    private fun close() {
        session = null
        requireCompleteBand = false
    }
}

val LocalEditWorkoutDock =
    staticCompositionLocalOf<EditWorkoutDockHostState?> { null }

@Composable
fun ProvideEditWorkoutDock(
    modifier: Modifier = Modifier,
    host: EditWorkoutDockHostState? = null,
    content: @Composable (listModifier: Modifier, dock: EditWorkoutDockHostState) -> Unit,
) {
    val rememberedHost = remember { EditWorkoutDockHostState() }
    val dockHost = host ?: rememberedHost
    val configuration = LocalConfiguration.current
    val repPadHeight =
        remember(configuration.screenHeightDp) {
            setLogKeyboardContentHeight(configuration)
        }

    CompositionLocalProvider(LocalEditWorkoutDock provides dockHost) {
        ProvideStratumOverlayDock(
            modifier = modifier,
            host = dockHost.overlay,
            dockContent =
                dockHost.session?.let { session ->
                    {
                        when (session) {
                            is EditWorkoutDockSession.RepRange ->
                                EditWorkoutRepRangeDock(
                                    host = dockHost,
                                    session = session,
                                    contentHeight = repPadHeight,
                                )
                            is EditWorkoutDockSession.Rest ->
                                EditWorkoutRestDock(host = dockHost)
                        }
                    }
                },
        ) { listModifier, _ ->
            BackHandler(enabled = dockHost.isOpen) {
                dockHost.dismiss()
            }
            content(listModifier, dockHost)
        }
    }
}

@Composable
private fun EditWorkoutRepRangeDock(
    host: EditWorkoutDockHostState,
    session: EditWorkoutDockSession.RepRange,
    contentHeight: Dp,
) {
    val validation = host.repRangeValidation
    val errorMessage =
        when (validation.issue) {
            RepRangeDraftIssue.None -> null
            RepRangeDraftIssue.MissingValue ->
                stringResource(CoreUiR.string.rep_range_error_missing_value)
            RepRangeDraftIssue.BelowMinFloor ->
                stringResource(
                    CoreUiR.string.rep_range_error_below_min_floor,
                    ProgressionRepRangePolicy.MIN_STORED_REP_MIN,
                )
            RepRangeDraftIssue.MaxNotAboveMin ->
                stringResource(CoreUiR.string.rep_range_error_max_not_above_min)
        }
    StratumRepRangeKeyboardContent(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(contentHeight),
        activeField = session.field,
        errorMessage = errorMessage,
        onDigit = { digit ->
            host.activeBuffer()?.let { base ->
                host.applyPadEditToDrafts(insertNumericKey(base, digit, allowDecimal = false))
            }
        },
        onBackspace = {
            host.activeBuffer()?.let { base ->
                host.applyPadEditToDrafts(backspaceNumericBuffer(base))
            }
        },
        onOver = {
            host.setRepField(toggleRepRangeField(session.field))
        },
        onDismiss = host::dismiss,
    )
}

@Composable
private fun EditWorkoutRestDock(host: EditWorkoutDockHostState) {
    val sheetColor = StratumThemeAccessor.colors.surfaceRaised
    val restSession = host.session as? EditWorkoutDockSession.Rest
    val isMiniRest = restSession?.isMiniRest == true
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isMiniRest) {
                StratumText(
                    text = stringResource(R.string.edit_workout_mini_rest_title),
                    style = StratumThemeAccessor.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            StratumIconButton(
                onClick = host::dismiss,
                containerColor = StratumThemeAccessor.colors.surfaceInset,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_close),
                    contentDescription = stringResource(R.string.edit_workout_rest_dismiss),
                    tint = StratumThemeAccessor.colors.textPrimary,
                )
            }
        }
        if (isMiniRest) {
            StratumText(
                text = stringResource(R.string.edit_workout_mini_rest_hint),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
            )
        }
        StratumDurationStepper(
            totalMinutes = host.restSeconds,
            onTotalMinutesChange = host::updateRestSeconds,
            minMinutes = MIN_REST_SECONDS,
            maxMinutes = MAX_REST_SECONDS,
            minutesStep = REST_SECONDS_STEP,
            hoursLabel = stringResource(R.string.edit_workout_rest_minutes_label),
            minutesLabel = stringResource(R.string.edit_workout_rest_seconds_label),
            incrementContentDescription = stringResource(R.string.edit_workout_rest_increment),
            decrementContentDescription = stringResource(R.string.edit_workout_rest_decrement),
            modifier = Modifier.fillMaxWidth(),
        )
        StratumButton(
            text = stringResource(
                if (isMiniRest) {
                    R.string.edit_workout_rest_apply_mini_rest
                } else {
                    R.string.edit_workout_rest_apply_to_set
                },
            ),
            onClick = host::applyRestToSet,
            modifier = Modifier.fillMaxWidth(),
        )
        StratumOutlinedButton(
            text = stringResource(R.string.edit_workout_rest_apply_to_all_sets),
            onClick = host::applyRestToAllSets,
            containerColor = sheetColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
