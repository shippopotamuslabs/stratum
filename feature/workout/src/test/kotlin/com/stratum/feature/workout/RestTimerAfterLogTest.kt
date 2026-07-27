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
    @DisplayName("explicit zero rest skips the timer (no default fallback)")
    fun zeroRestSkipsTimer() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 0,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
            )
        assertNull(seconds)
    }

    @Test
    @DisplayName("negative rest falls back to default")
    fun negativeRestFallsBackToDefault() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = -1,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
            )
        assertEquals(RestTimerDefaults.DEFAULT_REST_SECONDS, seconds)
    }

    @Test
    @DisplayName("workout fully complete skips rest even with positive set rest")
    fun fullyCompleteSkipsRest() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 120,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
                workoutFullyComplete = true,
            )
        assertNull(seconds)
    }

    @Test
    @DisplayName("myo while cluster continues uses mini rest")
    fun myoClusterContinuesUsesMiniRest() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 15,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
                isMyoSet = true,
                nextIsMyoSet = true,
                liftDefaultRestSeconds = 180,
            )
        assertEquals(15, seconds)
    }

    @Test
    @DisplayName("myo when cluster ends uses lift default rest")
    fun myoClusterEndsUsesLiftDefault() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 15,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
                isMyoSet = true,
                nextIsMyoSet = false,
                liftDefaultRestSeconds = 180,
            )
        assertEquals(180, seconds)
    }

    @Test
    @DisplayName("myo into drop skips timer (zero rest)")
    fun myoIntoDropSkipsTimer() {
        val seconds =
            restSecondsAfterLoggingSet(
                setRestSeconds = 15,
                isSupersetMember = false,
                useRestTimerBetweenLifts = false,
                isLastInSupersetChain = true,
                isMyoSet = true,
                nextIsMyoSet = false,
                nextIsDropSet = true,
                liftDefaultRestSeconds = 180,
            )
        assertNull(seconds)
    }
}
