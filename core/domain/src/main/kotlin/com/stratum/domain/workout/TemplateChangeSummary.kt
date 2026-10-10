package com.stratum.domain.workout

import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Categories for the end-of-workout template change summary (STR-125).
 *
 * Order is display order. Equipment changes join this enum once STR-117 equipment
 * swapping lands and the session overlay carries equipment state — there is nothing
 * to diff today.
 */
enum class TemplateChangeCategory {
    Lifts,
    Order,
    Sets,
    Rest,
    Supersets,
    Notes,
}

/** Last-resort lift name when a catalog name is missing; the runtime supplies all names. */
const val UNKNOWN_LIFT_DISPLAY_NAME = "Unknown lift"

/**
 * Compact structural shape of one template set for old → new comparison.
 * Templates carry no load (D2), so shape = type / band / RIR / top-set only.
 */
data class TemplateSetShape(
    val setType: SetType,
    val myoRepKind: MyoRepKind?,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
)

/**
 * One old → new row in the template change summary. Positions are 0-based template
 * positions; presentation maps to 1-based display.
 */
sealed interface TemplateChangeRow {
    val category: TemplateChangeCategory
    /** Display name of the owning lift (post-change name for swaps). */
    val liftName: String
}

/** A lift present in the applied workout but not the baseline. */
data class LiftAdded(
    override val liftName: String,
    val setCount: Int,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Lifts
}

/** A lift present in the baseline but not the applied workout. */
data class LiftRemoved(
    override val liftName: String,
    val setCount: Int,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Lifts
}

/** Same template slot, different catalog lift. */
data class LiftSwapped(
    val oldName: String,
    val newName: String,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Lifts
    override val liftName: String get() = newName
}

/** A lift whose rank among surviving lifts changed (0-based template positions). */
data class LiftMoved(
    override val liftName: String,
    val oldPosition: Int,
    val newPosition: Int,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Order
}

/**
 * Set-count change on a surviving lift, netted to a single old → new totals row.
 * [replacedCount] is the churn hidden by the net (`min(removed, added)`) — non-zero
 * exactly when sets were both removed and added, including net-zero churn where the
 * totals alone would read as no change.
 */
data class SetCountChanged(
    override val liftName: String,
    val oldCount: Int,
    val newCount: Int,
    val replacedCount: Int,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Sets
}

/** A surviving set whose type, band, RIR goal, or top-set marking changed. */
data class SetEdited(
    override val liftName: String,
    /** 0-based position in the applied lift. */
    val setPosition: Int,
    val oldShape: TemplateSetShape,
    val newShape: TemplateSetShape,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Sets
}

/** A surviving set whose inter-set rest changed. */
data class RestEdited(
    override val liftName: String,
    /** 0-based position in the applied lift. */
    val setPosition: Int,
    val oldSeconds: Int,
    val newSeconds: Int,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Rest
}

/**
 * A lift whose superset grouping changed. Partners are the other lift names sharing
 * the group (empty = ungrouped side).
 */
data class SupersetGroupingChanged(
    override val liftName: String,
    val oldPartners: List<String>,
    val newPartners: List<String>,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Supersets
}

/** A lift whose rest-timer-between-lifts toggle flipped. */
data class RestBetweenLiftsChanged(
    override val liftName: String,
    val oldEnabled: Boolean,
    val newEnabled: Boolean,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Supersets
}

/** A surviving lift whose pinned workout notes changed (effective list content). */
data class PinnedNotesChanged(
    override val liftName: String,
    val oldNotes: List<String>,
    val newNotes: List<String>,
) : TemplateChangeRow {
    override val category: TemplateChangeCategory = TemplateChangeCategory.Notes
}

/**
 * Ordered template change rows: category order ([TemplateChangeCategory]), then lift
 * position within each category. Empty when baseline and applied match.
 */
data class TemplateChangeSummary(
    val rows: List<TemplateChangeRow>,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
    val rowCount: Int get() = rows.size
}
