package com.stratum.application.response

import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.application.training.RefreshWeeklySeriesUseCase

interface ResponseProfilePersistencePort {
    suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile)

    /**
     * Replaces **only** the weekly series tables ([ExtractedResponseProfile.weeklyStimulus] and
     * [ExtractedResponseProfile.liftPerformance]) in a single transaction, leaving all other
     * response tables (epochs, dose-response, outcome indices, …) untouched. Used by
     * [com.stratum.application.training.RefreshWeeklySeriesUseCase] for the cheap, deterministic
     * post-workout refresh that keeps the dashboard charts fresh without re-running the heavy
     * dose-response modeling pipeline.
     */
    suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile)

    suspend fun clearAll()
}
