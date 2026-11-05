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
 *  (b) `runCatchingLogged` / `expectedFailure` call expressions (the LoggedResult
 *      funnel helpers that manufacture raw-Throwable failures),
 *  (c) Throwable-typed properties / constructor parameters declared on a subtype
 *      of a sealed type declared in the same file.
 *
 * Section-10 diagnostics exemption: subtypes of a sealed type whose name contains
 * "Diagnostic" are the partial-success diagnostics channel (STR-254..259), not
 * failure payloads. They legitimately carry `cause: Throwable` for the boundary
 * log drain and are exempt from check (c) by that name pattern.
 *
 * Ratchet: files not yet converted are listed in the rule's `legacyFiles` config
 * in config/detekt/detekt.yml. Every STR-243 cluster commit removes its files;
 * the list must end empty.
 */
class NoRawThrowableFailure(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "NoRawThrowableFailure",
            severity = Severity.CodeSmell,
            description =
                "Domain/application failures must be sealed outcome branches, not raw Throwables. " +
                    "Return the area's sealed outcome type instead of Result.failure / the LoggedResult funnel.",
            debt = Debt.TWENTY_MINS,
        )

    private val legacyFiles: List<String> =
        valueOrDefault("legacyFiles", emptyList())

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
        if (parameter.typeReference?.text?.removeSuffix("?") != "Throwable") return
        checkThrowableOnSealedSubtype(parameter, parameter.name ?: "parameter")
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.typeReference?.text?.removeSuffix("?") != "Throwable") return
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
        // §10 diagnostics channel exemption (name pattern): *Diagnostic sealed types.
        if (matched.contains(DIAGNOSTIC_MARKER)) return
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
        private val FUNNEL_CALLEES = setOf("runCatchingLogged", "expectedFailure")
        private const val DIAGNOSTIC_MARKER = "Diagnostic"
    }
}
