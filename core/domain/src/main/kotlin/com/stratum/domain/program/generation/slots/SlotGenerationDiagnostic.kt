package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * A non-fatal program-generation event recorded while building slots (STR-254).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs (CODE_STYLE_RULES.md §1),
 * so the slot builders record them on the caller's collector and the generation pipeline
 * reports each one exactly once through `ProgramGenerationTelemetryLogger`.
 * Diagnostics never reach the UI.
 */
sealed interface SlotGenerationDiagnostic {
    /**
     * A cross-domain touch slot was omitted because its resolved pattern string is not a
     * known [MovementPattern] — the touch for [muscle] was dropped from the session.
     */
    data class CrossDomainTouchSkipped(
        val patternName: String,
        val muscle: MuscleGroup,
    ) : SlotGenerationDiagnostic

    /**
     * An entire spec-focus isolation slot was dropped because its resolved pattern string
     * is not a known [MovementPattern] — no slot was added for [muscle].
     */
    data class SpecFocusSlotSkipped(
        val patternName: String,
        val muscle: MuscleGroup,
    ) : SlotGenerationDiagnostic

    /**
     * A lift was silently excluded from spec-focus candidacy for [patternName] because the
     * muscle string is not a known [MuscleGroup] — every lift was rejected for that check.
     */
    data class SpecFocusMuscleUnrecognized(
        val muscleName: String,
        val patternName: String,
    ) : SlotGenerationDiagnostic
}

/**
 * Parses [patternName] for the cross-domain touch slot owned by [muscle] (STR-254): records
 * a [SlotGenerationDiagnostic.CrossDomainTouchSkipped] on this collector and returns null
 * when the string is unrecognized, so the caller omits the touch slot. A null collector
 * still omits — the omission is the safety behavior; the record is the visibility.
 */
internal fun MutableList<SlotGenerationDiagnostic>?.parseTouchPatternOrRecord(
    patternName: String,
    muscle: MuscleGroup,
): MovementPattern? {
    val parsed = runCatching { MovementPattern.valueOf(patternName) }.getOrNull()
    if (parsed == null) {
        this?.add(SlotGenerationDiagnostic.CrossDomainTouchSkipped(patternName, muscle))
    }
    return parsed
}

/**
 * Parses [patternName] for the spec-focus isolation slot owned by [muscle] (STR-254):
 * records a [SlotGenerationDiagnostic.SpecFocusSlotSkipped] on this collector and returns
 * null when the string is unrecognized, so the caller drops the entire slot.
 */
internal fun MutableList<SlotGenerationDiagnostic>?.parseSpecFocusPatternOrRecord(
    patternName: String,
    muscle: MuscleGroup,
): MovementPattern? {
    val parsed = runCatching { MovementPattern.valueOf(patternName) }.getOrNull()
    if (parsed == null) {
        this?.add(SlotGenerationDiagnostic.SpecFocusSlotSkipped(patternName, muscle))
    }
    return parsed
}

/**
 * Parses [muscleName] for the spec-focus lift-candidacy check on [patternName] (STR-254):
 * records a [SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized] on this collector and
 * returns null when the string is unrecognized, so the caller excludes every lift.
 */
internal fun MutableList<SlotGenerationDiagnostic>?.parseSpecFocusMuscleOrRecord(
    muscleName: String,
    patternName: String,
): MuscleGroup? {
    val parsed = runCatching { MuscleGroup.valueOf(muscleName) }.getOrNull()
    if (parsed == null) {
        this?.add(SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized(muscleName, patternName))
    }
    return parsed
}
