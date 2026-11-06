package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtUserType

/**
 * STR-237 gate (CODE_STYLE_RULES §1 / LoggingPort section): :core:domain and
 * :core:application hold NO logging capability — no logging port, logger type,
 * telemetry port, or log call. The logging/telemetry ports live in
 * :core:infrastructure; operational failures ride the sealed outcomes'
 * diagnostics and are recorded once at the presentation boundary
 * (DiagnosticDrain).
 *
 * Scoped to :core:domain and :core:application MAIN sources. Flags:
 *  (a) a file whose package has a segment exactly `logging` or `telemetry`
 *      (segment equality on the dot-split package name — a segment like
 *      `loggingstuff` does NOT flag);
 *  (b) type references to `LoggingPort`, `AnalyticsPort`,
 *      `CrashTelemetryPort`, or `TelemetryUploadPort` — import directives
 *      importing one of those names (from any package) and `KtUserType`
 *      references with one of those names;
 *  (c) call expressions whose callee simple name is `runCatchingLogged`,
 *      `onFailureLogged`, `expectedFailure`, `LogErrorUseCase`, or
 *      `LogAnalyticsEventUseCase` (the last two are constructor calls).
 *
 * Known PSI limitation: this rule performs no type resolution. Checks (b)
 * and (c) match simple names as written in the source. Words inside
 * comments/KDoc and string literals are not PSI type or call expressions
 * and are never flagged — the sanctioned KDoc-only mentions of `LoggingPort`
 * in :core:application therefore pass.
 */
class NoLoggingCapabilityInDomainApplication(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "NoLoggingCapabilityInDomainApplication",
            severity = Severity.CodeSmell,
            description =
                "Domain and application must not hold logging capability. " +
                    "Logging/telemetry ports live in :core:infrastructure; operational failures " +
                    "ride the outcome's diagnostics and are recorded once at the boundary.",
            debt = Debt.TWENTY_MINS,
        )

    private fun isEnforced(path: String?): Boolean {
        if (path == null) return false
        return path.contains("/core/domain/src/main/") || path.contains("/core/application/src/main/")
    }

    override fun visitKtFile(file: KtFile) {
        if (isEnforced(file.virtualFilePath)) {
            val packageDirective = file.packageDirective
            val packageName = packageDirective?.packageNameExpression?.text.orEmpty()
            if (packageName.split('.').any { segment -> segment in BANNED_PACKAGE_SEGMENTS }) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(packageDirective ?: file),
                        message =
                            "Package `$packageName` declares a logging/telemetry package in " +
                                "domain/application. Those types live in :core:infrastructure (STR-237).",
                    ),
                )
            }
        }
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        if (!isEnforced(importDirective.containingKtFile.virtualFilePath)) return
        val importPath = importDirective.importPath?.pathStr ?: return
        val simpleName = importPath.substringAfterLast(".")
        if (simpleName in BANNED_TYPE_NAMES) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(importDirective),
                    message =
                        "Import of `$simpleName` brings a logging/telemetry port into " +
                            "domain/application. Record at the boundary instead (STR-237).",
                ),
            )
        }
    }

    override fun visitUserType(userType: KtUserType) {
        super.visitUserType(userType)
        if (!isEnforced(userType.containingKtFile.virtualFilePath)) return
        val referencedName = userType.referenceExpression?.text ?: return
        if (referencedName in BANNED_TYPE_NAMES) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(userType),
                    message =
                        "Reference to `$referencedName` is a logging/telemetry capability in " +
                            "domain/application. Carry the failure on the outcome instead (STR-237).",
                ),
            )
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!isEnforced(expression.containingKtFile.virtualFilePath)) return
        val callee = expression.calleeExpression?.text ?: return
        if (callee in BANNED_CALLEES) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "`$callee` is a logging call in domain/application. " +
                            "Carry the failure on the outcome and drain it at the boundary (STR-237).",
                ),
            )
        }
    }

    companion object {
        private val BANNED_PACKAGE_SEGMENTS =
            setOf("logging", "telemetry")

        private val BANNED_TYPE_NAMES =
            setOf("LoggingPort", "AnalyticsPort", "CrashTelemetryPort", "TelemetryUploadPort")

        private val BANNED_CALLEES =
            setOf(
                "runCatchingLogged",
                "onFailureLogged",
                "expectedFailure",
                "LogErrorUseCase",
                "LogAnalyticsEventUseCase",
            )
    }
}
