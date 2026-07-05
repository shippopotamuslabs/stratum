package com.stratum.domain.importing

import com.stratum.domain.importing.identity.ImportJobId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant

enum class ImportJobStatus {
    Pending,
    AgentRunning,
    Converting,
    Normalizing,
    ExtractingInsights,
    Completed,
    Failed,
}

data class ImportJob(
    val id: ImportJobId,
    val status: ImportJobStatus,
    val sampleRef: String?,
    val fullRef: String?,
    val scriptRef: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class ProgressTrend {
    Improving,
    Stagnant,
    Regressing,
}

data class LiftTrainingInsights(
    val liftId: LiftId,
    val estimatedOneRm: Weight?,
    val fatigueDecaySlope: Double?,
    val fatigueDecayIntercept: Double?,
    val sessionCount: Int,
    val progressTrend: ProgressTrend?,
    val lastPerformedAt: Instant?,
)

data class SupersetAffinity(
    val liftA: LiftId,
    val liftB: LiftId,
    val medianRestSeconds: Double,
    val coOccurrenceCount: Int,
    val confidence: Double,
)

data class MuscleVolumeBaseline(
    val muscle: MuscleGroup,
    val meanWeeklyHardSets: Double,
    val p90WeeklyHardSets: Double,
    val trendSlope: Double?,
)

data class ImportAnalysisMetadata(
    val windowStart: Instant,
    val windowEnd: Instant,
    /**
     * Tier A proven lift ids — catalog lifts matched from the user's logged history plus custom
     * lifts created from it. Persisted so program generation (OS-6) can treat these as
     * high-confidence primary movements vs. Tier B equipment-closure lifts (the substitution pool).
     * Equipment/gym-type are NOT persisted — they are transient import-enablement signals.
     */
    val provenLiftIds: List<LiftId> = emptyList(),
    val unmatchedExerciseNames: List<String>,
    val totalSessions: Int,
    val totalSets: Int,
    val extractedAt: Instant = Instant.EPOCH,
    val pipelineVersion: String? = null,
)

data class ExtractTrainingInsightsSummary(
    val liftsWithInsights: Int,
    val supersetPairs: Int,
    val musclesWithBaselines: Int,
    /** Populated after historical import lift resolution; null for insight-only runs. */
    val liftResolution: ResolveImportLiftsSummary? = null,
)

/** Diagnostics for import-time BM25 catalog matching and lift enablement. */
data class ResolveImportLiftsSummary(
    val inputSetCount: Int,
    /** Distinct raw exercise names in the import (before normalization). */
    val distinctInputExerciseNames: Int,
    /** Distinct exercise names after stripping superset suffixes (e.g. `∈ SS1`). */
    val distinctInputExerciseNamesNormalized: Int,
    val seedCatalogLiftCount: Int,
    val preexistingCustomLiftCount: Int,
    /** Set count that matched a seed catalog lift on the first pass (not distinct lifts). */
    val firstPassCatalogMatches: Int,
    val distinctCatalogLiftsEnabledInFirstPass: Int,
    /** Distinct normalized exercise names that matched a seed catalog lift on the first pass. */
    val distinctExerciseNamesMatchedCatalogInFirstPass: Int,
    val distinctUnmatchedExerciseNames: Int,
    val customLiftsCreated: Int,
    val secondPassAdditionalMatches: Int,
    val finalEnabledSeedLiftCount: Int,
    val finalEnabledCustomLiftCount: Int,
    val finalEnabledTotal: Int,
    val unresolvedSetCount: Int,
    val unmatchedExerciseSamples: List<UnmatchedExerciseSample>,
    val nearMissSamples: List<NearMissExerciseSample>,
    /** Tier A proven lift ids (catalog matches + customs) — persisted for program-gen priority. */
    val provenLiftIds: Set<LiftId> = emptySet(),
    /** Equipment inferred from the user's distinct import names (Flash pass), transient. */
    val confirmedEquipment: Set<GymEquipment> = emptySet(),
    /** Tier B lifts enabled by equipment closure (not already Tier A). */
    val tierBLiftsEnabled: Int = 0,
    /** Gym type derived from confirmed equipment for the sparse-import fallback; null if unused. */
    val derivedGymFallback: GymPreset? = null,
    /** Callable batches sent to inferCustomLifts; zero when no unmatched customs. */
    val customLiftInferenceBatchesAttempted: Int = 0,
    /** Callable batches that failed (timeout, auth, parse, etc.). */
    val customLiftInferenceBatchesFailed: Int = 0,
    /** Custom lifts created from successful LLM inference (remainder used stub guesser). */
    val customLiftLlmInferredCount: Int = 0,
    /** Last inferCustomLifts error message when any batch failed. */
    val customLiftInferenceLastError: String? = null,
)

data class UnmatchedExerciseSample(
    val exerciseNameRaw: String,
    val loggedSetCount: Int,
    val bestCatalogName: String?,
    val bestScore: Double,
)

/** BM25 scores below match threshold but close enough to inspect tuning. */
data class NearMissExerciseSample(
    val exerciseNameRaw: String,
    val bestCatalogName: String,
    val bestScore: Double,
    val loggedSetCount: Int,
)

data class ResolveImportLiftsResult(
    val resolvedSets: List<LoggedSet>,
    val summary: ResolveImportLiftsSummary,
    /**
     * Tier A proven lift ids — catalog lifts matched from the user's logged history plus custom
     * lifts created from it. Persisted into [ImportAnalysisMetadata.provenLiftIds] so program
     * generation (OS-6) can treat these as high-confidence primary movements vs. Tier B
     * equipment-closure lifts (the substitution pool). Order is not meaningful.
     */
    val provenLiftIds: Set<LiftId> = emptySet(),
)
