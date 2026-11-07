package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
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

    private fun lintApp(
        config: Config,
        relativePath: String,
        code: String,
    ) = NoRawThrowableFailure(config).lint(write(relativePath, code))

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
    @DisplayName("runCatchingCancellable funnel call is reported")
    fun runCatchingCancellableCallReported() {
        val findings =
            lintApp(
                "core/application/src/main/FooUseCase.kt",
                "suspend fun foo() = runCatchingCancellable { 1 }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("onFailureLogged funnel call is reported")
    fun onFailureLoggedCallReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooUseCase.kt",
                "fun foo(result: Result<Int>) = result.onFailureLogged(loggingPort, \"t\", \"m\")",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("a file listed in legacyFiles is not reported")
    fun legacyFileNotReported() {
        val config =
            TestConfig(
                "legacyFiles" to
                    listOf("core/application/src/main/kotlin/com/stratum/application/foo/FooUseCase.kt"),
            )
        val findings =
            lintApp(
                config,
                "core/application/src/main/kotlin/com/stratum/application/foo/FooUseCase.kt",
                "suspend fun foo() = runCatchingCancellable { 1 }",
            )
        assertEquals(0, findings.size)
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
    @DisplayName("nullable Throwable property on a sealed subtype is reported")
    fun nullableThrowableOnSealedSubtypeReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooResult.kt",
                """
                sealed interface FooResult {
                    data class Failure(val cause: Throwable?) : FooResult
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("List of Throwable property on a sealed subtype is reported")
    fun throwableListOnSealedSubtypeReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooResult.kt",
                """
                sealed interface FooResult {
                    data class Failure(val causes: List<Throwable>) : FooResult
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("Exception property on a sealed subtype is reported")
    fun exceptionOnSealedSubtypeReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooResult.kt",
                """
                sealed interface FooResult {
                    data class Failure(val cause: kotlin.Exception) : FooResult
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("a Diagnostic-named sealed outcome carrying Throwable is reported (exact-name exemption only)")
    fun diagnosticNamedOutcomeReported() {
        val findings =
            lintApp(
                exemptConfig(),
                "core/domain/src/main/SyncDiagnosticOutcome.kt",
                """
                sealed interface SyncDiagnosticOutcome {
                    data class Failed(val cause: Throwable) : SyncDiagnosticOutcome
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("every section-10 diagnostics type on the exact-name list is exempt")
    fun section10DiagnosticsExempt() {
        SECTION_10_DIAGNOSTICS.forEach { name ->
            val findings =
                lintApp(
                    exemptConfig(),
                    "core/domain/src/main/$name.kt",
                    """
                    sealed interface $name {
                        data class Failed(val cause: Throwable) : $name
                    }
                    """.trimIndent(),
                )
            assertEquals(0, findings.size, "$name must be exempt")
        }
    }

    @Test
    @DisplayName("Throwable property on a sealed Diagnostic subtype not on the list is reported")
    fun unlistedDiagnosticReported() {
        val findings =
            lintApp(
                "core/domain/src/main/FooDiagnostic.kt",
                """
                sealed interface FooDiagnostic {
                    data class LoadFailed(val cause: Throwable) : FooDiagnostic
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
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

    private fun exemptConfig(): Config =
        TestConfig("diagnosticExemptTypes" to SECTION_10_DIAGNOSTICS)

    private companion object {
        val SECTION_10_DIAGNOSTICS =
            listOf(
                "ScheduleDiagnostic",
                "ProgramImportRollbackDiagnostic",
                "EntitlementDiagnostic",
                "ApplyGrantedPermissionsDiagnostic",
                "EquipmentProposalDiagnostic",
                "MergeLiftsDiagnostic",
                "SwapWorkoutLiftDiagnostic",
                "RepRangeShiftDiagnostic",
                "LiftHistoryDiagnostic",
                "CloudSyncDiagnostic",
            )
    }
}
