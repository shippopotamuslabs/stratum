package com.stratum.domain.onboarding

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveSelectedPresetUseCase
import com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Composes every persisted onboarding stream into a single [OnboardingWizardState] flow.
 * ViewModels consume this aggregate read model instead of combining five streams
 * (ARCHITECTURE.md §4.3).
 */
class GetOnboardingWizardStateUseCase @Inject constructor(
    private val getOnboardingState: GetOnboardingStateUseCase,
    private val observeSpecialization: ObserveSpecializationUseCase,
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val observeSelectedPreset: ObserveSelectedPresetUseCase,
    private val observeUsedHistoricalImport: ObserveUsedHistoricalImportUseCase,
) {
    operator fun invoke(): Flow<OnboardingWizardState> =
        combine(
            getOnboardingState(),
            observeSpecialization(),
            observeLiftCatalog(),
            observeSelectedPreset(),
            observeUsedHistoricalImport(),
        ) { onboardingState, specialization, lifts, preset, usedImport ->
            OnboardingWizardState(
                progress = onboardingState.progress,
                profile = onboardingState.profile,
                specialization = specialization,
                lifts = lifts,
                selectedPreset = preset,
                usedHistoricalImport = usedImport,
            )
        }
}
