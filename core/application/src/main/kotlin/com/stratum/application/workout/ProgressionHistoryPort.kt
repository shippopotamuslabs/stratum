package com.stratum.application.workout

import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest

interface ProgressionHistoryPort {
    suspend fun historyForSlot(request: SlotHistoryRequest): List<SlotHistoryEntry>
}
