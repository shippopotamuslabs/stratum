package com.stratum.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class MainDispatcherRuleTest {
    @Test
    fun `given extension when coroutine uses Main then it runs without IllegalStateException`() =
        runTest {
            var ran = false
            withContext(Dispatchers.Main) {
                ran = true
            }
            assertTrue(ran)
        }
}
