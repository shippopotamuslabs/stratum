package com.stratum.feature.workout.ui

import com.stratum.domain.workout.EquipmentSwapped
import com.stratum.domain.workout.LiftAdded
import com.stratum.domain.workout.LiftMoved
import com.stratum.domain.workout.LiftRemoved
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.workout.PinnedNotesChanged
import com.stratum.domain.workout.RestBetweenLiftsChanged
import com.stratum.domain.workout.RestEdited
import com.stratum.domain.workout.SetCountChanged
import com.stratum.domain.workout.SetEdited
import com.stratum.domain.workout.SupersetGroupingChanged
import com.stratum.domain.workout.TemplateChangeCategory
import com.stratum.domain.workout.TemplateChangeRow
import com.stratum.domain.workout.TemplateChangeSummary
import com.stratum.domain.workout.TemplateSetShape

/**
 * Maps the domain [TemplateChangeSummary] → presentation [TemplateChangeSummaryUi].
 * Structural only: domain values become typed presentation values (1-based display
 * positions, raw counts/seconds/booleans, presentation shapes). User-visible copy
 * is resolved from resources in [TemplateChangeSummarySection]. Empty categories
 * are omitted.
 */
internal object TemplateChangeSummaryMapper {
    fun map(summary: TemplateChangeSummary): TemplateChangeSummaryUi {
        val groups =
            TemplateChangeCategory.entries.mapNotNull { category ->
                val rows =
                    summary.rows
                        .filter { it.category == category }
                        .map { it.toRowUi() }
                if (rows.isEmpty()) {
                    null
                } else {
                    TemplateChangeGroupUi(category.toCategoryUi(), rows)
                }
            }
        return TemplateChangeSummaryUi(groups)
    }

    private fun TemplateChangeCategory.toCategoryUi(): TemplateChangeCategoryUi =
        when (this) {
            TemplateChangeCategory.Lifts -> TemplateChangeCategoryUi.Lifts
            TemplateChangeCategory.Equipment -> TemplateChangeCategoryUi.Equipment
            TemplateChangeCategory.Order -> TemplateChangeCategoryUi.Order
            TemplateChangeCategory.Sets -> TemplateChangeCategoryUi.Sets
            TemplateChangeCategory.Rest -> TemplateChangeCategoryUi.Rest
            TemplateChangeCategory.Supersets -> TemplateChangeCategoryUi.Supersets
            TemplateChangeCategory.Notes -> TemplateChangeCategoryUi.Notes
        }

    private fun TemplateChangeRow.toRowUi(): TemplateChangeRowUi =
        when (this) {
            is LiftAdded -> LiftAddedUi(liftName, setCount)
            is LiftRemoved -> LiftRemovedUi(liftName, setCount)
            is LiftSwapped -> LiftSwappedUi(oldName, newName)
            is EquipmentSwapped -> EquipmentSwappedUi(liftName, oldId?.value, newId.value)
            is LiftMoved -> LiftMovedUi(liftName, oldPosition + 1, newPosition + 1)
            is SetCountChanged -> SetCountChangedUi(liftName, oldCount, newCount, replacedCount)
            is SetEdited -> SetEditedUi(liftName, setPosition + 1, oldShape.toShapeUi(), newShape.toShapeUi())
            is RestEdited -> RestEditedUi(liftName, setPosition + 1, oldSeconds, newSeconds)
            is SupersetGroupingChanged -> SupersetGroupingChangedUi(liftName, oldPartners, newPartners)
            is RestBetweenLiftsChanged -> RestBetweenLiftsChangedUi(liftName, oldEnabled, newEnabled)
            is PinnedNotesChanged -> PinnedNotesChangedUi(liftName, oldNotes, newNotes)
        }

    private fun TemplateSetShape.toShapeUi(): TemplateSetShapeUi =
        TemplateSetShapeUi(
            setKind = toWorkoutSetKind(setType, myoRepKind),
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            isTopSet = isTopSet,
        )
}
