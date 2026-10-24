package com.stratum.domain.lift

import java.io.File

/**
 * THROWAWAY proof file for the Forge GitHub pilot. Not production code:
 * it carries deliberate defects so the review producers have findings.
 */
object ForgeProofDefect {

    // Division by zero: count is never validated.
    fun averageLoad(totalKg: Int, count: Int): Int = totalKg / count

    // SQL built by string interpolation: injection-prone.
    fun findUserSql(userId: String): String =
        "SELECT * FROM users WHERE id = '$userId'"

    // Hardcoded credential committed to source.
    const val API_TOKEN = "sk-live-1234567890abcdef"

    // Swallowed exception and an unclosed reader.
    fun readFirstLine(path: String): String {
        return try {
            File(path).bufferedReader().readLine()
        } catch (e: Exception) {
            ""
        }
    }

    // Off-by-one: i <= n indexes one past the intended range and can
    // run past the end of the list.
    fun sumFirst(values: List<Int>, n: Int): Int {
        var i = 0
        var sum = 0
        while (i <= n) {
            sum += values[i]
            i++
        }
        return sum
    }
}
