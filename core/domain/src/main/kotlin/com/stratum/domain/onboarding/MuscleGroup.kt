package com.stratum.domain.onboarding

/**
 * The muscles Stratum tracks volume against — the unit a user *specializes* (OS-3) and the
 * unit each lift contributes volume to (OS-4 / OS-6). Named `MuscleGroup` rather than the
 * LiftLab `VolumeType` so call sites read in fitness-app terms ("which muscle groups do you
 * want to specialize?"). Resolves `docs/onboarding-vertical-slices.md` §10.
 *
 * The values mirror LiftLab's `VolumeType` for OS-6 generation parity; some entries are muscle
 * *heads* (e.g. [LateralDeltoid]) rather than broad groups, but "muscle group" is the accepted
 * umbrella term in this domain.
 *
 * OS-3 treats this as a plain identifier. OS-4 adds per-muscle volume contributions and OS-6
 * will add MEV/MRV landmark defaults; a bitmask is deliberately **not** added here.
 *
 * [Abductor] and [Adductor] (OS-4b) are volume-only contributors: lifts can target them for
 * volume tracking and they appear in the Gym & Lifts muscle summary, but they are **not** on
 * the body-diagram picker (`BodyPathData` has no SVG region for them) and are not offered for
 * specialization in the body-diagram picker (`BodyPathData` has no SVG region for them). Practically nobody wants to specialize them, so
 * excluding them from the picker is intentional, not a gap. Appended at the end so the
 * bitmask↔ordinal encoding in `LiftMapper` stays stable for existing values.
 */
enum class MuscleGroup {
    Chest,
    Back,
    Quad,
    Hamstring,
    Glute,
    PosteriorDeltoid,
    LateralDeltoid,
    AnteriorDeltoid,
    Tricep,
    Bicep,
    Trap,
    Calf,
    Forearm,
    Ab,
    LowerBack,
    Abductor,
    Adductor,
    ;

    /** Human-readable label — single source of truth for UI and lift summaries. */
    val displayLabel: String
        get() = when (this) {
            Chest -> "Chest"
            Back -> "Back"
            Quad -> "Quads"
            Hamstring -> "Hamstrings"
            Glute -> "Glutes"
            PosteriorDeltoid -> "Rear Delts"
            LateralDeltoid -> "Side Delts"
            AnteriorDeltoid -> "Front Delts"
            Tricep -> "Triceps"
            Bicep -> "Biceps"
            Trap -> "Traps"
            Calf -> "PlantarFlexion"
            Forearm -> "Forearms"
            Ab -> "Abs"
            LowerBack -> "Lower Back"
            Abductor -> "Abductors"
            Adductor -> "Adductors"
        }
}
