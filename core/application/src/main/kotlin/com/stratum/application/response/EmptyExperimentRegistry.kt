package com.stratum.application.response

import javax.inject.Inject
import com.stratum.domain.response.ExperimentBlock

class EmptyExperimentRegistry @Inject constructor() : ExperimentRegistry {
    override fun active(): List<ExperimentBlock> = emptyList()

    override fun record(block: ExperimentBlock) = Unit
}
