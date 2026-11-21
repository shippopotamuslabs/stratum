package com.stratum.feature.program

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateProgramEditorCutoverTest {
    @Test
    @DisplayName("the editor step composes the sheet and cannot present the tabbed body")
    fun editorStepComposesSheetOnly() {
        val screen = sourceFile("ui/CreateProgramScreen.kt")
        val state = sourceFile("CreateProgramUiState.kt")
        val lines = screen.lines()
        val bodyIndex =
            lines.indices.firstOrNull { index ->
                lines[index].trim() == "CreateProgramStep.Editor ->" &&
                    lines.getOrNull(index + 1)?.contains("ProgramSheet(") == true
            }
        checkNotNull(bodyIndex) { "editor step does not compose ProgramSheet" }
        val arm = lines.drop(bodyIndex + 1).takeWhile { line -> line.trim() != "}" }

        assertTrue(arm.any { line -> line.contains("ProgramSheet(") })
        assertFalse(arm.any { line -> line.contains("CreateProgramEditorContent") })
        assertFalse(screen.contains("CreateProgramEditorContent"))
        assertFalse(screen.contains("ProgramEditorSurface"))
        assertFalse(state.contains("ProgramEditorSurface"))
        assertFalse(state.contains("editorSurface"))
    }

    private fun sourceFile(relativeToFeatureMain: String): String {
        val candidates =
            listOf(
                File("src/main/kotlin/com/stratum/feature/program/$relativeToFeatureMain"),
                File("feature/program/src/main/kotlin/com/stratum/feature/program/$relativeToFeatureMain"),
            )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("missing $relativeToFeatureMain from ${File(".").absolutePath}")
    }
}
