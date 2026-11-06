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

class NoLoggingCapabilityInDomainApplicationTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lint(relativePath: String, code: String) =
        NoLoggingCapabilityInDomainApplication(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    // (a) Banned packages

    @Test
    @DisplayName("a file in the domain logging package is reported")
    fun domainLoggingPackageReported() {
        val findings =
            lint(
                "core/domain/src/main/Foo.kt",
                """
                package com.stratum.domain.logging

                class Foo
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("a file in an application telemetry subpackage is reported")
    fun applicationTelemetrySubpackageReported() {
        val findings =
            lint(
                "core/application/src/main/Foo.kt",
                """
                package com.stratum.application.telemetry.export

                class Foo
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("a package merely starting with a banned segment is not reported")
    fun lookalikePackageNotReported() {
        val findings =
            lint(
                "core/domain/src/main/Foo.kt",
                """
                package com.stratum.domain.loggingstuff

                class Foo
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    // (b) Banned type references

    @Test
    @DisplayName("importing a logging/telemetry port is reported")
    fun portImportReported() {
        BANNED_TYPES.forEach { name ->
            val findings =
                lint(
                    "core/application/src/main/Foo.kt",
                    """
                    import com.example.whatever.$name

                    class Foo
                    """.trimIndent(),
                )
            assertEquals(1, findings.size, "import of $name must be reported")
        }
    }

    @Test
    @DisplayName("a LoggingPort type reference is reported")
    fun portTypeReferenceReported() {
        val findings =
            lint(
                "core/application/src/main/FooUseCase.kt",
                """
                package com.stratum.application.foo

                class FooUseCase(val port: LoggingPort)
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("a fully qualified LoggingPort type reference is reported")
    fun qualifiedPortTypeReferenceReported() {
        val findings =
            lint(
                "core/domain/src/main/Foo.kt",
                """
                package com.stratum.domain.foo

                class Foo(val port: com.stratum.core.infrastructure.logging.LoggingPort)
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
    }

    // (c) Banned call expressions

    @Test
    @DisplayName("runCatchingLogged call is reported")
    fun runCatchingLoggedReported() {
        val findings =
            lint(
                "core/application/src/main/FooUseCase.kt",
                "suspend fun foo() = runCatchingLogged(port, \"t\", \"m\") { 1 }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("onFailureLogged call is reported")
    fun onFailureLoggedReported() {
        val findings =
            lint(
                "core/domain/src/main/Foo.kt",
                "fun foo(result: Result<Int>) = result.onFailureLogged(port, \"t\", \"m\")",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("expectedFailure call is reported")
    fun expectedFailureReported() {
        val findings =
            lint(
                "core/application/src/main/FooUseCase.kt",
                "fun foo() = expectedFailure(IllegalStateException(\"x\"))",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("LogErrorUseCase constructor call is reported")
    fun logErrorUseCaseReported() {
        val findings =
            lint(
                "core/domain/src/main/Foo.kt",
                "fun foo() = LogErrorUseCase(port)",
            )
        assertEquals(1, findings.size)
    }

    // Negatives

    @Test
    @DisplayName("an outcome carrier / drain-shape file in application main passes")
    fun carrierFileNotReported() {
        val findings =
            lint(
                "core/application/src/main/OperationalDiagnostic.kt",
                """
                package com.stratum.application.diagnostics

                data class OperationalDiagnostic(
                    val tag: String,
                    val message: String,
                    val cause: Throwable,
                )
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("KDoc and string literals mentioning LoggingPort do not flag")
    fun docAndStringMentionsNotReported() {
        val findings =
            lint(
                "core/application/src/main/HealthConnectPorts.kt",
                """
                package com.stratum.application.healthconnect

                /**
                 * A boundary drains each diagnostic through `LoggingPort.error`
                 * with the throwable attached; see also runCatchingLogged.
                 */
                class HealthConnectPorts {
                    val label: String = "LoggingPort"
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("logging capability in infrastructure sources is out of scope")
    fun infrastructurePathNotReported() {
        val findings =
            lint(
                "core/infrastructure/src/main/AndroidLoggingAdapter.kt",
                """
                package com.stratum.core.infrastructure.inference

                import com.stratum.core.infrastructure.logging.LoggingPort

                class AndroidLoggingAdapter(val port: LoggingPort)
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("a LogErrorUseCase call in a feature module is out of scope")
    fun featurePathNotReported() {
        val findings =
            lint(
                "feature/workout/src/main/Foo.kt",
                "fun foo() = LogErrorUseCase(port)",
            )
        assertEquals(0, findings.size)
    }

    private companion object {
        val BANNED_TYPES =
            listOf("LoggingPort", "AnalyticsPort", "CrashTelemetryPort", "TelemetryUploadPort")
    }
}
