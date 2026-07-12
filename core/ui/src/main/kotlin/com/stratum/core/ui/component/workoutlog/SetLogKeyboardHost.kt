package com.stratum.core.ui.component.workoutlog

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

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
 * Scrolls a focused cell so it sits above the keyboard dock. Implemented by the list host
 * (e.g. [LazyListState.animateScrollBy]); cells only supply window bounds.
 */
fun interface SetLogKeyboardRevealScroller {
    /**
     * Scroll the list so [cellBoundsInWindow] sits fully above the keyboard.
     * No-op when the cell is already clear of the dock.
     */
    suspend fun ensureCellAboveKeyboard(cellBoundsInWindow: Rect)
}

/**
 * List/screen-level keyboard host. Set rows open a session; the dock is composed once
 * outside the scrollable so large scrolls cannot tear down the pad.
 */
@Stable
class SetLogKeyboardHostState {
    var session: SetLogKeyboardSession? by mutableStateOf(null)
        private set

    /** Live draft values for the open set (updated via [syncValues]). */
    var weightText: String by mutableStateOf("")
        private set
    var repsText: String by mutableStateOf("")
        private set
    var rir: Int by mutableIntStateOf(2)
        private set

    /**
     * Total height of the dock (pad + nav bar) while open, else 0. Host lists should add this
     * to LazyColumn **contentPadding** (not Modifier.padding) so low fields can scroll up.
     */
    var dockHeightDp: Dp by mutableStateOf(0.dp)
        internal set

    /** Window Y of the top edge of the keyboard dock while open (for reveal math). */
    var dockTopInWindow: Float by mutableFloatStateOf(Float.NaN)
        internal set

    /**
     * List-level scroller. Set by [ProvideSetLogKeyboardHost] consumers that own a LazyList.
     */
    var revealScroller: SetLogKeyboardRevealScroller? by mutableStateOf(null)

    /** Bumps when a session opens or field changes so cells re-request reveal. */
    var revealEpoch: Int by mutableIntStateOf(0)
        private set

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
        revealEpoch++
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
            revealEpoch++
        }
    }

    fun dismiss() {
        session = null
        dockTopInWindow = Float.NaN
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
    val density = LocalDensity.current
    val contentHeight =
        remember(configuration.screenHeightDp) {
            setLogKeyboardContentHeight(configuration)
        }
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navBottomDp = with(density) { navBottomPx.toDp() }
    val dockHeight = if (host.isOpen) contentHeight + navBottomDp else 0.dp
    host.dockHeightDp = dockHeight

    CompositionLocalProvider(LocalSetLogKeyboardHost provides host) {
        Box(modifier = modifier.fillMaxSize()) {
            // Full-size list — keyboard overlays; list uses contentPadding for dock height.
            content(Modifier.fillMaxSize())
            val session = host.session
            if (session != null) {
                SetLogKeyboardDock(
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
                    onDockPositioned = { topInWindow ->
                        host.dockTopInWindow = topInWindow
                    },
                    // Pin to the physical bottom; background fills the nav-bar region so the
                    // list cannot peek under the pad (insets only pad the key content).
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SetLogKeyboardDock(
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
    onDockPositioned: (topInWindow: Float) -> Unit,
    modifier: Modifier = Modifier,
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

    // Order: background first (edge-to-edge to screen bottom), then nav-bar padding for keys.
    Column(
        modifier =
            modifier
                .background(StratumThemeAccessor.colors.surfaceRaised)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .onGloballyPositioned { coords ->
                    // Top of the key content (above the nav-bar inset), used for reveal scroll.
                    onDockPositioned(coords.boundsInWindow().top)
                }
                .fillMaxWidth(),
    ) {
        val liveCanComplete =
            canCompleteSetLog(
                showWeightInput = session.showWeightInput,
                weightText = weightBuffer,
                hasWeightRecommendation = session.hasWeightRecommendation,
                repsText = repsBuffer,
                hasRepsRecommendation = session.hasRepsRecommendation,
            )
        StratumSetLogKeyboardContent(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
            activeField = session.field,
            selectedRir = rir,
            weightText = weightBuffer,
            repsText = repsBuffer,
            showWeightInput = session.showWeightInput,
            allowDecimal = allowDecimal,
            canComplete = liveCanComplete,
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
                when (
                    resolveSetLogKeyboardAction(
                        active = session.field,
                        canComplete = liveCanComplete,
                        showWeightInput = session.showWeightInput,
                    )
                ) {
                    SetLogKeyboardAction.FocusReps -> onFieldChange(SetLogKeyboardField.Reps)
                    SetLogKeyboardAction.FocusWeight -> onFieldChange(SetLogKeyboardField.Weight)
                    SetLogKeyboardAction.Complete -> onComplete()
                    SetLogKeyboardAction.Dismiss -> onDismiss()
                }
            },
            onDismiss = onDismiss,
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
