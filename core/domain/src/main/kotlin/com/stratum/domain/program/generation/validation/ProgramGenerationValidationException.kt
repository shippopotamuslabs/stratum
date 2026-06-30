package com.stratum.domain.program.generation.validation

class ProgramGenerationValidationException(
    val report: ValidationReport,
) : IllegalStateException(
    report.issues
        .filter { it.severity == ValidationSeverity.Error }
        .joinToString("; ") { it.message },
)
