package com.stratum.domain.program.generation.context

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift

/**
 * High or Medium fatigue band — systemic multi-joint cost without promoting multi-pattern Low.
 */
val LiftContext.hasCompoundFatigue: Boolean
    get() =
        fatigueProfile == FatigueProfile.High ||
            fatigueProfile == FatigueProfile.Medium

/**
 * Same rule as [com.stratum.domain.lift.Lift.isCompound]: multi-pattern or High/Medium fatigue.
 * Use for Linear/scheme gates and selection candidate flags so generation stays aligned with UI.
 */
val LiftContext.isCompoundLift: Boolean
    get() = movementPatterns.size > 1 || hasCompoundFatigue
