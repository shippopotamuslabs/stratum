package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.program.navigation.ProgramLibraryKey
import com.stratum.feature.settings.ImportProgramNavigationEvent
import com.stratum.feature.settings.navigation.ImportDataKey
import com.stratum.feature.settings.navigation.ImportProgramKey
import com.stratum.feature.settings.navigation.SettingsKey

/**
 * STR-333: after a successful Settings program import, drops the Settings import entries
 * (Settings, Import Data, Import Program) and opens the imported program's editor, or the
 * Program Library when several programs were imported. Back from the destination returns
 * to the main surface instead of the import screens.
 */
internal fun MutableList<NavKey>.navigateAfterProgramImport(event: ImportProgramNavigationEvent) {
    val destination =
        when (event) {
            is ImportProgramNavigationEvent.OpenProgramEditor ->
                CreateProgramKey(programId = event.programId)
            ImportProgramNavigationEvent.OpenProgramLibrary -> ProgramLibraryKey
        }
    removeAll { it is SettingsKey || it is ImportDataKey || it is ImportProgramKey }
    if (lastOrNull() != destination) add(destination)
}
