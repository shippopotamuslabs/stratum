package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class NoRawThrowableFailureTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lintApp(relativePath: String, code: String) =
        NoRawThrowableFailure(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    @Test
    @DisplayName("Result.failure is reported in application main sources")
    fun resultFailureReported() {
        val findings =
            lintApp(
                "core/application/src/main/FooUseCase.kt",
                "fun foo(): Result<Unit> = Result.failure(IllegalStateException(\"x\"))",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("runCatchingLogged funnel call is reported")
    fun funnelCallReported() {
        val findings =
            lintApp(
                "core/application/src/main/FooUseCase.kt",
                "suspend fun foo() = runCatchingLogged(loggingPort, \"t\", \"m\") { 1 }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("Throwable property on a sealed Result subtype is reported")
    fun throwableOnSealedResultReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooResult.kt",
                """
                sealed interface FooResult {
                    data class Failure(val cause: Throwable) : FooResult
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("Throwable property on a sealed Diagnostic subtype is exempt (section-10 channel)")
    fun throwableOnDiagnosticExempt() {
        val findings =
            lintApp(
                "core/domain/src/main/FooDiagnostic.kt",
                """
                sealed interface FooDiagnostic {
                    data class LoadFailed(val cause: Throwable) : FooDiagnostic
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("Result.failure outside domain/application main sources is not reported")
    fun featurePathNotReported() {
        val findings =
            lintApp(
                "feature/workout/src/main/Foo.kt",
                "fun foo(): Result<Unit> = Result.failure(IllegalStateException(\"x\"))",
            )
        assertEquals(0, findings.size)
    }
}
