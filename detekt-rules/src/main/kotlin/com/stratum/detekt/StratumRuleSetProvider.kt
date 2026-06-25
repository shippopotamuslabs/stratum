package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class StratumRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = RULE_SET_ID

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                ForbiddenMaterialComposeInFeatures(config),
                ForbiddenRawColorLiteralInFeatures(config),
                ForbiddenMockKInTests(config),
                ForbiddenVerifyInViewModelTests(config),
                ForbiddenRepositoryInViewModel(config),
                ForbiddenDomainTypeInComposable(config),
            ),
        )

    companion object {
        const val RULE_SET_ID = "stratum"
    }
}
