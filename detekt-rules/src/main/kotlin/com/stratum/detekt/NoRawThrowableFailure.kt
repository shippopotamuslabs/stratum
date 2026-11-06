package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * STR-243 gate (CODE-STYLE-GOSPEL §11 / ARCHITECTURE-GOSPEL §8): no domain or
 * application use-case / port signature may return a failure whose payload is a
 * raw Throwable. Producers return per-area sealed outcome types instead.
 *
 * Scoped to :core:domain and :core:application MAIN sources. Flags:
 *  (a) `Result.failure(` call expressions,
 *  (b) calls to the funnel helpers that manufacture raw-Throwable failures —
 *      `runCatchingLogged` / `onFailureLogged` / `expectedFailure`
 *      (the former LoggedResult helpers, deleted by STR-237) and
 *      `runCatchingCancellable`
 *      (domain/coroutines/RunCatchingCancellable.kt) — at call sites outside
 *      their definition files (the definition files are legacyFiles entries),
 *  (c) raw-payload-typed properties / constructor parameters declared on a
 *      subtype of a sealed type declared in the same file. Payload types are
 *      `Throwable` and `Exception`, qualified (`kotlin.Throwable` /
 *      `kotlin.Exception`) or not, nullable or not, and `List<Throwable>` /
 *      `List<Exception>` (likewise qualified / nullable elements).
 *
 * Section-10 diagnostics exemption: subtypes of a sealed type whose name is on
 * the EXACT-NAME `diagnosticExemptTypes` list in config/detekt/detekt.yml are
 * the partial-success diagnostics channel (STR-254..259), not failure
 * payloads. They legitimately carry `cause: Throwable` for the boundary log
 * drain and are exempt from check (c) by that exact list — a sealed type that
 * merely has "Diagnostic" in its name is NOT exempt.
 *
 * Known PSI limitation: this rule performs no type resolution. Check (c) only
 * sees a subtype when its sealed supertype is declared in the SAME file;
 * sealed subtypes declared in a different file than their supertype are not
 * checked.
 *
 * Ratchet: files that legitimately construct or return raw-Throwable failures
 * are listed in the rule's `legacyFiles` config in config/detekt/detekt.yml —
 * the sanctioned funnel definitions plus the named application-internal
 * producers whose Result never crosses a port/presentation boundary.
 */
class NoRawThrowableFailure(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "NoRawThrowableFailure",
            severity = Severity.CodeSmell,
            description =
                "Domain/application failures must be sealed outcome branches, not raw Throwables. " +
                    "Return the area's sealed outcome type instead of Result.failure / the former LoggedResult funnel (deleted by STR-237).",
            debt = Debt.TWENTY_MINS,
        )

    private val legacyFiles: List<String> =
        valueOrDefault("legacyFiles", emptyList())

    private val diagnosticExemptTypes: List<String> =
        valueOrDefault("diagnosticExemptTypes", emptyList())

    private fun isEnforced(path: String?): Boolean {
        if (path == null) return false
        val inScope =
            path.contains("/core/domain/src/main/") || path.contains("/core/application/src/main/")
        if (!inScope) return false
        return legacyFiles.none { entry -> path.endsWith(entry) || path.contains(entry) }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val path = expression.containingKtFile.virtualFilePath
        if (!isEnforced(path)) return
        val selector = expression.selectorExpression as? KtCallExpression ?: return
        if (expression.receiverExpression.text == "Result" && selector.calleeExpression?.text == "failure") {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "Result.failure carries a raw Throwable across the boundary. " +
                            "Return a sealed outcome branch instead (STR-243).",
                ),
            )
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val path = expression.containingKtFile.virtualFilePath
        if (!isEnforced(path)) return
        val callee = expression.calleeExpression?.text ?: return
        if (callee in FUNNEL_CALLEES) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message =
                        "`$callee` manufactures a Result failure carrying a raw Throwable. " +
                            "Return a sealed outcome branch instead (STR-243).",
                ),
            )
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        if (!isRawThrowablePayloadType(parameter.typeReference?.text)) return
        checkThrowableOnSealedSubtype(parameter, parameter.name ?: "parameter")
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!isRawThrowablePayloadType(property.typeReference?.text)) return
        // Local variables are boundary/logging plumbing, not declared outcome fields.
        if (property.isLocal) return
        checkThrowableOnSealedSubtype(property, property.name ?: "property")
    }

    private fun checkThrowableOnSealedSubtype(
        element: org.jetbrains.kotlin.psi.KtElement,
        name: String,
    ) {
        val path = element.containingKtFile.virtualFilePath
        if (!isEnforced(path)) return
        val owner = element.getParentOfType<KtClass>(true) ?: element.containingClass() ?: return
        val superNames =
            owner.superTypeListEntries.mapNotNull { entry ->
                entry.typeReference?.text?.substringBefore("<")?.substringAfterLast(".")
            }
        if (superNames.isEmpty()) return
        // Sealed supertype declared in the same file?
        val file = owner.containingKtFile
        val sealedNames =
            file.declarations
                .filterIsInstance<KtClass>()
                .flatMap { declared -> listOf(declared) + declared.declarations.filterIsInstance<KtClass>() }
                .filter { declared ->
                    declared.modifierList?.text?.contains("sealed") == true
                }
                .mapNotNull { declared -> declared.name }
        val matched = superNames.firstOrNull { it in sealedNames } ?: return
        // §10 diagnostics channel exemption: exact-name list from detekt.yml only.
        if (matched in diagnosticExemptTypes) return
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(element),
                message =
                    "Throwable-typed `$name` on sealed subtype of `$matched`. " +
                        "Failure branches carry typed values, never a Throwable (STR-243).",
            ),
        )
    }

    companion object {
        private val FUNNEL_CALLEES =
            setOf("runCatchingLogged", "expectedFailure", "runCatchingCancellable", "onFailureLogged")

        private val RAW_PAYLOAD_TYPES =
            setOf("Throwable", "Exception", "kotlin.Throwable", "kotlin.Exception")

        /**
         * True when [typeText] is a raw Throwable/Exception payload type:
         * bare or `kotlin.`-qualified, nullable or not, or a `List<...>` of one.
         */
        private fun isRawThrowablePayloadType(typeText: String?): Boolean {
            if (typeText == null) return false
            val normalized = typeText.replace(WHITESPACE, "").removeSuffix("?")
            if (normalized in RAW_PAYLOAD_TYPES) return true
            if (normalized.startsWith("List<") && normalized.endsWith(">")) {
                val element = normalized.removePrefix("List<").removeSuffix(">").removeSuffix("?")
                return element in RAW_PAYLOAD_TYPES
            }
            return false
        }

        private val WHITESPACE = Regex("\\s")
    }
}
