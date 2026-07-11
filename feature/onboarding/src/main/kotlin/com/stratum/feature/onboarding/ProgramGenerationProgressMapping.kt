package com.stratum.feature.onboarding

import com.stratum.core.ui.progress.toUi as sharedToUi
import com.stratum.domain.program.ProgramGenerationProgress

internal fun ProgramGenerationProgress.toUi(): ProgramGenerationProgressUi = sharedToUi()
