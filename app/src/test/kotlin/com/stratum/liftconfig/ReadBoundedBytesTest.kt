package com.stratum.liftconfig

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReadBoundedBytesTest {

    @Test
    @DisplayName("Given a stream within cap when read then all bytes are returned")
    fun readsWithinCap() {
        val bytes = ByteArray(100) { it.toByte() }

        val result = readBoundedBytes(open = { ByteArrayInputStream(bytes) }, capBytes = 100)

        assertArrayEquals(bytes, result)
    }

    @Test
    @DisplayName("Given a stream over cap when read then null aborts without full allocation")
    fun abortsOverCap() {
        val bytes = ByteArray(10_000)

        val result = readBoundedBytes(open = { ByteArrayInputStream(bytes) }, capBytes = 1_000)

        assertNull(result)
    }

    @Test
    @DisplayName("Given no stream when read then null is returned")
    fun nullStreamFails() {
        assertNull(readBoundedBytes(open = { null }, capBytes = 1_000))
    }

    @Test
    @DisplayName("Given a failing stream when read then null is returned")
    fun failingStreamFails() {
        val broken =
            object : InputStream() {
                override fun read(): Int = throw IOException("boom")
            }

        assertNull(readBoundedBytes(open = { broken }, capBytes = 1_000))
    }
}
