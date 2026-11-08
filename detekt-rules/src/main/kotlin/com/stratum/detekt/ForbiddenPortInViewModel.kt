package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Enforces [contracts/ARCHITECTURE.md] §4.4 — ViewModels depend on use cases, not ports.
 *
 * STR-215 widening (option A: explicit allowlist): the legacy `*Port` suffix
 * match stays, and any other domain/application-layer type that is not a
 * `*UseCase` is now also flagged — [AuthManager] and the proposal-preference
 * interfaces are service ports in everything but name. `*Repository` stays
 * owned by [ForbiddenRepositoryInViewModel] so the two rules never
 * double-report. [ALLOWED_NON_USE_CASE_TYPES] records the blessed or
 * out-of-scope exceptions with their reasons; everything else routes through
 * a use case.
 */
class ForbiddenPortInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenPortInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must inject *UseCase classes from :core:application " +
                    "(or an allowlisted presentation exception). Domain/application-layer " +
                    "interfaces that are not use cases are service ports in " +
                    "everything but name — put them behind a use case instead.",
            debt = Debt.FIVE_MINS,
        )

    private var layerImportedTypes: Set<String> = emptySet()

    override fun visitKtFile(file: KtFile) {
        layerImportedTypes = emptySet()
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        layerImportedTypes =
            DomainTypeReference.collectDomainImportedTypes(importDirective, layerImportedTypes)
    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        val path = klass.containingKtFile.virtualFilePath ?: return
        if (!StratumPathFilters.isEnforced(path)) {
            return
        }

        val className = klass.name ?: return
        if (!className.endsWith(VIEW_MODEL_SUFFIX)) {
            return
        }

        val constructor = klass.primaryConstructor ?: return
        constructor.valueParameters.forEach { parameter ->
            val typeText = parameter.typeReference?.text ?: return@forEach
            if (typeText.endsWith(PORT_SUFFIX)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(parameter),
                        message =
                            "ViewModel `$className` must not depend on `$typeText`. " +
                                "Inject a *UseCase from :core:application instead. " +
                                "See contracts/ARCHITECTURE.md §4.4.",
                    ),
                )
                return@forEach
            }
            if (!isLayerNonUseCaseDependency(typeText)) {
                return@forEach
            }
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "ViewModel `$className` must not depend on `$typeText`. " +
                            "Domain/application-layer interfaces that are not use cases are " +
                            "service ports in everything but name — inject a *UseCase " +
                            "from :core:application instead. " +
                            "See contracts/ARCHITECTURE.md §4.4.",
                ),
            )
        }
    }

    private fun isLayerNonUseCaseDependency(typeText: String): Boolean {
        val root = typeText.substringBefore('<').trim().trimEnd('?').trim()
        if (root.endsWith(USE_CASE_SUFFIX)) {
            return false
        }
        if (root.endsWith(REPOSITORY_SUFFIX)) {
            return false
        }
        if (root in ALLOWED_NON_USE_CASE_TYPES) {
            return false
        }
        return DomainTypeReference.typeReferencesDomain(typeText, layerImportedTypes)
    }

    companion object {
        private const val VIEW_MODEL_SUFFIX = "ViewModel"
        private const val PORT_SUFFIX = "Port"
        private const val USE_CASE_SUFFIX = "UseCase"
        private const val REPOSITORY_SUFFIX = "Repository"

        /**
         * STR-215 explicit allowlist (option A). Each entry names a
         * non-use-case ViewModel dependency that is deliberately not routed
         * through a use case:
         *
         * - `WorkoutEditSession`: domain-layer transient draft holder;
         *   STR-207 transient-draft exception (only transient state lives in
         *   the VM, gospel §12.4).
         * - `EquipmentPickResult`: domain-layer navigation result bus;
         *   STR-207 EquipmentPickResult-collector exception.
         * - `ForceRefreshDerivedTrainingData`: pre-existing
         *   application-layer fun-interface seam in `SettingsViewModel`;
         *   out of STR-215 scope (3 named VMs) — follow-up routes it through
         *   `ForceRefreshDerivedTrainingDataUseCase`.
         */
        private val ALLOWED_NON_USE_CASE_TYPES =
            setOf(
                "WorkoutEditSession",
                "EquipmentPickResult",
                "ForceRefreshDerivedTrainingData",
            )
    }
}
