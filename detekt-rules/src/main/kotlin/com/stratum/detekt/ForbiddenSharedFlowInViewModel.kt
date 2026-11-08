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
 * Enforces [contracts/ARCHITECTURE.md] §4.2 (gospel §12.2) — one-shot events
 * use `Channel<Event>(Channel.BUFFERED)` exposed as `Flow` via
 * `receiveAsFlow()`, never `SharedFlow`.
 *
 * STR-216: flags a `*ViewModel` exposing a public `SharedFlow` (or
 * `MutableSharedFlow`) event surface. `Channel`-backed `Flow` properties and
 * private backing flows are conformant and never flagged.
 */
class ForbiddenSharedFlowInViewModel(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenSharedFlowInViewModel",
            severity = Severity.CodeSmell,
            description =
                "ViewModels must not expose one-shot events as SharedFlow. " +
                    "Use Channel<Event>(Channel.BUFFERED) exposed as Flow via receiveAsFlow().",
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
            .filter { ViewModelFlowShapes.isSharedFlowType(it.typeText) }
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
                                "public `SharedFlow`. Expose one-shot events as " +
                                "`Channel<Event>(Channel.BUFFERED)` via `receiveAsFlow()` " +
                                "instead. See contracts/ARCHITECTURE.md §4.2.",
                    ),
                )
            }
    }
}
