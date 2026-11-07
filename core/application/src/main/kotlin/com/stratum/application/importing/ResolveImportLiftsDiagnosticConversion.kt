package com.stratum.application.importing

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.importing.ResolveImportLiftsDiagnostic

/**
 * Canonical boundary rendering of a lift-name embedding batch failure
 * (STR-240 chunk 8 / 8b): given the consuming flow's [tag], produces the
 * [OperationalDiagnostic] that flow drains — the message construction and
 * cause handling live here exactly once, so the historical-import flow
 * (via `ImportLiftResolutionLogger`) and the template-import flow render
 * byte-identical records for the same failure, differing only in tag.
 */
fun ResolveImportLiftsDiagnostic.LiftNameEmbeddingFailed.toOperationalDiagnostic(
    tag: String,
): OperationalDiagnostic =
    OperationalDiagnostic(
        tag = tag,
        message = "Import lift name embedding failed — gray-zone matches may be " +
            "unresolved. Error: ${cause.message ?: "unknown"}",
        cause = cause,
    )
