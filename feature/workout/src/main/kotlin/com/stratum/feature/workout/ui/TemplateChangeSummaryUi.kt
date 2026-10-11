package com.stratum.feature.workout.ui

/**
 * Presentation model for the end-of-workout template change summary (STR-125).
 * Structural values only — no domain types (ARCHITECTURE.md §4.3) and no resolved
 * copy: [TemplateChangeSummarySection] resolves every user-visible string from
 * resources so the summary stays localizable.
 */
enum class TemplateChangeCategoryUi {
    Lifts,
    Equipment,
    Order,
    Sets,
    Rest,
    Supersets,
    Notes,
}

/**
 * One old → new row. Positions are 1-based display values (mapped from 0-based
 * domain positions). Each subtype carries exactly one side for additions and
 * removals; the section renders the missing side as added/removed.
 */
sealed interface TemplateChangeRowUi {
    /** Display name of the owning lift (post-change name for swaps). */
    val liftName: String
}

data class LiftAddedUi(
    override val liftName: String,
    val setCount: Int,
) : TemplateChangeRowUi

data class LiftRemovedUi(
    override val liftName: String,
    val setCount: Int,
) : TemplateChangeRowUi

data class LiftSwappedUi(
    val oldName: String,
    val newName: String,
) : TemplateChangeRowUi {
    override val liftName: String get() = newName
}

/**
 * Equipment reassigned on a surviving template slot. Names resolve from the
 * equipment catalog in the ViewModel (the session runtime has no catalog); ids
 * stay for the join. A null old id means the template had no assignment.
 */
data class EquipmentSwappedUi(
    override val liftName: String,
    val oldId: String?,
    val newId: String,
    val oldName: String? = null,
    val newName: String? = null,
) : TemplateChangeRowUi

data class LiftMovedUi(
    override val liftName: String,
    val oldPosition: Int,
    val newPosition: Int,
) : TemplateChangeRowUi

data class SetCountChangedUi(
    override val liftName: String,
    val oldCount: Int,
    val newCount: Int,
    val replacedCount: Int,
) : TemplateChangeRowUi

data class SetEditedUi(
    override val liftName: String,
    val setPosition: Int,
    val oldShape: TemplateSetShapeUi,
    val newShape: TemplateSetShapeUi,
) : TemplateChangeRowUi

data class RestEditedUi(
    override val liftName: String,
    val setPosition: Int,
    val oldSeconds: Int,
    val newSeconds: Int,
) : TemplateChangeRowUi

data class SupersetGroupingChangedUi(
    override val liftName: String,
    val oldPartners: List<String>,
    val newPartners: List<String>,
) : TemplateChangeRowUi

data class RestBetweenLiftsChangedUi(
    override val liftName: String,
    val oldEnabled: Boolean,
    val newEnabled: Boolean,
) : TemplateChangeRowUi

data class PinnedNotesChangedUi(
    override val liftName: String,
    val oldNotes: List<String>,
    val newNotes: List<String>,
) : TemplateChangeRowUi

/** Presentation shape of one template set (primitives only — no domain shape). */
data class TemplateSetShapeUi(
    val setKind: WorkoutSetKind,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
)

/** Non-empty group of rows for one category; empty categories are omitted upstream. */
data class TemplateChangeGroupUi(
    val category: TemplateChangeCategoryUi,
    val rows: List<TemplateChangeRowUi>,
)

/**
 * Backfills [EquipmentSwappedUi] display names from the equipment catalog (STR-124).
 * Pure: the mapper emits ids only (the session runtime has no catalog), and the
 * ViewModel joins names at the same enrichment seam as lift equipment names.
 * Unknown ids keep null names so the section renders the unassigned fallback.
 */
internal fun enrichTemplateChangeNames(
    summary: TemplateChangeSummaryUi,
    names: Map<String, String>,
): TemplateChangeSummaryUi =
    summary.copy(
        groups =
            summary.groups.map { group ->
                group.copy(
                    rows =
                        group.rows.map { row ->
                            if (row is EquipmentSwappedUi) {
                                row.copy(
                                    oldName = row.oldId?.let(names::get),
                                    newName = names[row.newId],
                                )
                            } else {
                                row
                            }
                        },
                )
            },
    )

data class TemplateChangeSummaryUi(
    val groups: List<TemplateChangeGroupUi>,
) {
    val rowCount: Int get() = groups.sumOf { it.rows.size }
    val isEmpty: Boolean get() = groups.isEmpty()
}
