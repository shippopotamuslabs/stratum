package com.stratum.domain.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SubscriptionPricingTest {

    @Nested
    @DisplayName("iso8601PeriodToDays")
    inner class IsoDays {
        @Test
        fun `P1D is 1 day`() {
            assertEquals(1, iso8601PeriodToDays("P1D"))
        }

        @Test
        fun `P1W is 7 days`() {
            assertEquals(7, iso8601PeriodToDays("P1W"))
        }

        @Test
        fun `P2W is 14 days`() {
            assertEquals(14, iso8601PeriodToDays("P2W"))
        }

        @Test
        fun `P1M is approximately 30 days`() {
            assertEquals(30, iso8601PeriodToDays("P1M"))
        }

        @Test
        fun `P1Y is approximately 365 days`() {
            assertEquals(365, iso8601PeriodToDays("P1Y"))
        }

        @Test
        fun `unknown periods are 0`() {
            assertEquals(0, iso8601PeriodToDays(""))
            assertEquals(0, iso8601PeriodToDays("PT1H"))
            assertEquals(0, iso8601PeriodToDays("P1DT1H"))
            assertEquals(0, iso8601PeriodToDays("weekly"))
        }
    }

    @Nested
    @DisplayName("trialDaysFromPhase")
    inner class TrialDays {
        @Test
        fun `multiplies period days by billing cycle count`() {
            assertEquals(14, trialDaysFromPhase("P2W", 1))
            assertEquals(14, trialDaysFromPhase("P1W", 2))
            assertEquals(30, trialDaysFromPhase("P1M", 1))
        }

        @Test
        fun `unknown period or non-positive cycle count is 0`() {
            assertEquals(0, trialDaysFromPhase("PT1H", 1))
            assertEquals(0, trialDaysFromPhase("P1W", 0))
            assertEquals(0, trialDaysFromPhase("P1W", -1))
        }
    }

    @Nested
    @DisplayName("monthlyEquivalentMicros")
    inner class MonthlyEquivalent {
        @Test
        fun `P1Y divides by 12`() {
            assertEquals(4_165_833L, monthlyEquivalentMicros(49_990_000L, "P1Y"))
        }

        @Test
        fun `P1M is unchanged`() {
            assertEquals(7_990_000L, monthlyEquivalentMicros(7_990_000L, "P1M"))
        }

        @Test
        fun `P2M divides by 2`() {
            assertEquals(7_990_000L, monthlyEquivalentMicros(15_980_000L, "P2M"))
        }

        @Test
        fun `weeks and unknown periods cannot compute`() {
            assertNull(monthlyEquivalentMicros(7_990_000L, "P1W"))
            assertNull(monthlyEquivalentMicros(7_990_000L, "P1D"))
            assertNull(monthlyEquivalentMicros(7_990_000L, ""))
            assertNull(monthlyEquivalentMicros(-1L, "P1M"))
        }
    }

    @Nested
    @DisplayName("annualSavingsPercent")
    inner class Savings {
        @Test
        fun `computes rounded percent between annual monthly equivalent and monthly price`() {
            assertEquals(48, annualSavingsPercent(4_165_833L, 7_990_000L))
        }

        @Test
        fun `returns 0 when annual is not cheaper`() {
            assertEquals(0, annualSavingsPercent(7_990_000L, 7_990_000L))
            assertEquals(0, annualSavingsPercent(8_000_000L, 7_990_000L))
        }

        @Test
        fun `returns null when monthly price cannot be used`() {
            assertNull(annualSavingsPercent(4_165_833L, 0L))
            assertNull(annualSavingsPercent(4_165_833L, -1L))
            assertNull(annualSavingsPercent(-1L, 7_990_000L))
        }
    }
}
