package com.stratum.feature.workout.ui

import com.stratum.domain.program.SetType
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
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Maps the domain [TemplateChangeSummary] → presentation [TemplateChangeSummaryUi].
 * Pure domain → presentation transform: positions to 1-based, seconds to `m:ss`,
 * set shapes to compact labels. Empty categories are omitted.
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
            TemplateChangeCategory.Order -> TemplateChangeCategoryUi.Order
            TemplateChangeCategory.Sets -> TemplateChangeCategoryUi.Sets
            TemplateChangeCategory.Rest -> TemplateChangeCategoryUi.Rest
            TemplateChangeCategory.Supersets -> TemplateChangeCategoryUi.Supersets
            TemplateChangeCategory.Notes -> TemplateChangeCategoryUi.Notes
        }

    private fun TemplateChangeRow.toRowUi(): TemplateChangeRowUi =
        when (this) {
            is LiftAdded ->
                TemplateChangeRowUi(label = liftName, oldText = null, newText = setCountLabel(setCount))
            is LiftRemoved ->
                TemplateChangeRowUi(label = liftName, oldText = setCountLabel(setCount), newText = null)
            is LiftSwapped ->
                TemplateChangeRowUi(label = "Lift", oldText = oldName, newText = newName)
            is LiftMoved ->
                TemplateChangeRowUi(
                    label = liftName,
                    oldText = "Position ${oldPosition + 1}",
                    newText = "Position ${newPosition + 1}",
                )
            is SetCountChanged ->
                TemplateChangeRowUi(
                    label = liftName,
                    oldText = setCountLabel(oldCount),
                    newText = formatNewCount(newCount, replacedCount),
                )
            is SetEdited ->
                TemplateChangeRowUi(
                    label = "$liftName · Set ${setPosition + 1}",
                    oldText = formatShape(oldShape),
                    newText = formatShape(newShape),
                )
            is RestEdited ->
                TemplateChangeRowUi(
                    label = "$liftName · Set ${setPosition + 1}",
                    oldText = formatRestDisplay(oldSeconds),
                    newText = formatRestDisplay(newSeconds),
                )
            is SupersetGroupingChanged ->
                TemplateChangeRowUi(
                    label = liftName,
                    oldText = formatGrouping(oldPartners),
                    newText = formatGrouping(newPartners),
                )
            is RestBetweenLiftsChanged ->
                TemplateChangeRowUi(
                    label = "$liftName · Rest between lifts",
                    oldText = formatToggle(oldEnabled),
                    newText = formatToggle(newEnabled),
                )
            is PinnedNotesChanged ->
                TemplateChangeRowUi(
                    label = "$liftName · Notes",
                    oldText = formatNotes(oldNotes),
                    newText = formatNotes(newNotes),
                )
        }

    private fun setCountLabel(count: Int): String = if (count == 1) "1 set" else "$count sets"

    /** New total with churn detail, e.g. `3 sets · 2 replaced` (suffix only when churned). */
    private fun formatNewCount(newCount: Int, replacedCount: Int): String {
        val label = setCountLabel(newCount)
        return if (replacedCount > 0) "$label · $replacedCount replaced" else label
    }

    private fun formatToggle(enabled: Boolean): String = if (enabled) "On" else "Off"

    private fun formatGrouping(partners: List<String>): String =
        if (partners.isEmpty()) {
            "Not grouped"
        } else {
            "Grouped with ${partners.joinToString(", ")}"
        }

    private fun formatNotes(notes: List<String>): String =
        if (notes.isEmpty()) {
            "No notes"
        } else {
            notes.joinToString("; ")
        }

    /**
     * Compact set shape, e.g. `Standard 6–10 · RIR 2 · Top`. RIR-only back-offs
     * (`0/0` band) omit the band.
     */
    private fun formatShape(shape: TemplateSetShape): String {
        val prescription =
            buildString {
                append(setTypeLabel(shape.setType, shape.myoRepKind))
                if (shape.repMin != 0 || shape.repMax != 0) {
                    append(" ${formatBand(shape.repMin, shape.repMax)}")
                }
            }
        return buildList {
            add(prescription)
            add("RIR ${shape.rirGoal}")
            if (shape.isTopSet) add("Top")
        }.joinToString(" · ")
    }

    private fun setTypeLabel(setType: SetType, myoRepKind: MyoRepKind?): String =
        when (setType) {
            SetType.Standard -> "Standard"
            SetType.DropSet -> "Drop Set"
            SetType.MyoRep ->
                when (myoRepKind) {
                    MyoRepKind.RepMatching -> "Myo-Rep Match"
                    MyoRepKind.RepFloor, null -> "Myo-Rep"
                }
        }

    private fun formatBand(repMin: Int, repMax: Int): String =
        if (repMin == repMax) "$repMin" else "$repMin–$repMax"
}
