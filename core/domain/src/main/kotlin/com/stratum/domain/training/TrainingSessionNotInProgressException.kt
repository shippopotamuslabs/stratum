package com.stratum.domain.training

import com.stratum.domain.training.identity.TrainingSessionId

class TrainingSessionNotInProgressException(
    val sessionId: TrainingSessionId,
    val actualStatus: TrainingSessionStatus,
) : Exception("Training session is not in progress: ${sessionId.value} (status=$actualStatus)")
