package com.stratum.domain.program.generation.naming

const val PROGRAM_NAMING_PROMPT_VERSION = 1

data class ProgramNamingIntentPayload(
    val trainingAge: String,
    val sessionLengthMinutes: Int?,
    val trainingDaysPerWeek: Int,
    val cycleLength: Int,
    val specialization: Map<String, String>,
    val specializedMuscles: List<String>,
    val trainingWeekdays: List<String>,
)

data class ProgramNamingLiftPayload(
    val name: String,
    val movementPattern: String,
    val primaryMuscles: List<String>,
    val setCount: Int,
)

data class ProgramNamingWorkoutPayload(
    val position: Int,
    val weekday: String?,
    val workoutLifts: List<ProgramNamingLiftPayload>,
)

data class ProgramNamingPayload(
    val promptVersion: Int,
    val intent: ProgramNamingIntentPayload,
    val program: ProgramNamingProgramPayload,
)

data class ProgramNamingProgramPayload(
    val workouts: List<ProgramNamingWorkoutPayload>,
)

data class ProgramNamingRequest(
    val namingPayload: ProgramNamingPayload,
)

data class ProgramNamingWorkoutName(
    val position: Int,
    val name: String,
)

data class ProgramNamingResult(
    val programName: String?,
    val workoutNames: List<ProgramNamingWorkoutName>,
) {
    val workoutNamesByPosition: Map<Int, String> get() =
        workoutNames.associate { row -> row.position to row.name }
}

interface ProgramNamingPort {
    suspend fun nameProgram(request: ProgramNamingRequest): Result<ProgramNamingResult>
}
