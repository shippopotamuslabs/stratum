package com.stratum.domain.importing

sealed interface ImportFileValidationResult {
    data object Valid : ImportFileValidationResult

    sealed interface Invalid : ImportFileValidationResult {
        data object NoFiles : Invalid

        data class TooManyFiles(val max: Int) : Invalid

        data class FileTooLarge(val fileName: String, val maxMb: Int) : Invalid
    }
}
