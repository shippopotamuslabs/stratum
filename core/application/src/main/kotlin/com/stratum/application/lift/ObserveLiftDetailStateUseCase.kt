package com.stratum.application.lift

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Application-layer read model for the lift detail screen (STR-285).
 *
 * The selected lift resolved from the catalog plus the display unit, so the
 * ViewModel can map presentation state from a single durable source per
 * §12.4. [lift] is null until the catalog contains the selected id.
 */
data class LiftDetailSnapshot(
    val liftId: String?,
    val lift: Lift?,
    val displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
)

/**
 * Owns the lift-detail multi-source composition (STR-285).
 *
 * Combines the selected lift id, the lift catalog, and the weight display
 * preferences into one [LiftDetailSnapshot] stream. Resolution semantics are
 * unchanged from the former ViewModel pipeline: first catalog lift whose id
 * matches, display unit from prefs.
 */
class ObserveLiftDetailStateUseCase @Inject constructor(
    private val observeLiftCatalog: ObserveLiftCatalogUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) {
    operator fun invoke(liftId: Flow<String?>): Flow<LiftDetailSnapshot> =
        combine(
            liftId,
            observeLiftCatalog(),
            observeWeightDisplayPreferences(),
        ) { id, catalog, weightPrefs ->
            LiftDetailSnapshot(
                liftId = id,
                lift = catalog.firstOrNull { it.id.value == id },
                displayUnit = weightPrefs.displayUnit,
            )
        }
}
