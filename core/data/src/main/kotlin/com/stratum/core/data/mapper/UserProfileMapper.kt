package com.stratum.core.data.mapper

import com.stratum.core.data.db.UserProfileEntity
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday

fun UserProfileEntity.toDomain(): UserTrainingProfile =
    UserTrainingProfile(
        trainingAge = trainingAge?.let { runCatching { TrainingAge.valueOf(it) }.getOrNull() },
        lastTrained = lastTrained?.let { runCatching { LastTrainedBucket.valueOf(it) }.getOrNull() },
        weekdays = parseWeekdays(weekdays),
        sessionLengthMinutes = sessionLengthMinutes,
    )

fun UserTrainingProfile.toEntity(): UserProfileEntity =
    UserProfileEntity(
        id = UserProfileEntity.ID,
        trainingAge = trainingAge?.name,
        lastTrained = lastTrained?.name,
        weekdays = weekdays.joinToString(",") { it.name },
        sessionLengthMinutes = sessionLengthMinutes,
    )

private fun parseWeekdays(value: String): Set<Weekday> {
    if (value.isBlank()) return emptySet()
    return value.split(",")
        .mapNotNull { token ->
            token.trim().takeIf { it.isNotEmpty() }?.let { runCatching { Weekday.valueOf(it) }.getOrNull() }
        }
        .toSet()
}
