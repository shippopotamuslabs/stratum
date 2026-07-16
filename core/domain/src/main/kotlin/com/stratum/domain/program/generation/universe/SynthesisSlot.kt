package com.stratum.domain.program.generation.universe

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.structure.SessionRegionFilter

data class SynthesisSlot(
    val id: String,
    val pattern: String,
    val primaryMuscle: String,
    val role: SlotRole,
    val repProfile: RpRepProfile,
    val allowedRegionFilters: Set<SessionRegionFilter>,
    val isCrossDomainTouch: Boolean,
    val siblingVariant: SiblingVariant?,
    /** Prefer lengthened/shortened variants when scoring lifts (must survive materialize). */
    val lengthBiasPreference: MuscleLengthBiasPreference? = null,
    /**
     * Isolation slots (e.g. quad-iso) admit NonAxialCompound machines alongside true isolation.
     * Must survive materialization so lift selection sees the same pool as the catalog templates.
     */
    val allowedFatigueProfiles: Set<FatigueProfile>? = null,
)
