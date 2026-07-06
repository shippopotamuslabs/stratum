package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId

class ActiveTrainingSessionExistsException(
    val activeSessionId: TrainingSessionId,
) : Exception("Active training session already exists: ${activeSessionId.value}")
