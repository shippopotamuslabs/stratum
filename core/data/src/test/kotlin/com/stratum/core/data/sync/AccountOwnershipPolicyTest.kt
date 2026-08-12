package com.stratum.core.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AccountOwnershipPolicyTest {
    @Test
    @DisplayName("same durable owner does not wipe")
    fun sameDurableOwnerKeepsRoom() {
        assertFalse(AccountOwnershipPolicy.mustWipe("uid-a", null, "uid-a"))
        assertEquals("uid-a", AccountOwnershipPolicy.previousOwner("uid-a", null))
    }

    @Test
    @DisplayName("logout keeps durable owner so the next uid still wipes")
    fun differentUidAfterLogoutWipes() {
        assertTrue(AccountOwnershipPolicy.mustWipe("uid-a", null, "uid-b"))
    }

    @Test
    @DisplayName("blank durable owner is untrusted and wipes")
    fun blankDurableOwnerWipes() {
        assertTrue(AccountOwnershipPolicy.mustWipe("", null, "uid-b"))
        assertTrue(AccountOwnershipPolicy.mustWipe(null, null, "uid-b"))
        assertNull(AccountOwnershipPolicy.previousOwner("", null))
    }

    @Test
    @DisplayName("memory owner is used when durable owner is blank")
    fun memoryOwnerWhenDurableBlank() {
        assertFalse(AccountOwnershipPolicy.mustWipe("", "uid-b", "uid-b"))
        assertTrue(AccountOwnershipPolicy.mustWipe(null, "uid-a", "uid-b"))
        assertEquals("uid-a", AccountOwnershipPolicy.previousOwner(null, "uid-a"))
    }
}
