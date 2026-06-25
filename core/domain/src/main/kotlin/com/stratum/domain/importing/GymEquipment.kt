package com.stratum.domain.importing

/**
 * Closed equipment vocabulary used transiently during historical import to drive Tier B lift
 * enablement (equipment closure) and the sparse-import gym-type fallback.
 *
 * Hybrid granularity per the import enablement plan:
 * - Free weights are coarse (a "Dumbbell" is a dumbbell — brand does not matter for availability).
 * - Machines/stations are specific enough to give a real availability signal (a user who logged a
 *   lat pulldown has a `LatPulldownMachine`; that lets us enable other lat pulldown variants but
 *   not, say, a leg press).
 *
 * **Not persisted on the `Lift` entity or in Room.** This enum exists for the import enablement
 * algorithm and the bundled `lift-equipment-tags.json` asset only. Post-onboarding the app is
 * lift-first (ARCHITECTURE.md §2.1) and does not reason about equipment.
 *
 * Adding a term = new enum value + a `name`-string entry in the asset/Flash JSON schema. The
 * string form is stable (used in the APK asset and the Firebase callable JSON schema), so renames
 * are breaking — add, don't rename.
 */
enum class GymEquipment {
    // Free weights — coarse
    Barbell,
    Dumbbell,
    EzBar,
    Kettlebell,
    Band,
    Bodyweight,

    // Benches / racks
    FlatBench,
    AdjustableBench,
    PowerRack,

    // Specific machines / stations
    SmithMachine,
    CableStation,
    CableCrossover,
    LatPulldownMachine,
    SeatedCableRowStation,
    TBarRowStation,
    LegPress,
    HackSquat,
    LegExtension,
    LegCurl,
    CalfRaiseMachine,
    AbCrunchMachine,
    PecDeck,
    PreacherCurlMachine,
    HipThrustMachine,
    Ghd,
    LegRaiseCaptainChair,
    ;

    companion object {
        /**
         * Equipment whose presence in a user's confirmed set implies a commercial gym for the
         * sparse-import fallback. Used transiently in `ResolveImportLiftsUseCase`.
         */
        val COMMERCIAL_INDICATORS: Set<GymEquipment> = setOf(
            CableStation,
            CableCrossover,
            LatPulldownMachine,
            SeatedCableRowStation,
            LegPress,
            HackSquat,
            LegExtension,
            LegCurl,
            CalfRaiseMachine,
            AbCrunchMachine,
            PecDeck,
            PreacherCurlMachine,
            HipThrustMachine,
            SmithMachine,
            TBarRowStation,
            Ghd,
            LegRaiseCaptainChair,
        )
    }
}
