package com.stratum.core.data.mapper

import com.stratum.core.data.db.LiftSetEntity
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Legacy-row hydration (STR-248): rows written by older builds or imports can
 * hold values the current editor would never produce. The mapper is the
 * construction seam, so hydration must not reject a legitimate stored state —
 * a stored rest of 0 means "no rest prescribed" and must load as 0. Before the
 * fix this threw the exact `restSeconds must be positive, was 0` exception that
 * crash-looped 1.62.5 on launch.
 */
class ProgramMapperLegacyRowTest {

    @Test
    @DisplayName("LiftSet entity with zero rest hydrates to a domain set with zero rest")
    fun liftSetEntityWithZeroRestHydrates() {
        val entity =
            LiftSetEntity(
                id = LiftSetId("set-legacy"),
                workoutLiftId = WorkoutLiftId("wl-1"),
                position = 0,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                isTopSet = true,
                restSeconds = 0,
            )
        val domain = entity.toDomain()
        assertEquals(0, domain.restSeconds)
    }
}
