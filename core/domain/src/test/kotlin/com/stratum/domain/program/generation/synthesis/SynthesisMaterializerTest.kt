package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.structure.SessionRegionFilter
import com.stratum.domain.program.generation.universe.SynthesisSlot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SynthesisMaterializerTest {

    @Test
    @DisplayName("sessionTypeLabelFromRegionFilter maps UpperOnly/LowerOnly/Unrestricted")
    fun sessionTypeLabelFromRegionFilter_mapsFilters() {
        assertEquals(
            SessionTypeId.Upper,
            sessionTypeLabelFromRegionFilter(SessionRegionFilter.UpperOnly),
        )
        assertEquals(
            SessionTypeId.Lower,
            sessionTypeLabelFromRegionFilter(SessionRegionFilter.LowerOnly),
        )
        assertEquals(
            SessionTypeId.FullBody,
            sessionTypeLabelFromRegionFilter(SessionRegionFilter.Unrestricted),
        )
    }

    @Test
    @DisplayName("materialize prefers regionFilterBySession over active-slot inference")
    fun materialize_prefersRegionFilterOverActiveSlots() {
        // Active slots are upper-primary only; filter is LowerOnly → label must follow filter.
        val upperSlot =
            synthesisSlot(
                id = "horiz_press",
                pattern = "HorizontalPress",
                primaryMuscle = "Chest",
                allowed = setOf(SessionRegionFilter.UpperOnly, SessionRegionFilter.Unrestricted),
            )
        val solution =
            UnifiedSynthesisSolution(
                activeSlotIdsBySession = listOf(setOf(upperSlot.id)),
                setCountBySessionSlot = mapOf((0 to upperSlot.id) to 3),
                weekdayIndexByPosition = listOf(0),
                regionFilterBySession = listOf(SessionRegionFilter.LowerOnly),
            )

        val result = materializeSynthesisResult(listOf(upperSlot), solution)

        assertEquals(listOf(SessionRegionFilter.LowerOnly), result.regionFilterBySession)
        assertEquals(SessionTypeId.Lower, result.plans.single().sessionType)
        assertEquals(SessionTypeId.Lower, result.sessions.single().sessionType)
    }

    @Test
    @DisplayName("materialize maps all region filters onto dual plan/session labels")
    fun materialize_mapsEachRegionFilterToLegacyLabel() {
        val upper =
            synthesisSlot(
                id = "press",
                pattern = "HorizontalPress",
                primaryMuscle = "Chest",
                allowed = setOf(SessionRegionFilter.UpperOnly, SessionRegionFilter.Unrestricted),
            )
        val lower =
            synthesisSlot(
                id = "squat",
                pattern = "SquatPattern",
                primaryMuscle = "Quad",
                allowed = setOf(SessionRegionFilter.LowerOnly, SessionRegionFilter.Unrestricted),
            )
        val solution =
            UnifiedSynthesisSolution(
                activeSlotIdsBySession =
                    listOf(setOf(upper.id), setOf(lower.id), setOf(upper.id, lower.id)),
                setCountBySessionSlot =
                    mapOf(
                        (0 to upper.id) to 3,
                        (1 to lower.id) to 3,
                        (2 to upper.id) to 2,
                        (2 to lower.id) to 2,
                    ),
                weekdayIndexByPosition = listOf(0, 2, 4),
                regionFilterBySession =
                    listOf(
                        SessionRegionFilter.UpperOnly,
                        SessionRegionFilter.LowerOnly,
                        SessionRegionFilter.Unrestricted,
                    ),
            )

        val result = materializeSynthesisResult(listOf(upper, lower), solution)

        assertEquals(
            listOf(SessionTypeId.Upper, SessionTypeId.Lower, SessionTypeId.FullBody),
            result.plans.map { it.sessionType },
        )
        assertEquals(
            listOf(SessionTypeId.Upper, SessionTypeId.Lower, SessionTypeId.FullBody),
            result.sessions.map { it.sessionType },
        )
        assertEquals(solution.regionFilterBySession, result.regionFilterBySession)
    }

    @Test
    @DisplayName("materialize falls back to active-slot inference when region filters missing")
    fun materialize_fallsBackWhenRegionFiltersShort() {
        val lower =
            synthesisSlot(
                id = "hinge",
                pattern = "HipHinge",
                primaryMuscle = "Hamstring",
                allowed = setOf(SessionRegionFilter.LowerOnly, SessionRegionFilter.Unrestricted),
            )
        val solution =
            UnifiedSynthesisSolution(
                activeSlotIdsBySession = listOf(setOf(lower.id)),
                setCountBySessionSlot = mapOf((0 to lower.id) to 4),
                weekdayIndexByPosition = listOf(1),
                regionFilterBySession = emptyList(),
            )

        val result = materializeSynthesisResult(listOf(lower), solution)

        assertEquals(SessionTypeId.Lower, result.plans.single().sessionType)
        assertEquals(emptyList<SessionRegionFilter>(), result.regionFilterBySession)
    }

    private fun synthesisSlot(
        id: String,
        pattern: String,
        primaryMuscle: String,
        allowed: Set<SessionRegionFilter>,
    ): SynthesisSlot =
        SynthesisSlot(
            id = id,
            pattern = pattern,
            primaryMuscle = primaryMuscle,
            role = SlotRole.SecondaryCompound,
            repProfile = RpRepProfile.Isolation,
            allowedRegionFilters = allowed,
            isCrossDomainTouch = false,
            siblingVariant = null,
        )
}
