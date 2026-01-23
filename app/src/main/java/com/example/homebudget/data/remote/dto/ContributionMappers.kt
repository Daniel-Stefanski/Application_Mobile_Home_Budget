package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.Contribution

fun ContributionRemoteReadDto.toLocal(
    userId: Int,
    localGoalId: Int
): Contribution {
    return Contribution(
        userId = userId,
        remoteId = id,
        goalId = localGoalId,
        personName = person_name,
        amount = amount,
        timestamp = timestamp
    )
}