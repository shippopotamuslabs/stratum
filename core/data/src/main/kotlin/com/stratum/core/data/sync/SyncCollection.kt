package com.stratum.core.data.sync

/**
 * Canonical Firestore collection path segments for user-owned syncable entities.
 * Security rules and hydrate order must stay aligned with [firestorePath] values.
 *
 * Incomplete surface (intentionally not synced — multi-device will recompute or diverge):
 * response-modeling tables, import job metadata, insights, theme prefs, overlays.
 */
enum class SyncCollection(
    val firestorePath: String,
) {
    UserProfile("user_profile"),
    OnboardingProgress("onboarding_progress"),
    GymPreference("gym_preference"),
    SpecializationPreference("specialization_preference"),
    /** Parent of lift.equipmentId and equipment-owned increment tiers. */
    Equipment("equipment"),
    Lifts("lifts"),
    IncrementTiers("increment_tiers"),
    Programs("programs"),
    Workouts("workouts"),
    WorkoutLifts("workout_lifts"),
    LiftSets("lift_sets"),
    TrainingSessions("training_sessions"),
    LoggedSets("logged_sets"),
    ProgramChangeEvents("program_change_events"),
    ;

    /**
     * Collections that must hydrate successfully before this one (Room FKs / logical parents).
     * A failed parent pull skips this collection so child cursors stay put for retry.
     */
    val hydrateParents: List<SyncCollection>
        get() =
            when (this) {
                Lifts -> listOf(Equipment)
                IncrementTiers -> listOf(Equipment, Lifts)
                Workouts -> listOf(Programs)
                WorkoutLifts -> listOf(Workouts, Lifts)
                LiftSets -> listOf(WorkoutLifts)
                LoggedSets -> listOf(TrainingSessions)
                ProgramChangeEvents -> listOf(Programs)
                UserProfile,
                OnboardingProgress,
                GymPreference,
                SpecializationPreference,
                Equipment,
                Programs,
                TrainingSessions,
                -> emptyList()
            }

    companion object {
        /** Parents before children for Room FK-safe hydrate. */
        val HYDRATE_ORDER: List<SyncCollection> = entries.toList()

        val ALLOWLIST: Set<String> = entries.map { it.firestorePath }.toSet()

        fun fromFirestorePath(path: String): SyncCollection? =
            entries.firstOrNull { it.firestorePath == path }
    }
}
