package com.stratum.feature.workout

import com.stratum.feature.workout.notification.RestTimerDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("restSecondsAfterLoggingSet")
class RestTimerAfterLogTest {

    @Test
    @DisplayName("non-superset always starts rest using set restSeconds")
    fun nonSupersetUsesSetRest() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 150,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
            )
        assertEquals(150, seconds)
    }

    @Test
    @DisplayName("superset with useRestTimerBetweenLifts starts rest on intermediate lift")
    fun supersetWithRestBetweenStartsOnMiddle() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 90,
                isSupersetMember = true,
                useRestTimerBetweenLifts = true,
                isLastInSupersetChain = false,
            )
        assertEquals(90, seconds)
    }

    @Test
    @DisplayName("superset without useRestTimerBetweenLifts skips rest on intermediate lift")
    fun supersetWithoutRestBetweenSkipsMiddle() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 90,
                isSupersetMember = true,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = false,
            )
        assertNull(seconds)
    }

    @Test
    @DisplayName("superset without useRestTimerBetweenLifts starts rest on last lift in chain")
    fun supersetWithoutRestBetweenStartsOnLast() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 180,
                isSupersetMember = true,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
            )
        assertEquals(180, seconds)
    }

    @Test
    @DisplayName("zero set rest falls back to default")
    fun zeroRestFallsBackToDefault() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 0,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
            )
        assertEquals(RestTimerDefaults.DEFAULT_REST_SECONDS, seconds)
    }
}
