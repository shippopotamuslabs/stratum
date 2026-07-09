package com.stratum.domain.onboarding

import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** One-shot read of the persisted [UserTrainingProfile] (or empty when unset). */
class GetUserTrainingProfileUseCase @Inject constructor(
    private val repository: UserTrainingProfileRepository,
) {
    suspend operator fun invoke(): UserTrainingProfile =
        repository.observeProfile().first() ?: UserTrainingProfile.Empty
}
