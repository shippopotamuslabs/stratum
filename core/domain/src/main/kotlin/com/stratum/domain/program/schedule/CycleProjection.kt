package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.Weekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar projection for a cycle window. Slot identity is index-only; weekdays/dates are
 * derived from [cycleStartEpochDay] (epoch day of slot 0 for the current cycle).
 */
object CycleProjection {
    fun projectedEpochDay(cycleStartEpochDay: Long, slotIndex: Int): Long {
        require(slotIndex >= 0) { "slotIndex must be non-negative, was $slotIndex" }
        return cycleStartEpochDay + slotIndex
    }

    fun projectedDate(cycleStartEpochDay: Long, slotIndex: Int): LocalDate =
        LocalDate.ofEpochDay(projectedEpochDay(cycleStartEpochDay, slotIndex))

    fun projectedWeekday(cycleStartEpochDay: Long, slotIndex: Int): Weekday =
        projectedDate(cycleStartEpochDay, slotIndex).toDomainWeekday()

    /**
     * UI label for a projected slot, e.g. `Monday - 6/17` (full weekday, month/day without
     * zero-padding).
     */
    fun formatSlotLabel(
        cycleStartEpochDay: Long,
        slotIndex: Int,
        locale: Locale = Locale.US,
    ): String = formatSlotLabel(projectedDate(cycleStartEpochDay, slotIndex), locale)

    fun formatSlotLabel(date: LocalDate, locale: Locale = Locale.US): String {
        val weekdayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return "$weekdayName - ${date.monthValue}/${date.dayOfMonth}"
    }

    /** Next cycle's slot-0 epoch day after completing a full cycle of [cycleLength] slots. */
    fun nextCycleStartEpochDay(cycleStartEpochDay: Long, cycleLength: Int): Long {
        require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
        return cycleStartEpochDay + cycleLength
    }

    /**
     * Projected view of every slot in the current cycle window (length = [slots].size).
     */
    fun projectCycleWindow(
        slots: List<CycleSlot>,
        cycleStartEpochDay: Long,
        locale: Locale = Locale.US,
    ): List<ProjectedCycleDay> =
        slots.map { slot ->
            val date = projectedDate(cycleStartEpochDay, slot.slotIndex)
            ProjectedCycleDay(
                slot = slot,
                date = date,
                weekday = date.toDomainWeekday(),
                label = formatSlotLabel(date, locale),
            )
        }
}

/** One row in the Programs cycle strip: slot + projected calendar identity. */
data class ProjectedCycleDay(
    val slot: CycleSlot,
    val date: LocalDate,
    val weekday: Weekday,
    val label: String,
)

fun LocalDate.toDomainWeekday(): Weekday =
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> Weekday.Monday
        DayOfWeek.TUESDAY -> Weekday.Tuesday
        DayOfWeek.WEDNESDAY -> Weekday.Wednesday
        DayOfWeek.THURSDAY -> Weekday.Thursday
        DayOfWeek.FRIDAY -> Weekday.Friday
        DayOfWeek.SATURDAY -> Weekday.Saturday
        DayOfWeek.SUNDAY -> Weekday.Sunday
    }
