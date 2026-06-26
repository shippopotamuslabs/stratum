package com.stratum.domain.response

import javax.inject.Inject

class EmptyExperimentRegistry @Inject constructor() : ExperimentRegistry {
    override fun active(): List<ExperimentBlock> = emptyList()

    override fun record(block: ExperimentBlock) = Unit
}
