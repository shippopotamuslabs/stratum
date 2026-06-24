package com.stratum.domain.onboarding

/**
 * The muscles Stratum tracks volume against — the unit a user *specializes* (OS-3) and the
 * unit each lift contributes volume to (OS-4 / OS-6). Named `MuscleGroup` rather than the
 * LiftLab `VolumeType` so call sites read in fitness-app terms ("which muscle groups do you
 * want to specialize?"). Resolves `docs/onboarding-vertical-slices.md` §10.
 *
 * The 15 values mirror LiftLab's `VolumeType` one-for-one for OS-6 generation parity; some
 * entries are muscle *heads* (e.g. [LateralDeltoid]) rather than broad groups, but
 * "muscle group" is the accepted umbrella term in this domain.
 *
 * OS-3 treats this as a plain identifier. OS-4 will add per-muscle volume contributions and
 * OS-6 will add MEV/MRV landmark defaults; a bitmask is deliberately **not** added here.
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
    ;
}
