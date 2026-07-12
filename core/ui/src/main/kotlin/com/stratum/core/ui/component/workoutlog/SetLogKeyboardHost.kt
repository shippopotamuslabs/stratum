package com.stratum.core.ui.component.workoutlog

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fraction of the **screen** height for the custom set-log keyboard pad (~system IME).
 */
const val SET_LOG_KEYBOARD_HEIGHT_FRACTION = 0.33f

/** Resolved pad content height for the current configuration (excludes nav bar). */
fun setLogKeyboardContentHeight(configuration: Configuration): Dp =
    (configuration.screenHeightDp * SET_LOG_KEYBOARD_HEIGHT_FRACTION).dp

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
    val onRirChange: (Int) -> Unit,
    val onComplete: (() -> Unit)?,
)

/**
 * List/screen-level keyboard host. Set rows open a session; the dock is composed once
 * outside the scrollable so large scrolls cannot tear down the pad.
 *
 * Dock height / reveal live on [overlay] ([LocalStratumOverlayDock]).
 */
@Stable
class SetLogKeyboardHostState(
    val overlay: StratumOverlayDockHostState = StratumOverlayDockHostState(),
) {
    var session: SetLogKeyboardSession? by mutableStateOf(null)
        private set

    /** Live draft values for the open set (updated via [syncValues]). */
    var weightText: String by mutableStateOf("")
        private set
    var repsText: String by mutableStateOf("")
        private set
    var rir: Int by mutableIntStateOf(2)
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
        onRirChange: (Int) -> Unit,
        onComplete: (() -> Unit)?,
    ) {
        this.weightText = weightText
        this.repsText = repsText
        this.rir = rir
        session =
            SetLogKeyboardSession(
                setKey = setKey,
                field = field,
                showWeightInput = showWeightInput,
                hasWeightRecommendation = hasWeightRecommendation,
                hasRepsRecommendation = hasRepsRecommendation,
                onWeightChange = onWeightChange,
                onRepsChange = onRepsChange,
                onRirChange = onRirChange,
                onComplete = onComplete,
            )
        // Open chrome is derived by ProvideStratumOverlayDock from session != null (dockContent).
        // Always request reveal (also covers open-while-open set switches where isOpen stays true).
        overlay.requestReveal()
    }

    /** Keep host drafts in sync while the owning row is composed. */
    fun syncValues(
        setKey: String,
        weightText: String,
        repsText: String,
        rir: Int,
        hasWeightRecommendation: Boolean? = null,
        hasRepsRecommendation: Boolean? = null,
    ) {
        val current = session ?: return
        if (current.setKey != setKey) return
        this.weightText = weightText
        this.repsText = repsText
        this.rir = rir
        val nextWeightRec = hasWeightRecommendation ?: return
        val nextRepsRec = hasRepsRecommendation ?: return
        val recsChanged =
            nextWeightRec != current.hasWeightRecommendation ||
                nextRepsRec != current.hasRepsRecommendation
        if (recsChanged) {
            session =
                current.copy(
                    hasWeightRecommendation = nextWeightRec,
                    hasRepsRecommendation = nextRepsRec,
                )
        }
    }

    fun setField(field: SetLogKeyboardField) {
        val current = session ?: return
        val next =
            if (!current.showWeightInput && field == SetLogKeyboardField.Weight) {
                SetLogKeyboardField.Reps
            } else {
                field
            }
        if (next != current.field) {
            session = current.copy(field = next)
            overlay.requestReveal()
        }
    }

    fun dismiss() {
        session = null
        // Closed chrome is derived by ProvideStratumOverlayDock when dockContent becomes null.
    }

    fun isFieldActive(
        setKey: String,
        field: SetLogKeyboardField,
    ): Boolean = session?.setKey == setKey && session?.field == field

    fun isSetActive(setKey: String): Boolean = session?.setKey == setKey
}

val LocalSetLogKeyboardHost =
    staticCompositionLocalOf<SetLogKeyboardHostState?> { null }

/**
 * Provides a [SetLogKeyboardHostState] and draws an in-hierarchy bottom keyboard **over** the
 * list. Lists must add [SetLogKeyboardHostState.dockHeightDp] to their own contentPadding so
 * fields can scroll above the dock; do not shrink the list with Modifier.padding.
 */
@Composable
fun ProvideSetLogKeyboardHost(
    modifier: Modifier = Modifier,
    content: @Composable (listModifier: Modifier) -> Unit,
) {
    val host = remember { SetLogKeyboardHostState() }
    val configuration = LocalConfiguration.current
    val contentHeight =
        remember(configuration.screenHeightDp) {
            setLogKeyboardContentHeight(configuration)
        }

    CompositionLocalProvider(LocalSetLogKeyboardHost provides host) {
        ProvideStratumOverlayDock(
            modifier = modifier,
            host = host.overlay,
            dockContent =
                host.session?.let { session ->
                    {
                        SetLogKeyboardDockContent(
                            session = session,
                            weightText = host.weightText,
                            repsText = host.repsText,
                            rir = host.rir,
                            contentHeight = contentHeight,
                            onWeightChange = { text ->
                                host.syncValues(session.setKey, text, host.repsText, host.rir)
                                session.onWeightChange(text)
                            },
                            onRepsChange = { text ->
                                host.syncValues(session.setKey, host.weightText, text, host.rir)
                                session.onRepsChange(text)
                            },
                            onRirChange = { value ->
                                host.syncValues(session.setKey, host.weightText, host.repsText, value)
                                session.onRirChange(value)
                            },
                            onFieldChange = host::setField,
                            onComplete = {
                                session.onComplete?.invoke()
                                host.dismiss()
                            },
                            onDismiss = host::dismiss,
                        )
                    }
                },
        ) { listModifier, _ ->
            content(listModifier)
        }
    }
}

@Composable
private fun SetLogKeyboardDockContent(
    session: SetLogKeyboardSession,
    weightText: String,
    repsText: String,
    rir: Int,
    contentHeight: Dp,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRirChange: (Int) -> Unit,
    onFieldChange: (SetLogKeyboardField) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Local buffers so rapid key taps stay consistent before parent recomposes.
    var weightBuffer by remember(session.setKey) { mutableStateOf(weightText) }
    var repsBuffer by remember(session.setKey) { mutableStateOf(repsText) }
    LaunchedEffect(weightText) { weightBuffer = weightText }
    LaunchedEffect(repsText) { repsBuffer = repsText }

    val allowDecimal = session.field == SetLogKeyboardField.Weight
    val commit: (String) -> Unit = { updated ->
        when (session.field) {
            SetLogKeyboardField.Weight -> {
                weightBuffer = updated
                onWeightChange(updated)
            }
            SetLogKeyboardField.Reps -> {
                repsBuffer = updated
                onRepsChange(updated)
            }
        }
    }

    val liveCanComplete =
        canCompleteSetLog(
            showWeightInput = session.showWeightInput,
            weightText = weightBuffer,
            hasWeightRecommendation = session.hasWeightRecommendation,
            repsText = repsBuffer,
            hasRepsRecommendation = session.hasRepsRecommendation,
        )
    StratumSetLogKeyboardContent(
        state =
            SetLogKeyboardPadState(
                activeField = session.field,
                selectedRir = rir,
                showWeightInput = session.showWeightInput,
                allowDecimal = allowDecimal,
                canComplete = liveCanComplete,
            ),
        actions =
            SetLogKeyboardPadActions(
                onRirSelected = onRirChange,
                onDigit = { digit ->
                    val base =
                        when (session.field) {
                            SetLogKeyboardField.Weight -> weightBuffer
                            SetLogKeyboardField.Reps -> repsBuffer
                        }
                    commit(appendSetLogKey(base, digit, allowDecimal))
                },
                onDecimal = {
                    commit(appendSetLogKey(weightBuffer, '.', allowDecimal = true))
                },
                onBackspace = {
                    val base =
                        when (session.field) {
                            SetLogKeyboardField.Weight -> weightBuffer
                            SetLogKeyboardField.Reps -> repsBuffer
                        }
                    commit(backspaceSetLogValue(base))
                },
                onPrimaryAction = {
                    applySetLogKeyboardAction(
                        action =
                            resolveSetLogKeyboardAction(
                                active = session.field,
                                canComplete = liveCanComplete,
                                showWeightInput = session.showWeightInput,
                            ),
                        onFieldChange = onFieldChange,
                        onComplete = onComplete,
                        onDismiss = onDismiss,
                    )
                },
                onDismiss = onDismiss,
                onElevatedNavAction = {
                    applySetLogKeyboardAction(
                        action =
                            resolveSetLogKeyboardElevatedNavAction(
                                active = session.field,
                                canComplete = liveCanComplete,
                                showWeightInput = session.showWeightInput,
                            ),
                        onFieldChange = onFieldChange,
                        onComplete = onComplete,
                        onDismiss = onDismiss,
                    )
                },
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(contentHeight),
    )
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
