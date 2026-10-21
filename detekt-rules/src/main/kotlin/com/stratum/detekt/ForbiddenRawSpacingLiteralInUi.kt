package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Enforces [contracts/UI_DESIGN.md] §12.2 — spacing positions take `StratumSpacing`
 * tokens (`s4`…`s64`), never raw `N.dp` literals.
 *
 * Scoped to `Modifier.padding` / `absolutePadding` / `PaddingValues` /
 * `Arrangement.spacedBy` arguments and `contentPadding = N.dp` values in shipped UI
 * (see [SpacingPathFilters]). `0.dp` is allowed (grid-neutral). Deliberate limits,
 * documented rather than fixed:
 *
 * - `Spacer(Modifier.height/width/size(…))` is NOT covered: the same call shape sizes
 *   icons and dividers, so flagging it would false-positive on component-spec sizes.
 * - Only direct `<int>.dp` literals are flagged; arithmetic or named values pass.
 *
 * Pre-existing pinned geometry (calendar cells, plate-diagram math, dense 2dp log
 * rows, export-bitmap padding) is allowlisted per file with `@file:Suppress` and a
 * `STR-142 allowlist` reason comment.
 */
class ForbiddenRawSpacingLiteralInUi(config: Config) : Rule(config) {
    override val issue =
        Issue(
            id = "ForbiddenRawSpacingLiteralInUi",
            severity = Severity.CodeSmell,
            description =
                "Spacing positions must use StratumSpacing tokens, not raw N.dp literals. " +
                    "Use StratumSpacing.s* from :core:ui/theme — see contracts/UI_DESIGN.md §12.2.",
            debt = Debt.FIVE_MINS,
        )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val path = expression.containingKtFile.virtualFilePath
        if (!SpacingPathFilters.isEnforced(path)) {
            return
        }

        val callee = expression.calleeExpression?.text
        val isSpacingCall = callee in SPACING_CALLEES
        for (argument in expression.valueArguments) {
            val argumentName = argument.getArgumentName()?.asName?.identifier
            if (!isSpacingCall && argumentName != CONTENT_PADDING_ARGUMENT) {
                continue
            }
            val value = argument.getArgumentExpression() ?: continue
            if (isRawNonZeroDp(value)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(value),
                        message =
                            "Raw `${value.text}` in spacing position. " +
                                "Use StratumSpacing.s* — see contracts/UI_DESIGN.md §12.2.",
                    ),
                )
            }
        }
    }

    private fun isRawNonZeroDp(expression: KtExpression): Boolean {
        val qualified = expression as? KtDotQualifiedExpression ?: return false
        if (qualified.selectorExpression?.text != DP_SELECTOR) {
            return false
        }
        val receiver = qualified.receiverExpression as? KtConstantExpression ?: return false
        return receiver.text != ZERO_LITERAL
    }

    companion object {
        private val SPACING_CALLEES = setOf("padding", "absolutePadding", "PaddingValues", "spacedBy")
        private const val CONTENT_PADDING_ARGUMENT = "contentPadding"
        private const val DP_SELECTOR = "dp"
        private const val ZERO_LITERAL = "0"
    }
}
