package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.SavingsGoal

fun SavingsGoalRemoteReadDto.toLocal(userId: Int): SavingsGoal {
    return SavingsGoal(
        userId = userId,
        remoteId = id,
        title = title,
        targetAmount = target_amount,
        savedAmount = saved_amount,
        endDate = end_date,
        sharedWith = shared_with,
        notificationCompletedSent = notification_completed_sent
    )
}