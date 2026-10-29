package com.stratum.application.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.HistoricalImportLimits
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportFileValidationResult

class ValidateImportFilesUseCaseTest {
    private val subject = ValidateImportFilesUseCase()

    @Test
    @DisplayName("Given no files when validated then NoFiles")
    fun noFiles() {
        val result = subject(emptyList())

        assertTrue(result is ImportFileValidationResult.Invalid.NoFiles)
    }

    @Test
    @DisplayName("Given more than max files when validated then TooManyFiles")
    fun tooManyFiles() {
        val files =
            (1..HistoricalImportLimits.MAX_FILES + 1).map { index ->
                ImportFileInput(bytes = byteArrayOf(1), fileName = "file$index.csv")
            }

        val result = subject(files)

        assertEquals(
            ImportFileValidationResult.Invalid.TooManyFiles(HistoricalImportLimits.MAX_FILES),
            result,
        )
    }

    @Test
    @DisplayName("Given oversized file when validated then FileTooLarge")
    fun fileTooLarge() {
        val oversizedBytes = ByteArray(HistoricalImportLimits.MAX_FILE_BYTES + 1)
        val files =
            listOf(
                ImportFileInput(bytes = byteArrayOf(1), fileName = "small.csv"),
                ImportFileInput(bytes = oversizedBytes, fileName = "huge.csv"),
            )

        val result = subject(files)

        assertEquals(
            ImportFileValidationResult.Invalid.FileTooLarge(
                fileName = "huge.csv",
                maxMb = HistoricalImportLimits.MAX_FILE_SIZE_MB,
            ),
            result,
        )
    }

    @Test
    @DisplayName("Given valid files when validated then Valid")
    fun validFiles() {
        val files =
            listOf(
                ImportFileInput(bytes = byteArrayOf(1, 2, 3), fileName = "export.csv"),
            )

        val result = subject(files)

        assertEquals(ImportFileValidationResult.Valid, result)
    }
}
