package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.program.navigation.ProgramLibraryKey
import com.stratum.feature.settings.ImportProgramNavigationEvent
import com.stratum.feature.settings.navigation.ImportDataKey
import com.stratum.feature.settings.navigation.ImportProgramKey
import com.stratum.feature.settings.navigation.SettingsKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SettingsImportBackStackTest {

    @Test
    @DisplayName("Single import opens the editor above main with the Settings import stack cleared")
    fun editorReplacesSettingsImportStack() {
        val backStack: MutableList<NavKey> =
            mutableListOf(MainTabKey(), SettingsKey, ImportDataKey, ImportProgramKey)

        backStack.navigateAfterProgramImport(ImportProgramNavigationEvent.OpenProgramEditor("p1"))

        assertEquals(listOf(MainTabKey(), CreateProgramKey(programId = "p1")), backStack)
    }

    @Test
    @DisplayName("Multi import opens the library above main with the Settings import stack cleared")
    fun libraryReplacesSettingsImportStack() {
        val backStack: MutableList<NavKey> =
            mutableListOf(MainTabKey(), SettingsKey, ImportDataKey, ImportProgramKey)

        backStack.navigateAfterProgramImport(ImportProgramNavigationEvent.OpenProgramLibrary)

        assertEquals(listOf(MainTabKey(), ProgramLibraryKey), backStack)
    }

    @Test
    @DisplayName("Library already below the import stack is not pushed twice")
    fun libraryIsNotDuplicated() {
        val backStack: MutableList<NavKey> =
            mutableListOf(MainTabKey(), ProgramLibraryKey, SettingsKey, ImportDataKey, ImportProgramKey)

        backStack.navigateAfterProgramImport(ImportProgramNavigationEvent.OpenProgramLibrary)

        assertEquals(listOf(MainTabKey(), ProgramLibraryKey), backStack)
    }
}
