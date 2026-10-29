package com.stratum.detekt

import org.jetbrains.kotlin.psi.KtImportDirective

internal object DomainTypeReference {
    const val DOMAIN_PACKAGE_PREFIX = "com.stratum.domain"
    const val APPLICATION_PACKAGE_PREFIX = "com.stratum.application"

    // STR-172: use cases, ports, and their outcomes moved from :core:domain to
    // :core:application. UI state and composables must stay free of BOTH layers'
    // types, so the "domain type" checks cover the application package as well.
    private val LAYER_PACKAGE_PREFIXES = listOf(DOMAIN_PACKAGE_PREFIX, APPLICATION_PACKAGE_PREFIX)

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

    fun collectDomainImportedTypes(importDirective: KtImportDirective, current: Set<String>): Set<String> {
        val importPath = importDirective.importPath?.pathStr ?: return current
        if (LAYER_PACKAGE_PREFIXES.none { importPath.startsWith(it) }) {
            return current
        }
        val simpleName =
            importDirective.importedName?.asString()
                ?: importPath.substringAfterLast('.')
        return current + simpleName
    }

    fun isAllowedType(typeText: String): Boolean {
        if (typeText.contains("->")) {
            return true
        }
        val root = typeText.substringBefore('<').trim()
        return root in ALLOWED_SIMPLE_TYPES
    }

    fun typeReferencesDomain(typeText: String, domainTypes: Set<String>): Boolean {
        val tokens =
            typeText
                .split(TOKEN_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return tokens.any { token ->
            LAYER_PACKAGE_PREFIXES.any { token.startsWith(it) } || token in domainTypes
        }
    }

    private val TOKEN_SPLIT_REGEX = Regex("[<>,\\s()?.]+")
}
