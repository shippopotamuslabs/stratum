package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

sealed class StartTrainingSessionRequest {
    data class ProgramWorkout(val workoutId: WorkoutId) : StartTrainingSessionRequest()

    data object AdHocEmpty : StartTrainingSessionRequest()
}

data class LogSetCommand(
    val sessionId: TrainingSessionId,
    val liftId: LiftId,
    val workoutLiftId: WorkoutLiftId?,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
)

class StartTrainingSessionUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
) {
    suspend operator fun invoke(request: StartTrainingSessionRequest): Result<TrainingSession> = runCatching {
        val activeSession = trainingLogRepository.getActiveSession()
        if (activeSession != null) {
            throw ActiveTrainingSessionExistsException(activeSession.id)
        }

        val program =
            programRepository.getProgram()
                ?: error("No active program found to start training session")
        val now = Instant.now()

        val session =
            when (request) {
                is StartTrainingSessionRequest.ProgramWorkout -> {
                    val workout =
                        program.workouts.firstOrNull { it.id == request.workoutId }
                            ?: throw IllegalArgumentException("Workout ${request.workoutId.value} not found in program")
                    TrainingSession(
                        id = TrainingSessionId(UUID.randomUUID().toString()),
                        programId = program.id,
                        workoutId = request.workoutId,
                        status = TrainingSessionStatus.IN_PROGRESS,
                        kind = TrainingSessionKind.PROGRAM_WORKOUT,
                        origin = TrainingSessionOrigin.LIVE,
                        cycleNumberSnapshot = program.currentCycleNumber,
                        weekdaySnapshot = workout.weekday,
                        startedAt = now,
                        endedAt = null,
                        sourceApp = null,
                    )
                }
                StartTrainingSessionRequest.AdHocEmpty ->
                    TrainingSession(
                        id = TrainingSessionId(UUID.randomUUID().toString()),
                        programId = program.id,
                        workoutId = null,
                        status = TrainingSessionStatus.IN_PROGRESS,
                        kind = TrainingSessionKind.AD_HOC,
                        origin = TrainingSessionOrigin.LIVE,
                        cycleNumberSnapshot = program.currentCycleNumber,
                        weekdaySnapshot = null,
                        startedAt = now,
                        endedAt = null,
                        sourceApp = null,
                    )
            }

        trainingLogRepository.insertSession(session)
        session
    }
}

class FinalizeTrainingSessionUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<TrainingSession> = runCatching {
        val session =
            trainingLogRepository.getSession(sessionId)
                ?: throw IllegalArgumentException("Training session ${sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(sessionId, session.status)
        }

        val completed =
            session.copy(
                status = TrainingSessionStatus.COMPLETED,
                endedAt = Instant.now(),
            )
        trainingLogRepository.updateSession(completed)

        if (completed.kind == TrainingSessionKind.PROGRAM_WORKOUT) {
            advanceCycleAfterProgramWorkoutComplete(
                trainingLogRepository = trainingLogRepository,
                programRepository = programRepository,
                programChangeEventRepository = programChangeEventRepository,
            )
        }

        completed
    }
}

class AbandonTrainingSessionUseCase(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): Result<Unit> = runCatching {
        trainingLogRepository.deleteSession(sessionId)
    }
}

class RecordWorkoutSkipUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
) {
    suspend operator fun invoke(workoutId: WorkoutId): Result<TrainingSession> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to record workout skip")
        val workout =
            program.workouts.firstOrNull { it.id == workoutId }
                ?: throw IllegalArgumentException("Workout ${workoutId.value} not found in program")
        val now = Instant.now()

        val session =
            TrainingSession(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = program.id,
                workoutId = workoutId,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = program.currentCycleNumber,
                weekdaySnapshot = workout.weekday,
                startedAt = now,
                endedAt = now,
                sourceApp = null,
            )

        trainingLogRepository.insertSession(session)
        advanceCycleAfterProgramWorkoutComplete(
            trainingLogRepository = trainingLogRepository,
            programRepository = programRepository,
            programChangeEventRepository = programChangeEventRepository,
        )
        session
    }
}

class RecordRestDaySkipUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository? = null,
) {
    suspend operator fun invoke(weekday: Weekday): Result<TrainingSession> = runCatching {
        val program =
            programRepository.getProgram()
                ?: error("No active program found to record rest day skip")
        val now = Instant.now()

        val session =
            TrainingSession(
                id = TrainingSessionId(UUID.randomUUID().toString()),
                programId = program.id,
                workoutId = null,
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.REST_DAY,
                origin = TrainingSessionOrigin.LIVE,
                cycleNumberSnapshot = program.currentCycleNumber,
                weekdaySnapshot = weekday,
                startedAt = now,
                endedAt = now,
                sourceApp = null,
            )

        trainingLogRepository.insertSession(session)
        session
    }
}

class LogSetUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: com.stratum.domain.lift.LiftRepository,
    private val programRepository: ProgramRepository,
    private val loggedLiftContextFactory: LoggedLiftContextFactory,
) {
    suspend operator fun invoke(command: LogSetCommand): Result<LoggedSet> = runCatching {
        val session =
            trainingLogRepository.getSession(command.sessionId)
                ?: throw IllegalArgumentException("Training session ${command.sessionId.value} not found")
        if (session.status != TrainingSessionStatus.IN_PROGRESS) {
            throw TrainingSessionNotInProgressException(command.sessionId, session.status)
        }

        val lift =
            liftRepository.observeLifts().first().firstOrNull { it.id == command.liftId }
                ?: throw IllegalArgumentException("Lift ${command.liftId.value} not found")

        val liftContext =
            if (command.workoutLiftId != null) {
                val program =
                    programRepository.getProgram()
                        ?: error("No active program found to resolve workout lift")
                val workoutLift =
                    program.workouts
                        .flatMap { it.workoutLifts }
                        .firstOrNull { it.id == command.workoutLiftId }
                        ?: throw IllegalArgumentException("Workout lift ${command.workoutLiftId.value} not found")
                val templateSet = workoutLift.sets.firstOrNull { it.position == command.setIndex }
                loggedLiftContextFactory.fromLiveTemplate(lift, workoutLift, templateSet)
            } else {
                loggedLiftContextFactory.fromLiftCatalog(lift, lift.name)
            }

        val loggedSet =
            LoggedSet(
                id = LoggedSetId(UUID.randomUUID().toString()),
                sessionId = command.sessionId,
                exerciseNameRaw = lift.name,
                liftId = command.liftId,
                setIndex = command.setIndex,
                weight = command.weight,
                reps = command.reps,
                rir = command.rir,
                completedAt = command.completedAt,
                liftContext = liftContext,
            )

        trainingLogRepository.insertSet(loggedSet)
        loggedSet
    }
}

class ObserveCycleProgressUseCase(
    private val programRepository: ProgramRepository,
    private val trainingLogRepository: TrainingLogRepository,
) {
    operator fun invoke(): Flow<CycleProgress> =
        combine(
            programRepository.observeProgram(),
            trainingLogRepository.observeActiveSession(),
        ) { program, activeSession ->
            program to activeSession
        }.flatMapConcat { (program, activeSession) ->
            flow {
                emit(resolveCycleProgress(program, activeSession))
            }
        }

    private suspend fun resolveCycleProgress(
        program: com.stratum.domain.program.Program?,
        activeSession: TrainingSession?,
    ): CycleProgress {
        if (program == null) {
            return CycleProgress(
                program =
                    com.stratum.domain.program.Program(
                        id = ProgramId(""),
                        name = "",
                        createdAt = Instant.EPOCH,
                        cycleLength = 0,
                        workouts = emptyList(),
                    ),
                activeSession = activeSession,
                completedWorkoutIdsThisCycle = emptySet(),
                nextWorkout = null,
                isCycleComplete = false,
            )
        }
        val sessions = trainingLogRepository.getAllSessions()
        val completedWorkoutIds =
            program.workouts
                .map { it.id }
                .filter { workoutId ->
                    CycleProgressResolver.isWorkoutCompletedThisCycle(program, sessions, workoutId)
                }
                .toSet()
        return CycleProgress(
            program = program,
            activeSession = activeSession,
            completedWorkoutIdsThisCycle = completedWorkoutIds,
            nextWorkout = CycleProgressResolver.resolveNextWorkout(program, sessions),
            isCycleComplete = CycleProgressResolver.isCycleComplete(program, sessions),
        )
    }
}

private suspend fun advanceCycleAfterProgramWorkoutComplete(
    trainingLogRepository: TrainingLogRepository,
    programRepository: ProgramRepository,
    programChangeEventRepository: ProgramChangeEventRepository?,
) {
    val program = programRepository.getProgram() ?: return
    val sessions = trainingLogRepository.getAllSessions()
    val cyclePosition = CycleProgressResolver.reconcileCyclePosition(program, sessions)
    val cycleComplete = CycleProgressResolver.isCycleComplete(program, sessions)
    val completedCycleNumber = program.currentCycleNumber
    val nextCycleNumber = if (cycleComplete) completedCycleNumber + 1 else completedCycleNumber
    val nextCyclePosition = if (cycleComplete) 0 else cyclePosition

    programRepository.updateCyclePointer(
        programId = program.id,
        currentCycleNumber = nextCycleNumber,
        cyclePosition = nextCyclePosition,
    )

    if (cycleComplete && programChangeEventRepository != null) {
        programChangeEventRepository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = program.id,
                eventType = ProgramChangeEventType.CYCLE_COMPLETED,
                occurredAt = Instant.now(),
                payload = ProgramChangeEventPayload.CycleCompleted(completedCycleNumber),
            ),
        )
    }
}
