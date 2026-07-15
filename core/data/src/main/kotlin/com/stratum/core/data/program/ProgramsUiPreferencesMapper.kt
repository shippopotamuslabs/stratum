package com.stratum.core.data.program

import com.stratum.domain.program.ProgramsUiPreferences

internal fun ProgramsUiPreferencesRecord.toDomain(): ProgramsUiPreferences =
    ProgramsUiPreferences(expandedWorkoutId = expandedWorkoutId)

internal fun ProgramsUiPreferences.toRecord(): ProgramsUiPreferencesRecord =
    ProgramsUiPreferencesRecord(expandedWorkoutId = expandedWorkoutId)
