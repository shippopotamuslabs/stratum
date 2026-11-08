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
 * Enforces [contracts/ARCHITECTURE.md] §4.4 (gospel §12.1) — loading/loaded
 * flags live inside the UiState data class, not beside it as a public
 * `StateFlow<Boolean>`.
 *
 * STR-216: flags a `*ViewModel` exposing a public non-nullable
 * `StateFlow<Boolean>` fragment (the `isStarting` / `isLoaded` / `showBanner`
 * shape the STR-207 remediation tickets folded). `StateFlow<Boolean?>`
 * tri-state status reads are out of scope — see
 * [ViewModelFlowShapes.isBooleanStateFlowType].
 */
class ForbiddenBooleanStateFlowInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenBooleanStateFlowInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must not expose public StateFlow<Boolean> state fragments. " +
                    "Fold loading/loaded flags into the UiState data class.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val path = klass.containingKtFile.virtualFilePath ?: return
        if (!ViewModelFlowShapes.isViewModelClass(klass, path)) {
            return
        }

        val className = klass.name
        ViewModelFlowShapes.publicFlowSurfaces(klass)
            .filter { ViewModelFlowShapes.isBooleanStateFlowType(it.typeText) }
            .forEach { surface ->
                val property =
                    klass.getProperties().firstOrNull { it.name == surface.name }
                        ?: return@forEach
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(property),
                        message =
                            "ViewModel `$className` exposes `${surface.name}` as " +
                                "public `StateFlow<Boolean>`. Fold the flag into the " +
                                "UiState data class instead of splitting it out. " +
                                "See contracts/ARCHITECTURE.md §4.4.",
                    ),
                )
            }
    }
}
