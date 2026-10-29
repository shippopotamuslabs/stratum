package com.stratum.application.workout

import com.stratum.application.importing.ExtractTrainingInsightsUseCase
import com.stratum.application.importing.InsightsAnalysisScope
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.application.importing.LiftTrainingInsightsRepository
import com.stratum.application.lift.LiftRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.program.Program
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.ProgressionRepRangePolicy
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedSet
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.countsTowardMainBlockFatigue
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.isEligibleCompletedLiveSession
import com.stratum.domain.workout.fatigue.LAST_SET_REP_FLOOR
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import com.stratum.domain.workout.RepRangeShiftInput
import com.stratum.domain.workout.RepRangeShiftProposal
import com.stratum.domain.workout.evaluateRepRangeShift
import com.stratum.domain.workout.repRangeShiftProposalFingerprint
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.workout.MAX_SESSION_REP_RANGE_SHIFT_PROPOSALS

/**
 * Analyzes a completed **live program workout** for template lifts whose measured fatigue
 * decay (from training insights) implies the last set would fall under the last-set rep
 * floor at the current band min.
 *
 * Signal is **historical insights** (refreshed at the start of analysis so the just-finished
 * session is included). A session-local gate still requires the last **main-block** logged
 * set (not myo minis / drops) for that template lift to have been under [LAST_SET_REP_FLOOR]
 * reps so strong sessions stay quiet.
 *
 * Only lifts with a stamped [com.stratum.domain.training.LoggedLiftContext.workoutLiftId]
 * are considered (no ambiguous catalog-id first-match). Failures log and return empty.
 */
class AnalyzeSessionRepRangeShiftProposalsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val liftTrainingInsightsRepository: LiftTrainingInsightsRepository,
    private val liftRepository: LiftRepository,
    private val preferences: RepRangeShiftProposalPreferences,
    private val extractTrainingInsights: ExtractTrainingInsightsUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): List<RepRangeShiftProposal> =
        runCatchingLogged(
            loggingPort = loggingPort,
            tag = TAG,
            message = "Failed to analyze session rep-range shift proposals for ${sessionId.value}",
        ) {
            analyze(sessionId)
        }.getOrElse { emptyList() }

    private suspend fun analyze(sessionId: TrainingSessionId): List<RepRangeShiftProposal> {
        if (!preferences.isEnabled()) {
            return emptyList()
        }
        val session = trainingLogRepository.getSession(sessionId)
        val program = programRepository.getProgram()
        if (!isAnalyzable(session, program)) {
            return emptyList()
        }
        checkNotNull(session)
        checkNotNull(program)

        // Await rollup so this session contributes to slopes (finalize only fires-and-forgets).
        extractTrainingInsights(scope = InsightsAnalysisScope.All)

        val sets = trainingLogRepository.getSetsForSession(sessionId)
        val byWorkoutLift = setsWithWorkoutLiftId(sets)
        if (byWorkoutLift.isEmpty()) {
            return emptyList()
        }

        val workoutLiftsById = indexWorkoutLifts(program)
        val insightsByLift =
            liftTrainingInsightsRepository.getAll().associateBy { it.liftId }
        val liftNamesById =
            liftRepository.observeLifts().first().associate { it.id to it.name }

        val built = ArrayList<RepRangeShiftProposal>()
        for ((workoutLiftId, liftSets) in byWorkoutLift) {
            val proposal =
                buildProposal(
                    workoutLiftId = workoutLiftId,
                    sessionSets = liftSets,
                    workoutLiftsById = workoutLiftsById,
                    insightsByLift = insightsByLift,
                    liftNamesById = liftNamesById,
                ) ?: continue
            if (!preferences.isDismissed(proposal.id)) {
                built += proposal
            }
        }

        return built
            .sortedByDescending { it.lastSetShortfall }
            .take(MAX_SESSION_REP_RANGE_SHIFT_PROPOSALS)
    }

    private fun isAnalyzable(session: TrainingSession?, program: Program?): Boolean {
        if (session == null || program == null) return false
        val eligibleLive =
            isEligibleCompletedLiveSession(session.status, session.origin) &&
                session.kind == TrainingSessionKind.PROGRAM_WORKOUT &&
                session.workoutId != null
        if (!eligibleLive) return false
        // Soft-unlinked sessions must not mutate the active template.
        val sessionProgramId = session.programId
        return sessionProgramId != null && sessionProgramId == program.id
    }

    private fun indexWorkoutLifts(
        program: Program,
    ): Map<WorkoutLiftId, Pair<WorkoutLift, WorkoutId>> =
        program.workouts
            .flatMap { workout -> workout.workoutLifts.map { it to workout.id } }
            .associate { (wl, workoutId) -> wl.id to (wl to workoutId) }

    private fun setsWithWorkoutLiftId(
        sets: List<LoggedSet>,
    ): Map<WorkoutLiftId, List<LoggedSet>> =
        sets
            .mapNotNull { set ->
                val wlId = set.liftContext.workoutLiftId ?: return@mapNotNull null
                wlId to set
            }
            .groupBy({ it.first }, { it.second })

    private fun buildProposal(
        workoutLiftId: WorkoutLiftId,
        sessionSets: List<LoggedSet>,
        workoutLiftsById: Map<WorkoutLiftId, Pair<WorkoutLift, WorkoutId>>,
        insightsByLift: Map<LiftId, LiftTrainingInsights>,
        liftNamesById: Map<LiftId, String>,
    ): RepRangeShiftProposal? {
        val resolved = workoutLiftsById[workoutLiftId] ?: return null
        val (workoutLift, workoutId) = resolved
        // Session evidence gate: last logged set for this template lift fell under the floor.
        if (!sessionLastSetUnderFloor(sessionSets)) return null

        val measuredSlope = insightsByLift[workoutLift.liftId]?.fatigueDecaySlope
        val band = currentBand(workoutLift)
        val setCount = workoutLift.sets.size
        val evaluation =
            when {
                measuredSlope == null || band.first <= 0 -> null
                else ->
                    evaluateRepRangeShift(
                        RepRangeShiftInput(
                            fatigueSlope = measuredSlope,
                            setCount = setCount,
                            currentRepMin = band.first,
                            currentRepMax = band.second,
                        ),
                    )
            } ?: return null

        val liftName =
            liftNamesById[workoutLift.liftId]
                ?: sessionSets.firstOrNull()?.liftContext?.liftName?.takeIf { it.isNotBlank() }
        if (liftName == null || measuredSlope == null) return null

        return RepRangeShiftProposal(
            id =
                repRangeShiftProposalFingerprint(
                    workoutLiftId = workoutLift.id,
                    currentRepMin = band.first,
                    currentRepMax = band.second,
                    proposedRepMin = evaluation.proposedRepMin,
                    proposedRepMax = evaluation.proposedRepMax,
                ),
            workoutLiftId = workoutLift.id,
            workoutId = workoutId,
            liftId = workoutLift.liftId,
            liftName = liftName,
            currentRepMin = band.first,
            currentRepMax = band.second,
            proposedRepMin = evaluation.proposedRepMin,
            proposedRepMax = evaluation.proposedRepMax,
            setCount = setCount,
            fatigueSlope = measuredSlope,
            lastSetShortfall = evaluation.lastSetShortfall,
        )
    }

    /**
     * Last main-block set (by [LoggedSet.setIndex]) under the floor.
     * Myo minis and drops are ignored — their low reps are protocol, not band failure.
     */
    private fun sessionLastSetUnderFloor(sessionSets: List<LoggedSet>): Boolean {
        val lastMain =
            sessionSets
                .filter { it.countsTowardMainBlockFatigue() }
                .maxByOrNull { it.setIndex }
                ?: return false
        return lastMain.reps < LAST_SET_REP_FLOOR
    }

    private fun currentBand(workoutLift: WorkoutLift): Pair<Int, Int> {
        val specs =
            workoutLift.sets
                .sortedBy { it.position }
                .map { set ->
                    ProgressionRepRangePolicy.WorkingSetRepSpec(
                        repMin = set.repMin,
                        repMax = set.repMax,
                        isTopSet = set.isTopSet,
                    )
                }
        return ProgressionRepRangePolicy.resolveSharedBand(specs)
    }

    private companion object {
        const val TAG = "AnalyzeSessionRepRangeShift"
    }
}
