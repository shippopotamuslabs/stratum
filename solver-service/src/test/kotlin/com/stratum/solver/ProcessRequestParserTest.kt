package com.stratum.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProcessRequestParserTest {
    @Test
    fun `parses uid and jobId`() {
        val req = ProcessRequestParser.parse("""{"uid":"user-1","jobId":"job-abc"}""")
        assertEquals("user-1", req.uid)
        assertEquals("job-abc", req.jobId)
    }

    @Test
    fun `allows whitespace and field order`() {
        val req = ProcessRequestParser.parse(
            """
            {
              "jobId" : "j1",
              "uid" : "u1"
            }
            """.trimIndent(),
        )
        assertEquals("u1", req.uid)
        assertEquals("j1", req.jobId)
    }

    @Test
    fun `rejects missing uid`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessRequestParser.parse("""{"jobId":"j1"}""")
        }
    }

    @Test
    fun `rejects empty jobId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessRequestParser.parse("""{"uid":"u1","jobId":""}""")
        }
    }

    @Test
    fun `rejects slash in jobId`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessRequestParser.parse("""{"uid":"u1","jobId":"a/b"}""")
        }
    }

    @Test
    fun `rejects non-object body`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessRequestParser.parse("""["uid"]""")
        }
    }
}
