package com.stratum.detekt

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner

/**
 * Shared PSI surface for the STR-216 ViewModel state-shape rules
 * ([ForbiddenMultipleStateFlowInViewModel], [ForbiddenBooleanStateFlowInViewModel],
 * [ForbiddenSharedFlowInViewModel]).
 *
 * Only *declared* public flow surfaces are counted: body properties and
 * primary-constructor `val`/`var` parameters with an explicit `StateFlow` /
 * `SharedFlow` type annotation. `private`, `internal`, and `protected`
 * members are transient VM internals, not screen-collected state, and are
 * never counted — the ticket scopes each rule to *public* surfaces.
 */
internal object ViewModelFlowShapes {
    private const val VIEW_MODEL_SUFFIX = "ViewModel"

    fun isViewModelClass(klass: KtClass, path: String): Boolean {
        if (!StratumPathFilters.isEnforced(path)) {
            return false
        }
        val className = klass.name ?: return false
        return className.endsWith(VIEW_MODEL_SUFFIX)
    }

    /** Declared-type text of every public flow surface: body properties + ctor `val`s. */
    fun publicFlowSurfaces(klass: KtClass): List<NamedType> {
        val body =
            klass.getProperties()
                .filter { isPublic(it) }
                .mapNotNull { property ->
                    val typeText = property.typeReference?.text ?: return@mapNotNull null
                    val name = property.name ?: return@mapNotNull null
                    NamedType(name, typeText)
                }
        val ctor =
            klass.primaryConstructorParameters
                .filter { isPublic(it) && (it.hasValOrVar()) }
                .mapNotNull { parameter ->
                    val typeText = parameter.typeReference?.text ?: return@mapNotNull null
                    val name = parameter.name ?: return@mapNotNull null
                    NamedType(name, typeText)
                }
        return body + ctor
    }

    fun isStateFlowType(typeText: String): Boolean =
        flowRoot(typeText) == STATE_FLOW || flowRoot(typeText) == MUTABLE_STATE_FLOW

    fun isSharedFlowType(typeText: String): Boolean =
        flowRoot(typeText) == SHARED_FLOW || flowRoot(typeText) == MUTABLE_SHARED_FLOW

    /**
     * Non-nullable `StateFlow<Boolean>` only. `StateFlow<Boolean?>` is a
     * tri-state status read (unknown until the first emission — e.g.
     * `OnboardingStatusViewModel.isComplete`), not a loading/loaded fragment
     * split out of a UiState, so it is out of scope.
     */
    fun isBooleanStateFlowType(typeText: String): Boolean {
        if (!isStateFlowType(typeText)) {
            return false
        }
        val argument =
            typeText.substringAfter('<', missingDelimiterValue = "").substringBeforeLast('>').trim()
        return argument == BOOLEAN_TYPE
    }

    private fun flowRoot(typeText: String): String {
        val root = typeText.substringBefore('<').trim().trimEnd('?').trim()
        return root.substringAfterLast('.')
    }

    private fun isPublic(owner: KtModifierListOwner): Boolean =
        !owner.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
            !owner.hasModifier(KtTokens.INTERNAL_KEYWORD) &&
            !owner.hasModifier(KtTokens.PROTECTED_KEYWORD)

    data class NamedType(
        val name: String,
        val typeText: String,
    )

    private const val STATE_FLOW = "StateFlow"
    private const val MUTABLE_STATE_FLOW = "MutableStateFlow"
    private const val SHARED_FLOW = "SharedFlow"
    private const val MUTABLE_SHARED_FLOW = "MutableSharedFlow"
    private const val BOOLEAN_TYPE = "Boolean"
}
