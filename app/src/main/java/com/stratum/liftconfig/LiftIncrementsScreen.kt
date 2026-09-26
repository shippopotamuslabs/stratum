package com.stratum.liftconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumOutlinedButton
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumSwitch
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTextField
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs

@Composable
fun LiftIncrementsRoute(
    liftId: String,
    onBackClick: () -> Unit,
    viewModel: LiftIncrementsViewModel = hiltViewModel(),
    initialTiersLb: List<ProposalTierArgs> = emptyList(),
    proposalContext: ProposalEditContext? = null,
) {
    LaunchedEffect(liftId, initialTiersLb, proposalContext) {
        viewModel.initialize(liftId, initialTiersLb, proposalContext)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) onBackClick()
    }
    var showScanDrawer by rememberSaveable { mutableStateOf(false) }
    var drawerError by remember { mutableStateOf<String?>(null) }
    val photoFailedMessage = stringResource(UiR.string.lift_increments_scan_unreadable)
    val cameraDeniedMessage = stringResource(UiR.string.lift_increments_scan_camera_blocked)
    val capture =
        rememberScanPhotoCapture(
            onScanPhoto = viewModel::scan,
            onPhotoError = { drawerError = photoFailedMessage },
            onPermissionDenied = { drawerError = cameraDeniedMessage },
        )
    LaunchedEffect(state.scanUi) {
        if (state.scanUi is IncrementScanState.Scanning ||
            state.scanUi is IncrementScanState.Scanned
        ) {
            showScanDrawer = false
        }
    }
    LiftIncrementsScreen(
        state = state,
        onBackClick = onBackClick,
        onTierLowerChanged = viewModel::onTierLowerChanged,
        onTierUpperChanged = viewModel::onTierUpperChanged,
        onTierIncrementChanged = viewModel::onTierIncrementChanged,
        onOpenEndedToggled = viewModel::onOpenEndedToggled,
        onAddTier = viewModel::addTier,
        onRemoveTier = viewModel::removeTier,
        onSave = viewModel::save,
        microActions =
            MicroEditorActions(
                onStopChanged = viewModel::onMicroChanged,
                onAddStop = viewModel::onAddMicro,
                onRemoveStop = viewModel::onRemoveMicro,
            ),
        onResetToEquipment = viewModel::resetToEquipmentDefaults,
        scanEntry =
            ScanEntryUi(
                available = state.scanAvailable,
                state = state.scanUi,
                onScanClick = {
                    drawerError = null
                    showScanDrawer = true
                },
            ),
    )
    if (showScanDrawer) {
        ScanSourceDrawer(
            onDismiss = { showScanDrawer = false },
            onCameraClick = {
                drawerError = null
                capture.launchCamera()
            },
            onGalleryClick = {
                drawerError = null
                capture.launchGallery()
            },
            error = drawerError,
            scanning = state.scanUi is IncrementScanState.Scanning,
        )
    }
}

/**
 * Adder-card callbacks, grouped so the screen stays under the parameter-count gate.
 * The equipment editor wires live handlers; other callers use the no-op defaults.
 */
data class MicroEditorActions(
    val onStopChanged: (Int, String) -> Unit = { _, _ -> },
    val onAddStop: () -> Unit = {},
    val onRemoveStop: (Int) -> Unit = {},
)

/**
 * Equipment-page fork extras, grouped so the shell stays under the parameter-count gate:
 *
 * - [showForkSaveBar]: two-button bar (Update Shared / Save As New). When false the
 *   legacy single Save button renders (per-lift override editor).
 * - [affectsBanner]: blast-radius banner slot. Null renders no banner;
 *   [AffectsBannerUi.onClick] null keeps it display-only. The lift override editor
 *   passes null.
 */
data class ForkSaveUi(
    val showForkSaveBar: Boolean = false,
    val onSaveAsNew: () -> Unit = {},
    val affectsBanner: AffectsBannerUi? = null,
)

/**
 * AI scan entry for the increments editor, grouped so the shell stays under the
 * parameter-count gate. The photo drawer + launchers live at the routes; the shell
 * only renders the entry, the scanning/error states, and retry.
 */
data class ScanEntryUi(
    val available: Boolean = false,
    val state: IncrementScanState = IncrementScanState.Idle,
    val onScanClick: () -> Unit = {},
)

@Composable
fun LiftIncrementsScreen(
    state: LiftIncrementsUiState,
    onBackClick: () -> Unit,
    onTierLowerChanged: (Int, String) -> Unit,
    onTierUpperChanged: (Int, String) -> Unit,
    onTierIncrementChanged: (Int, String) -> Unit,
    onOpenEndedToggled: (Int, Boolean) -> Unit,
    onAddTier: () -> Unit,
    onRemoveTier: (Int) -> Unit,
    onSave: () -> Unit,
    microActions: MicroEditorActions = MicroEditorActions(),
    onResetToEquipment: () -> Unit = {},
    forkUi: ForkSaveUi = ForkSaveUi(),
    scanEntry: ScanEntryUi = ScanEntryUi(),
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = state.liftName.ifBlank { stringResource(UiR.string.lift_increments_title) },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(UiR.string.lift_config_navigate_back),
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
                    .padding(horizontal = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                item {
                    IncrementsHelpCard()
                }
                if (forkUi.affectsBanner != null) {
                    item {
                        InheritanceBanner(
                            message = forkUi.affectsBanner.message,
                            onClick = forkUi.affectsBanner.onClick,
                        )
                    }
                }
                if (state.inheritanceBanner != null) {
                    item {
                        InheritanceBanner(message = state.inheritanceBanner)
                    }
                } else if (state.showResetToEquipment) {
                    item {
                        InheritanceBanner(
                            message = stringResource(UiR.string.lift_increments_override_banner),
                        )
                    }
                }
                if (state.validationMessage != null) {
                    item {
                        IncrementsValidationBanner(message = state.validationMessage)
                    }
                }
                if (state.warnedTierIndexes.isNotEmpty()) {
                    item {
                        SampleWarningBanner()
                    }
                }
                itemsIndexed(state.tiers, key = { index, _ -> index }) { index, tier ->
                    TierEditorCard(
                        index = index,
                        tier = tier,
                        unitLabel = state.unitLabel,
                        isLast = index == state.tiers.lastIndex,
                        canRemove = state.tiers.size > 1,
                        hasWarning = index in state.warnedTierIndexes,
                        onLowerChanged = { onTierLowerChanged(index, it) },
                        onUpperChanged = { onTierUpperChanged(index, it) },
                        onIncrementChanged = { onTierIncrementChanged(index, it) },
                        onOpenEndedToggled = { onOpenEndedToggled(index, it) },
                        onRemove = { onRemoveTier(index) },
                    )
                }
                item {
                    StratumTextButton(
                        text = stringResource(UiR.string.lift_increments_add_tier),
                        onClick = onAddTier,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.showMicroCard) {
                    item {
                        MicroEditorCard(
                            stops = state.microStops,
                            unitLabel = state.unitLabel,
                            onStopChanged = microActions.onStopChanged,
                            onAddStop = microActions.onAddStop,
                            onRemoveStop = microActions.onRemoveStop,
                        )
                    }
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.showResetToEquipment) {
                    StratumOutlinedButton(
                        text = stringResource(UiR.string.lift_increments_reset_to_equipment),
                        onClick = onResetToEquipment,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (scanEntry.available) {
                    ScanEntryRow(
                        scanning = scanEntry.state is IncrementScanState.Scanning,
                        onClick = scanEntry.onScanClick,
                    )
                }
                if (scanEntry.state is IncrementScanState.Failed) {
                    IncrementsValidationBanner(
                        message = stringResource(UiR.string.lift_increments_scan_failed),
                    )
                    StratumTextButton(
                        text = stringResource(UiR.string.lift_increments_scan_retry),
                        onClick = scanEntry.onScanClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (forkUi.showForkSaveBar) {
                    StratumButton(
                        text = stringResource(UiR.string.equipment_increments_update_shared),
                        onClick = onSave,
                        enabled = !state.isSaving && state.validationMessage == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StratumOutlinedButton(
                        text = stringResource(UiR.string.equipment_increments_save_as_new),
                        onClick = forkUi.onSaveAsNew,
                        enabled = !state.isSaving && state.validationMessage == null,
                        modifier = Modifier.padding(bottom = 20.dp).fillMaxWidth(),
                    )
                } else {
                    StratumButton(
                        text = stringResource(UiR.string.lift_increments_save),
                        onClick = onSave,
                        enabled = !state.isSaving && state.validationMessage == null,
                        modifier = Modifier.padding(bottom = 20.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Blast-radius banner for the equipment editor (message plus optional sheet entry). */
data class AffectsBannerUi(
    val message: String,
    val onClick: (() -> Unit)? = null,
)

/**
 * Bottom AI scan entry: outlined container with a leading camera icon and label.
 * Theme roles only (no raw button chrome), matching the editor banner treatment.
 */
@Composable
private fun ScanEntryRow(scanning: Boolean, onClick: () -> Unit) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.accent,
                    shape = shapes.panel,
                )
                .clickable(enabled = !scanning, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
        StratumText(
            text =
                stringResource(
                    if (scanning) {
                        UiR.string.lift_increments_scan_scanning
                    } else {
                        UiR.string.lift_increments_scan_cta
                    },
                ),
            style = StratumThemeAccessor.typography.bodyLarge,
        )
    }
}

/**
 * Photo-source drawer for the increments AI scan. Owned by the routes (which hold
 * the capture launchers); [error] carries route-local photo failures (unreadable
 * image, denied camera permission). Scan-request failures surface in the editor
 * via the shell error banner instead.
 */
@Composable
internal fun ScanSourceDrawer(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    error: String?,
    scanning: Boolean,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(UiR.string.lift_increments_scan_sheet_title),
    ) {
        ScanSourceRow(
            icon = Icons.Outlined.PhotoCamera,
            label = stringResource(UiR.string.lift_increments_scan_camera),
            enabled = !scanning,
            onClick = onCameraClick,
        )
        ScanSourceRow(
            icon = Icons.Outlined.Image,
            label = stringResource(UiR.string.lift_increments_scan_gallery),
            enabled = !scanning,
            onClick = onGalleryClick,
        )
        if (scanning) {
            StratumText(
                text = stringResource(UiR.string.lift_increments_scan_scanning),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (error != null) {
            IncrementsValidationBanner(message = error)
        }
    }
}

@Composable
private fun ScanSourceRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(24.dp),
        )
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.bodyLarge,
            muted = !enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InheritanceBanner(
    message: String,
    onClick: (() -> Unit)? = null,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    var modifier =
        Modifier
            .fillMaxWidth()
            .background(color = colors.surfaceRaised, shape = shapes.panel)
            .border(
                width = shapes.borderWidth,
                color = colors.accent,
                shape = shapes.panel,
            )
    if (onClick != null) {
        modifier = modifier.clickable(onClick = onClick)
    }
    StratumText(
        text = message,
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun IncrementsHelpCard() {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
        StratumText(
            text = stringResource(UiR.string.lift_increments_help),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IncrementsValidationBanner(message: String) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    StratumText(
        text = message,
        style =
            StratumThemeAccessor.typography.bodyLarge.copy(
                color = colors.error,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.error,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun SampleWarningBanner() {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.warning,
                    shape = shapes.panel,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_info),
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp),
        )
        StratumText(
            text = stringResource(UiR.string.lift_increments_sample_warning),
            style = StratumThemeAccessor.typography.sectionLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TierEditorCard(
    index: Int,
    tier: IncrementTierDraft,
    unitLabel: String,
    isLast: Boolean,
    canRemove: Boolean,
    onLowerChanged: (String) -> Unit,
    onUpperChanged: (String) -> Unit,
    onIncrementChanged: (String) -> Unit,
    onOpenEndedToggled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    /** True when this band's step misses a proposal's logged weight (yellow, non-blocking). */
    hasWarning: Boolean = false,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val summary = tierSummaryText(tier, unitLabel)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = if (hasWarning) colors.warning else colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_tier_label, index + 1),
                    style = StratumThemeAccessor.typography.titleMedium,
                )
                if (summary != null) {
                    StratumNumber(
                        text = summary,
                        style = StratumThemeAccessor.typography.sectionLabel,
                    )
                }
            }
            if (canRemove) {
                StratumIconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(UiR.string.lift_increments_remove_tier),
                        tint = colors.textMuted,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumTextField(
                value = tier.lowerBound,
                onValueChange = onLowerChanged,
                label = "${stringResource(UiR.string.lift_increments_lower)} ($unitLabel)",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (!tier.openEnded) {
                StratumTextField(
                    value = tier.upperBound.orEmpty(),
                    onValueChange = onUpperChanged,
                    label = "${stringResource(UiR.string.lift_increments_upper)} ($unitLabel)",
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            } else {
                StratumTextField(
                    value = stringResource(UiR.string.lift_increments_upper_open),
                    onValueChange = {},
                    label = "${stringResource(UiR.string.lift_increments_upper)} ($unitLabel)",
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )
            }
            StratumTextField(
                value = tier.increment,
                onValueChange = onIncrementChanged,
                label = "${stringResource(UiR.string.lift_increments_step)} ($unitLabel)",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        if (isLast) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(shapes.borderWidth)
                        .background(colors.borderSubtle),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(
                    text = stringResource(UiR.string.lift_increments_open_ended),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumSwitch(
                    checked = tier.openEnded,
                    onCheckedChange = onOpenEndedToggled,
                )
            }
        }
    }
}

@Composable
private fun MicroEditorCard(
    stops: List<String>,
    unitLabel: String,
    onStopChanged: (Int, String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (Int) -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val summary =
        if (stops.isEmpty()) {
            stringResource(UiR.string.lift_increments_micro_empty)
        } else {
            "0 / ${stops.joinToString(separator = " / ")} $unitLabel"
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.surfaceRaised, shape = shapes.panel)
                .border(
                    width = shapes.borderWidth,
                    color = colors.borderSubtle,
                    shape = shapes.panel,
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StratumText(
            text = stringResource(UiR.string.lift_increments_micro_title),
            style = StratumThemeAccessor.typography.titleMedium,
        )
        StratumNumber(
            text = summary,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        StratumText(
            text = stringResource(UiR.string.lift_increments_micro_help),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        stops.forEachIndexed { index, stop ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumTextField(
                    value = stop,
                    onValueChange = { onStopChanged(index, it) },
                    label = stringResource(UiR.string.lift_increments_micro_stop, unitLabel),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                StratumIconButton(onClick = { onRemoveStop(index) }) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(UiR.string.lift_increments_micro_remove),
                        tint = colors.textMuted,
                    )
                }
            }
        }
        StratumTextButton(
            text = stringResource(UiR.string.lift_increments_micro_add),
            onClick = onAddStop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun tierSummaryText(tier: IncrementTierDraft, unitLabel: String): String? {
    val lower = tier.lowerBound.trim()
    val step = tier.increment.trim()
    if (lower.isEmpty() || step.isEmpty()) return null
    val body =
        if (tier.openEnded) {
            stringResource(UiR.string.lift_increments_summary_open, lower, step)
        } else {
            val upper = tier.upperBound?.trim().orEmpty()
            if (upper.isEmpty()) return null
            stringResource(UiR.string.lift_increments_summary_closed, lower, upper, step)
        }
    return "$body $unitLabel"
}
