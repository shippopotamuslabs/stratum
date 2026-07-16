package com.stratum.domain.export

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.export.ExportTestFixtures.programActive
import com.stratum.domain.export.ExportTestFixtures.programImportHistory
import com.stratum.domain.export.ExportTestFixtures.programLib
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PreviewExportProgramsUseCaseTest {

    @Test
    @DisplayName("IE-S2-001 Preview lists user programs and excludes import-history")
    fun ieS2_001_previewExcludesImportHistory() = runTest {
        val repo = FakeProgramRepository()
        repo.saveProgram(programActive)
        repo.saveToLibrary(programLib)
        repo.saveToLibrary(programImportHistory)
        val options = PreviewExportProgramsUseCase(repo)()
        assertEquals(2, options.size)
        assertEquals(setOf("prog-active", "prog-lib"), options.map { it.id.value }.toSet())
        assertFalse(options.any { it.id.value == "import-history" })
    }
}
