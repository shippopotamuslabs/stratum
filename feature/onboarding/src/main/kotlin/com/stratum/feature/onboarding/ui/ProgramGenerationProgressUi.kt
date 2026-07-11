package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.stratum.core.ui.progress.distinctGenerationDetail as coreDistinctGenerationDetail
import com.stratum.core.ui.progress.programGenerationPhaseStringRes as corePhaseStringRes
import com.stratum.core.ui.progress.programGenerationProgressSubtitle as coreProgressSubtitle
import com.stratum.core.ui.progress.userFacingGenerationDetail as coreUserFacingDetail
import com.stratum.feature.onboarding.ProgramGenerationProgressPhaseUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi

/** @see com.stratum.core.ui.progress.userFacingGenerationDetail */
internal fun userFacingGenerationDetail(detail: String): String? =
    coreUserFacingDetail(detail)

/** @see com.stratum.core.ui.progress.distinctGenerationDetail */
internal fun distinctGenerationDetail(detail: String, phaseLabel: String): String? =
    coreDistinctGenerationDetail(detail, phaseLabel)

@StringRes
internal fun programGenerationPhaseStringRes(phase: ProgramGenerationProgressPhaseUi): Int =
    corePhaseStringRes(phase)

@Composable
internal fun programGenerationProgressSubtitle(
    progress: ProgramGenerationProgressUi,
    phaseLabel: String,
): String? = coreProgressSubtitle(progress, phaseLabel)
