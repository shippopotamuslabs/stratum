package com.stratum.domain.importing

import javax.inject.Inject

class ValidateImportFilesUseCase @Inject constructor() {
    operator fun invoke(files: List<ImportFileInput>): ImportFileValidationResult {
        if (files.isEmpty()) {
            return ImportFileValidationResult.Invalid.NoFiles
        }
        if (files.size > HistoricalImportLimits.MAX_FILES) {
            return ImportFileValidationResult.Invalid.TooManyFiles(HistoricalImportLimits.MAX_FILES)
        }
        val oversized = files.firstOrNull { it.bytes.size > HistoricalImportLimits.MAX_FILE_BYTES }
        if (oversized != null) {
            return ImportFileValidationResult.Invalid.FileTooLarge(
                fileName = oversized.fileName,
                maxMb = HistoricalImportLimits.MAX_FILE_SIZE_MB,
            )
        }
        return ImportFileValidationResult.Valid
    }
}
