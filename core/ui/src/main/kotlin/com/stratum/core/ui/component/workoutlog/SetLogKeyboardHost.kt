package com.stratum.core.ui.component.workoutlog

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import kotlinx.coroutines.delay
import com.stratum.core.ui.component.formatWeightAmount
import com.stratum.core.ui.component.platecalculator.PlateInventoryUi
import com.stratum.core.ui.component.platecalculator.StratumPlateCalculatorMiniDrawer
import com.stratum.core.ui.component.platecalculator.StratumPlateCalculatorSheet
import com.stratum.core.ui.component.platecalculator.computePlateLoadUi
import com.stratum.core.ui.component.platecalculator.defaultPlateInventory
import com.stratum.core.ui.component.platecalculator.parsePlateTargetWeight

/**
 * Fraction of the **screen** height for the custom set-log keyboard pad (~system IME).
 */
const val SET_LOG_KEYBOARD_HEIGHT_FRACTION = 0.33f

/**
 * §12.5 floor for the pad: four key rows must each stay >= 48dp, plus the
 * three 6dp inter-row gaps — 4 * 48 + 3 * 6 = 210dp. On short screens
 * (landscape, split-screen) the pad grows past its 0.33 fraction rather
 * than shrinking keys below the touch-target law; hosted content such as
 * the reps-mode toggle inherits the floored key height.
 */
private val MinSetLogKeyboardContentHeight = 210.dp

/** Resolved pad content height for the current configuration (excludes nav bar). */
fun setLogKeyboardContentHeight(configuration: Configuration): Dp =
    (configuration.screenHeightDp * SET_LOG_KEYBOARD_HEIGHT_FRACTION).dp
        .coerceAtLeast(MinSetLogKeyboardContentHeight)

/**
 * Active keyboard session. Hosted **above** the LazyColumn so scrolling never disposes the pad.
 */
data class SetLogKeyboardSession(
    val setKey: String,
    val field: SetLogKeyboardField,
    val showWeightInput: Boolean,
    /** Static rec flags for live [canCompleteSetLog] while buffers change under the pad. */
    val hasWeightRecommendation: Boolean,
    val hasRepsRecommendation: Boolean,
    val onWeightChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val onPartialRepsChange: (String) -> Unit = {},
    val onRirChange: (Int) -> Unit,
    val onComplete: (() -> Unit)?,
    val isPlateLoaded: Boolean = false,
    val recommendedWeightAmount: Double = 0.0,
    val unitLabel: String = "lb",
    val isDoubleSided: Boolean = true,
)

/**
 * List/screen-level keyboard host. Set rows open a session; the dock is composed once
 * outside the scrollable so large scrolls cannot tear down the pad.
 *
 * While a session is open for a set, **host drafts are source of truth** for weight/reps text
 * (pad + active cell). Parent [syncSessionChrome] only updates RIR/recs — never overwrites
 * mid-edit buffers. Same-set re-activation uses [setField] / same-set branch of [open] and
 * does not reseed text from row props.
 *
 * Dock height / reveal live on [overlay] ([LocalStratumOverlayDock]).
 */
@Stable
class SetLogKeyboardHostState(
    val overlay: StratumOverlayDockHostState = StratumOverlayDockHostState(),
) {
    var session: SetLogKeyboardSession? by mutableStateOf(null)
        private set

    /** In-flight edit buffers for active session. */
    var weightBuffer: NumericEditBuffer by mutableStateOf(NumericEditBuffer("", 0))
        private set
    var repsBuffer: NumericEditBuffer by mutableStateOf(NumericEditBuffer("", 0))
        private set
    var partialRepsBuffer: NumericEditBuffer by mutableStateOf(NumericEditBuffer("", 0))
        private set

    val weightText: String get() = weightBuffer.text
    val weightCursor: Int get() = weightBuffer.cursor
    val weightSelection: TextRange get() = weightBuffer.selection

    val repsText: String get() = repsBuffer.text
    val repsCursor: Int get() = repsBuffer.cursor
    val repsSelection: TextRange get() = repsBuffer.selection

    val partialRepsText: String get() = partialRepsBuffer.text
    val partialRepsCursor: Int get() = partialRepsBuffer.cursor
    val partialRepsSelection: TextRange get() = partialRepsBuffer.selection

    var rir: Int by mutableIntStateOf(2)
        private set
    var repsEntryMode: RepsEntryMode by mutableStateOf(RepsEntryMode.Full)
        private set
    var repsModeToast: RepsEntryMode? by mutableStateOf(null)
        private set

    var isPlateDrawerOpen: Boolean by mutableStateOf(false)
        private set
    var isPlateSheetOpen: Boolean by mutableStateOf(false)
        private set

    /**
     * In-memory bar/plate selection for this logging screen. Shared by the mini drawer and
     * expanded sheet. Updated only when the sheet **Apply**s — Done/dismiss discards drafts.
     * Not written to equipment or preferences.
     */
    var plateInventory: PlateInventoryUi? by mutableStateOf(null)
        private set

    /** @see StratumOverlayDockHostState.dockHeightDp */
    val dockHeightDp: Dp get() = overlay.dockHeightDp

    /** @see StratumOverlayDockHostState.dockTopInWindow */
    val dockTopInWindow: Float get() = overlay.dockTopInWindow

    /** @see StratumOverlayDockHostState.revealScroller */
    var revealScroller: StratumOverlayDockRevealScroller?
        get() = overlay.revealScroller
        set(value) {
            overlay.revealScroller = value
        }

    /** @see StratumOverlayDockHostState.revealEpoch */
    val revealEpoch: Int get() = overlay.revealEpoch

    val isOpen: Boolean get() = session != null

    fun togglePlateDrawer() {
        isPlateDrawerOpen = !isPlateDrawerOpen
        if (isPlateDrawerOpen) {
            overlay.requestReveal()
        }
    }

    fun openPlateSheet() {
        isPlateSheetOpen = true
    }

    fun closePlateSheet() {
        isPlateSheetOpen = false
    }

    fun plateInventoryFor(unitLabel: String): PlateInventoryUi {
        val held = plateInventory
        if (held != null && held.unitLabel.equals(unitLabel, ignoreCase = true)) {
            return held
        }
        return defaultPlateInventory(unitLabel)
    }

    fun commitPlateInventory(inventory: PlateInventoryUi) {
        plateInventory = inventory
    }

    fun applyWeightFromCalculator(weight: Double) {
        val s = session ?: return
        val formatted = formatWeightAmount(weight)
        weightBuffer = NumericEditBuffer(formatted, formatted.length)
        s.onWeightChange(formatted)
    }

    fun applyCalculatorToSet(weight: Double, inventory: PlateInventoryUi) {
        commitPlateInventory(inventory)
        applyWeightFromCalculator(weight)
    }

    @Suppress("LongParameterList")
    fun open(
        setKey: String,
        field: SetLogKeyboardField,
        weightText: String,
        repsText: String,
        rir: Int,
        showWeightInput: Boolean,
        hasWeightRecommendation: Boolean,
        hasRepsRecommendation: Boolean,
        onWeightChange: (String) -> Unit,
        onRepsChange: (String) -> Unit,
        onPartialRepsChange: (String) -> Unit = {},
        onRirChange: (Int) -> Unit,
        onComplete: (() -> Unit)?,
        isPlateLoaded: Boolean = false,
        partialRepsText: String = "",
        recommendedWeightAmount: Double = 0.0,
        unitLabel: String = "lb",
        isDoubleSided: Boolean = true,
        initialRepsMode: RepsEntryMode = RepsEntryMode.Full,
    ) {
        val previous = session
        if (previous != null && previous.setKey == setKey) {
            // Same set: host drafts stay SoT. Refresh chrome/callbacks and tab the field only.
            this.rir = rir
            this.repsEntryMode = initialRepsMode
            val nextField =
                if (!showWeightInput && field == SetLogKeyboardField.Weight) {
                    SetLogKeyboardField.Reps
                } else {
                    field
                }
            when (nextField) {
                SetLogKeyboardField.Weight -> {
                    weightBuffer = weightBuffer.selectAll()
                }
                SetLogKeyboardField.Reps -> {
                    if (repsEntryMode == RepsEntryMode.Partial) {
                        partialRepsBuffer = partialRepsBuffer.selectAll()
                    } else {
                        repsBuffer = repsBuffer.selectAll()
                    }
                }
            }
            session =
                previous.copy(
                    field = nextField,
                    showWeightInput = showWeightInput,
                    hasWeightRecommendation = hasWeightRecommendation,
                    hasRepsRecommendation = hasRepsRecommendation,
                    onWeightChange = onWeightChange,
                    onRepsChange = onRepsChange,
                    onPartialRepsChange = onPartialRepsChange,
                    onRirChange = onRirChange,
                    onComplete = onComplete,
                    isPlateLoaded = isPlateLoaded,
                    recommendedWeightAmount = recommendedWeightAmount,
                    unitLabel = unitLabel,
                    isDoubleSided = isDoubleSided,
                )
            overlay.requestReveal()
            return
        }

        // New set (or first open): seed drafts from row props, select focused field numbers.
        weightBuffer = NumericEditBuffer(weightText, weightText.length)
        repsBuffer = NumericEditBuffer(repsText, repsText.length)
        partialRepsBuffer = NumericEditBuffer(partialRepsText, partialRepsText.length)
        this.rir = rir
        this.isPlateDrawerOpen = false
        this.isPlateSheetOpen = false
        this.repsEntryMode = initialRepsMode
        this.repsModeToast = null

        val resolvedField =
            if (!showWeightInput && field == SetLogKeyboardField.Weight) {
                SetLogKeyboardField.Reps
            } else {
                field
            }
        when (resolvedField) {
            SetLogKeyboardField.Weight -> {
                weightBuffer = weightBuffer.selectAll()
            }
            SetLogKeyboardField.Reps -> {
                if (initialRepsMode == RepsEntryMode.Partial) {
                    partialRepsBuffer = partialRepsBuffer.selectAll()
                } else {
                    repsBuffer = repsBuffer.selectAll()
                }
            }
        }
        session =
            SetLogKeyboardSession(
                setKey = setKey,
                field = resolvedField,
                showWeightInput = showWeightInput,
                hasWeightRecommendation = hasWeightRecommendation,
                hasRepsRecommendation = hasRepsRecommendation,
                onWeightChange = onWeightChange,
                onRepsChange = onRepsChange,
                onPartialRepsChange = onPartialRepsChange,
                onRirChange = onRirChange,
                onComplete = onComplete,
                isPlateLoaded = isPlateLoaded,
                recommendedWeightAmount = recommendedWeightAmount,
                unitLabel = unitLabel,
                isDoubleSided = isDoubleSided,
            )
        overlay.requestReveal()
    }

    /**
     * Syncs session **chrome** (RIR + recommendation flags) while the row is composed.
     * Does **not** overwrite host weight/reps drafts — those are owned by the pad session.
     */
    fun syncSessionChrome(
        setKey: String,
        rir: Int,
        hasWeightRecommendation: Boolean? = null,
        hasRepsRecommendation: Boolean? = null,
    ) {
        val s = session ?: return
        if (s.setKey != setKey) return
        this.rir = rir
        session =
            s.copy(
                hasWeightRecommendation = hasWeightRecommendation ?: s.hasWeightRecommendation,
                hasRepsRecommendation = hasRepsRecommendation ?: s.hasRepsRecommendation,
            )
    }

    fun setField(field: SetLogKeyboardField) {
        val s = session ?: return
        val clampedField =
            if (!s.showWeightInput && field == SetLogKeyboardField.Weight) {
                SetLogKeyboardField.Reps
            } else {
                field
            }
        session = s.copy(field = clampedField)
        when (clampedField) {
            SetLogKeyboardField.Weight -> {
                weightBuffer = weightBuffer.selectAll()
            }
            SetLogKeyboardField.Reps -> {
                if (repsEntryMode == RepsEntryMode.Partial) {
                    partialRepsBuffer = partialRepsBuffer.selectAll()
                } else {
                    repsBuffer = repsBuffer.selectAll()
                }
            }
        }
    }

    fun selectRepsEntryMode(mode: RepsEntryMode) {
        val modeChanged = repsEntryMode != mode
        repsEntryMode = mode
        if (modeChanged) {
            repsModeToast = mode
        }
        if (mode == RepsEntryMode.Partial) {
            partialRepsBuffer = partialRepsBuffer.selectAll()
        } else {
            repsBuffer = repsBuffer.selectAll()
        }
    }

    fun clearRepsModeToast() {
        repsModeToast = null
    }

    fun isSetActive(setKey: String): Boolean = session?.setKey == setKey

    fun isFieldActive(setKey: String, field: SetLogKeyboardField): Boolean =
        session?.let { it.setKey == setKey && it.field == field } ?: false

    fun setCursor(field: SetLogKeyboardField, cursor: Int) = setCursorForField(field, cursor)

    fun syncValues(
        setKey: String,
        @Suppress("UnusedParameter") weightText: String,
        @Suppress("UnusedParameter") repsText: String,
        rir: Int,
        hasWeightRecommendation: Boolean? = null,
        hasRepsRecommendation: Boolean? = null,
    ) {
        syncSessionChrome(
            setKey = setKey,
            rir = rir,
            hasWeightRecommendation = hasWeightRecommendation,
            hasRepsRecommendation = hasRepsRecommendation,
        )
    }

    /**
     * Updates caret index for the active session field (called on click within active field).
     */
    fun setCursorForField(field: SetLogKeyboardField, cursor: Int) {
        val s = session ?: return
        if (s.field != field) return
        when (field) {
            SetLogKeyboardField.Weight -> {
                weightBuffer = weightBuffer.setCursor(cursor)
            }
            SetLogKeyboardField.Reps -> {
                if (repsEntryMode == RepsEntryMode.Partial) {
                    partialRepsBuffer = partialRepsBuffer.setCursor(cursor)
                } else {
                    repsBuffer = repsBuffer.setCursor(cursor)
                }
            }
        }
    }

    /** Active text buffer + caret + selection for the focused session field. */
    fun activeBuffer(): NumericEditBuffer? {
        val s = session ?: return null
        return when (s.field) {
            SetLogKeyboardField.Weight -> weightBuffer
            SetLogKeyboardField.Reps ->
                if (repsEntryMode == RepsEntryMode.Partial) partialRepsBuffer else repsBuffer
        }
    }

    /** Applies a pad edit to host state and commits text upstream to VM. */
    fun applyPadEditAndCommit(buffer: NumericEditBuffer) {
        val s = session ?: return
        val coerced = buffer.coerced()
        when (s.field) {
            SetLogKeyboardField.Weight -> {
                val textChanged = weightBuffer.text != coerced.text
                weightBuffer = coerced
                if (textChanged) {
                    s.onWeightChange(coerced.text)
                }
            }
            SetLogKeyboardField.Reps -> {
                if (repsEntryMode == RepsEntryMode.Partial) {
                    val textChanged = partialRepsBuffer.text != coerced.text
                    partialRepsBuffer = coerced
                    if (textChanged) {
                        s.onPartialRepsChange(coerced.text)
                    }
                } else {
                    val textChanged = repsBuffer.text != coerced.text
                    repsBuffer = coerced
                    if (textChanged) {
                        s.onRepsChange(coerced.text)
                    }
                }
            }
        }
    }

    fun dismiss() {
        session = null
        isPlateDrawerOpen = false
        isPlateSheetOpen = false
        repsModeToast = null
        weightBuffer = NumericEditBuffer("", 0)
        repsBuffer = NumericEditBuffer("", 0)
        partialRepsBuffer = NumericEditBuffer("", 0)
    }
}

val LocalSetLogKeyboardHost =
    staticCompositionLocalOf<SetLogKeyboardHostState?> { null }

/**
 * Wraps list/screen content and provides [SetLogKeyboardHostState] + dock composition.
 * Dock content is null while there is no session so a closed pad does not paint
 * navigation-bar padding over the list.
 */
@Composable
fun ProvideSetLogKeyboardHost(
    modifier: Modifier = Modifier,
    content: @Composable (listModifier: Modifier) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val contentHeight =
        remember(configuration.screenHeightDp) {
            setLogKeyboardContentHeight(configuration)
        }
    val hostState = remember { SetLogKeyboardHostState() }
    val session = hostState.session

    ProvideStratumOverlayDock(
        modifier = modifier,
        host = hostState.overlay,
        dockContent =
            if (session != null) {
                {
                    SetLogKeyboardDockContent(
                        host = hostState,
                        contentHeight = contentHeight,
                    )
                }
            } else {
                null
            },
    ) { listModifier, _ ->
        CompositionLocalProvider(LocalSetLogKeyboardHost provides hostState) {
            content(listModifier)
        }
        if (hostState.isPlateSheetOpen && session != null) {
            StratumPlateCalculatorSheet(
                initialTargetWeight = parsePlateTargetWeight(
                    text = hostState.weightText,
                    fallback = session.recommendedWeightAmount,
                ),
                unitLabel = session.unitLabel,
                isDoubleSided = session.isDoubleSided,
                initialInventory = hostState.plateInventoryFor(session.unitLabel),
                onDismissRequest = { hostState.closePlateSheet() },
                onApplyWeight = hostState::applyCalculatorToSet,
            )
        }
    }
}

@Composable
private fun SetLogKeyboardDockContent(
    host: SetLogKeyboardHostState,
    contentHeight: Dp,
) {
    val session = host.session ?: return
    val allowDecimal = session.field == SetLogKeyboardField.Weight

    // Live validation against host's in-flight text (not stale row props).
    val liveCanComplete =
        canCompleteSetLog(
            showWeightInput = session.showWeightInput,
            weightText = host.weightText,
            hasWeightRecommendation = session.hasWeightRecommendation,
            repsText = host.repsText,
            hasRepsRecommendation = session.hasRepsRecommendation,
        )

    val completeAndDismiss = {
        session.onWeightChange(host.weightText)
        session.onRepsChange(host.repsText)
        session.onPartialRepsChange(host.partialRepsText)
        host.overlay.skipNextCollapseScrollRestore = true
        session.onComplete?.invoke()
        host.dismiss()
    }

    val showRepsModeToggle = session.field == SetLogKeyboardField.Reps
    val toastMode = host.repsModeToast
    LaunchedEffect(toastMode) {
        if (toastMode != null) {
            delay(1_000)
            host.clearRepsModeToast()
        }
    }

    val showPlateCalculator =
        session.showWeightInput && session.isPlateLoaded && session.field == SetLogKeyboardField.Weight
    val currentTargetWeight =
        parsePlateTargetWeight(host.weightText, session.recommendedWeightAmount)
    val inventory = host.plateInventoryFor(session.unitLabel)
    val plateLoad =
        remember(
            currentTargetWeight,
            inventory,
            session.unitLabel,
            session.isDoubleSided,
        ) {
            computePlateLoadUi(
                targetWeight = currentTargetWeight,
                barWeight = inventory.barWeight,
                availablePlates = inventory.enabledPlates.toList(),
                unitLabel = session.unitLabel,
                isDoubleSided = session.isDoubleSided,
            )
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        val toast = toastMode
        if (toast != null) {
            StratumText(
                text =
                    stringResource(
                        if (toast == RepsEntryMode.Partial) {
                            CoreUiR.string.set_log_keyboard_now_partial
                        } else {
                            CoreUiR.string.set_log_keyboard_now_full
                        },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s4),
                style = StratumThemeAccessor.typography.sectionLabel,
                color = StratumThemeAccessor.colors.textMuted,
            )
        }
        AnimatedVisibility(
            visible = host.isPlateDrawerOpen && showPlateCalculator,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            StratumPlateCalculatorMiniDrawer(
                plateLoad = plateLoad,
                onExpandClick = { host.openPlateSheet() },
                onCloseClick = { host.togglePlateDrawer() },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = StratumSpacing.s4, vertical = StratumSpacing.s4),
            )
        }

        StratumSetLogKeyboardContent(
            state =
                SetLogKeyboardPadState(
                    activeField = session.field,
                    selectedRir = host.rir,
                    showWeightInput = session.showWeightInput,
                    allowDecimal = allowDecimal,
                    canComplete = liveCanComplete,
                    showPlateCalculator = showPlateCalculator,
                    showRepsModeToggle = showRepsModeToggle,
                    repsEntryMode = host.repsEntryMode,
                ),
            actions =
                SetLogKeyboardPadActions(
                    onRirSelected = { value ->
                        host.syncSessionChrome(session.setKey, value)
                        session.onRirChange(value)
                    },
                    onDigit = { digit ->
                        host.activeBuffer()?.let { base ->
                            host.applyPadEditAndCommit(insertNumericKey(base, digit, allowDecimal))
                        }
                    },
                    onDecimal = {
                        if (session.field == SetLogKeyboardField.Weight) {
                            host.activeBuffer()?.let { base ->
                                host.applyPadEditAndCommit(
                                    insertNumericKey(base, '.', allowDecimal = true),
                                )
                            }
                        }
                    },
                    onBackspace = {
                        host.activeBuffer()?.let { base ->
                            host.applyPadEditAndCommit(backspaceNumericBuffer(base))
                        }
                    },
                    onPrimaryAction = {
                        applySetLogKeyboardAction(
                            action = resolveSetLogKeyboardAction(canComplete = liveCanComplete),
                            onFieldChange = host::setField,
                            onComplete = completeAndDismiss,
                            onDismiss = host::dismiss,
                        )
                    },
                    onDismiss = host::dismiss,
                    onElevatedNavAction = {
                        applySetLogKeyboardAction(
                            action =
                                resolveSetLogKeyboardElevatedNavAction(
                                    active = session.field,
                                    showWeightInput = session.showWeightInput,
                                ),
                            onFieldChange = host::setField,
                            onComplete = completeAndDismiss,
                            onDismiss = host::dismiss,
                        )
                    },
                    onPlateCalculatorClick = {
                        host.togglePlateDrawer()
                    },
                    onRepsEntryModeChange = host::selectRepsEntryMode,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
        )
    }
}

@Composable
fun rememberSetLogKeyboardHeight(): Dp {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenHeightDp) {
        setLogKeyboardContentHeight(configuration)
    }
}

private fun applySetLogKeyboardAction(
    action: SetLogKeyboardAction?,
    onFieldChange: (SetLogKeyboardField) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        SetLogKeyboardAction.FocusReps -> onFieldChange(SetLogKeyboardField.Reps)
        SetLogKeyboardAction.FocusWeight -> onFieldChange(SetLogKeyboardField.Weight)
        SetLogKeyboardAction.Complete -> onComplete()
        SetLogKeyboardAction.Dismiss -> onDismiss()
        null -> Unit
    }
}

// Backward-compatible name used by list hosts.
typealias SetLogKeyboardRevealScroller = StratumOverlayDockRevealScroller
