package com.stratum.domain.program

import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.lift.Lift

data class LiftWithInsights(
    val lift: Lift,
    val trainingInsights: LiftTrainingInsights?,
)
