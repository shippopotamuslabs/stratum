package com.stratum.core.ui.component.workoutlog

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

    /** Live draft values for the open set (host-owned while session is open). */
    var weightText: String by mutableStateOf("")
        private set
    var repsText: String by mutableStateOf("")
        private set
    var rir: Int by mutableIntStateOf(2)
        private set

    /** Caret indices for [weightText] / [repsText] (`0..length`). */
    var weightCursor: Int by mutableIntStateOf(0)
        private set
    var repsCursor: Int by mutableIntStateOf(0)
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
        val previous = session
        if (previous != null && previous.setKey == setKey) {
            // Same set: host drafts stay SoT. Refresh chrome/callbacks and tab the field only.
            this.rir = rir
            val nextField =
                if (!showWeightInput && field == SetLogKeyboardField.Weight) {
                    SetLogKeyboardField.Reps
                } else {
                    field
                }
            session =
                previous.copy(
                    field = nextField,
                    showWeightInput = showWeightInput,
                    hasWeightRecommendation = hasWeightRecommendation,
                    hasRepsRecommendation = hasRepsRecommendation,
                    onWeightChange = onWeightChange,
                    onRepsChange = onRepsChange,
                    onRirChange = onRirChange,
                    onComplete = onComplete,
                )
            overlay.requestReveal()
            return
        }

        // New set (or first open): seed drafts from row props, carets at end.
        this.weightText = weightText
        this.repsText = repsText
        this.rir = rir
        weightCursor = weightText.length
        repsCursor = repsText.length
        session =
            SetLogKeyboardSession(
                setKey = setKey,
                field =
                    if (!showWeightInput && field == SetLogKeyboardField.Weight) {
                        SetLogKeyboardField.Reps
                    } else {
                        field
                    },
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
        val current = session ?: return
        if (current.setKey != setKey) return
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

    /**
     * @deprecated Prefer [syncSessionChrome]. Name retained so older call sites compile during
     * migration; still does not reseed weight/reps text.
     */
    @Suppress("UNUSED_PARAMETER")
    fun syncValues(
        setKey: String,
        weightText: String,
        repsText: String,
        rir: Int,
        hasWeightRecommendation: Boolean? = null,
        hasRepsRecommendation: Boolean? = null,
    ) {
        // weightText/repsText intentionally ignored — host is SoT while open.
        syncSessionChrome(
            setKey = setKey,
            rir = rir,
            hasWeightRecommendation = hasWeightRecommendation,
            hasRepsRecommendation = hasRepsRecommendation,
        )
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

    /** Cell tap moved the caret inside the given field. */
    fun setCursor(
        field: SetLogKeyboardField,
        index: Int,
    ) {
        when (field) {
            SetLogKeyboardField.Weight ->
                weightCursor = index.coerceIn(0, weightText.length)
            SetLogKeyboardField.Reps ->
                repsCursor = index.coerceIn(0, repsText.length)
        }
    }

    /**
     * Applies a pad edit to the active field and **commits** text to the session callbacks
     * (live set-log write-through).
     */
    fun applyPadEditAndCommit(updated: NumericEditBuffer) {
        val current = session ?: return
        val coerced = updated.coerced()
        when (current.field) {
            SetLogKeyboardField.Weight -> {
                val textChanged = weightText != coerced.text
                weightText = coerced.text
                weightCursor = coerced.cursor
                if (textChanged) {
                    current.onWeightChange(coerced.text)
                }
            }
            SetLogKeyboardField.Reps -> {
                val textChanged = repsText != coerced.text
                repsText = coerced.text
                repsCursor = coerced.cursor
                if (textChanged) {
                    current.onRepsChange(coerced.text)
                }
            }
        }
    }

    fun activeBuffer(): NumericEditBuffer? {
        val current = session ?: return null
        return when (current.field) {
            SetLogKeyboardField.Weight ->
                NumericEditBuffer(text = weightText, cursor = weightCursor).coerced()
            SetLogKeyboardField.Reps ->
                NumericEditBuffer(text = repsText, cursor = repsCursor).coerced()
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
                            host = host,
                            session = session,
                            contentHeight = contentHeight,
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
    host: SetLogKeyboardHostState,
    session: SetLogKeyboardSession,
    contentHeight: Dp,
) {
    val allowDecimal = session.field == SetLogKeyboardField.Weight
    val liveCanComplete =
        canCompleteSetLog(
            showWeightInput = session.showWeightInput,
            weightText = host.weightText,
            hasWeightRecommendation = session.hasWeightRecommendation,
            repsText = host.repsText,
            hasRepsRecommendation = session.hasRepsRecommendation,
        )
    val completeAndDismiss: () -> Unit = {
        // Host is SoT for weight/reps while open — flush so the commit path sees typed
        // digits even if a write-through was skipped, then skip dock scroll restore so
        // post-log autoscroll is not cancelled by the collapse anchor restore.
        session.onWeightChange(host.weightText)
        session.onRepsChange(host.repsText)
        host.overlay.skipNextCollapseScrollRestore = true
        session.onComplete?.invoke()
        host.dismiss()
    }
    StratumSetLogKeyboardContent(
        state =
            SetLogKeyboardPadState(
                activeField = session.field,
                selectedRir = host.rir,
                showWeightInput = session.showWeightInput,
                allowDecimal = allowDecimal,
                canComplete = liveCanComplete,
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
                        action =
                            resolveSetLogKeyboardAction(
                                active = session.field,
                                canComplete = liveCanComplete,
                                showWeightInput = session.showWeightInput,
                            ),
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
                                canComplete = liveCanComplete,
                                showWeightInput = session.showWeightInput,
                            ),
                        onFieldChange = host::setField,
                        onComplete = completeAndDismiss,
                        onDismiss = host::dismiss,
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
