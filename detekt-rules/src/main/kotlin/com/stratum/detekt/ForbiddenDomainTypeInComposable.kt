package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Enforces [contracts/ARCHITECTURE.md] §4 — feature composables take presentation types,
 * not domain models. Scoped to public composables in feature modules that import
 * `com.stratum.domain.*`.
 */
class ForbiddenDomainTypeInComposable(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenDomainTypeInComposable",
            severity = Severity.CodeSmell,
            description =
                "Feature @Composable functions must not take domain model parameters. " +
                    "Map domain → presentation in the ViewModel instead.",
            debt = Debt.FIVE_MINS,
        )

    private var domainImportedTypes: Set<String> = emptySet()

    override fun visitKtFile(file: KtFile) {
        domainImportedTypes = emptySet()
        super.visitKtFile(file)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)

        val importPath = importDirective.importPath?.pathStr ?: return
        if (!importPath.startsWith(DOMAIN_PACKAGE_PREFIX)) {
            return
        }

        val simpleName =
            importDirective.importedName?.asString()
                ?: importPath.substringAfterLast('.')
        domainImportedTypes = domainImportedTypes + simpleName
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        val path = function.containingKtFile.virtualFilePath ?: return
        if (!StratumPathFilters.isFeatureModule(path)) {
            return
        }
        if (domainImportedTypes.isEmpty()) {
            return
        }
        if (!function.hasComposableAnnotation()) {
            return
        }
        if (function.hasModifier(KtTokens.PRIVATE_KEYWORD) || function.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
            return
        }

        function.valueParameters.forEach { parameter ->
            val typeText = parameter.typeReference?.text ?: return@forEach
            if (isAllowedType(typeText)) {
                return@forEach
            }
            if (!typeReferencesDomain(typeText, domainImportedTypes)) {
                return@forEach
            }

            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(parameter),
                    message =
                        "Composable `${function.name}` must not take domain type `$typeText`. " +
                            "Expose presentation models from the ViewModel. See contracts/ARCHITECTURE.md §4.",
                ),
            )
        }
    }

    companion object {
        private const val DOMAIN_PACKAGE_PREFIX = "com.stratum.domain"

        private val ALLOWED_SIMPLE_TYPES =
            setOf(
                "String",
                "Int",
                "Boolean",
                "Float",
                "Double",
                "Long",
                "Short",
                "Byte",
                "Char",
                "Unit",
                "Modifier",
                "Any",
            )

        private fun KtAnnotated.hasComposableAnnotation(): Boolean =
            annotationEntries.any { entry ->
                entry.shortName?.asString() == "Composable" ||
                    entry.typeReference?.text?.endsWith("Composable") == true
            }

        private fun isAllowedType(typeText: String): Boolean {
            if (typeText.contains("->")) {
                return true
            }
            val root = typeText.substringBefore('<').trim()
            return root in ALLOWED_SIMPLE_TYPES
        }

        private fun typeReferencesDomain(typeText: String, domainTypes: Set<String>): Boolean {
            val tokens =
                typeText
                    .split(TOKEN_SPLIT_REGEX)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            return tokens.any { token ->
                token.startsWith(DOMAIN_PACKAGE_PREFIX) || token in domainTypes
            }
        }

        private val TOKEN_SPLIT_REGEX = Regex("[<>,\\s()?.]+")
    }
}
