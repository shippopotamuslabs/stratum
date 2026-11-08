package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass

/**
 * Enforces [contracts/ARCHITECTURE.md] §4.4 (gospel §12.1) — one
 * `StateFlow<UiState>` per ViewModel.
 *
 * STR-216: flags a `*ViewModel` exposing more than one public `StateFlow`.
 * `Channel`-backed one-shot events declared as `Flow` are not state and are
 * never counted. Sanctioned extras (the shell theme stream, the `userMessage`
 * side-flow pending the STR-207 bless-vs-migrate decision, the STR-209 rest
 * passthrough) are suppressed per-class with a ticket reference — see the
 * STR-207 exception list, not just this failure.
 */
class ForbiddenMultipleStateFlowInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenMultipleStateFlowInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must expose a single public StateFlow (the UiState). " +
                    "Fold extra state into the UiState data class or suppress per-class " +
                    "with a STR-207 exception-list reason.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val path = klass.containingKtFile.virtualFilePath ?: return
        if (!ViewModelFlowShapes.isViewModelClass(klass, path)) {
            return
        }

        val stateFlows =
            ViewModelFlowShapes.publicFlowSurfaces(klass)
                .filter { ViewModelFlowShapes.isStateFlowType(it.typeText) }
        if (stateFlows.size <= 1) {
            return
        }

        val className = klass.name
        val names = stateFlows.joinToString(", ") { "`${it.name}`" }
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(klass),
                message =
                    "ViewModel `$className` exposes ${stateFlows.size} public StateFlows " +
                        "($names). Keep one `StateFlow<UiState>` per ViewModel " +
                        "(gospel §12.1); sanctioned extras are suppressed per-class " +
                        "with a STR-207 exception-list reason. " +
                        "See contracts/ARCHITECTURE.md §4.4.",
            ),
        )
    }
}
