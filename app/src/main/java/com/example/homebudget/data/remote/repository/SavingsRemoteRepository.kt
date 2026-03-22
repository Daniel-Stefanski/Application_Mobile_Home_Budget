package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.entity.Contribution
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.data.remote.SupabaseClient
import com.example.homebudget.data.remote.dto.ContributionRemoteDto
import com.example.homebudget.data.remote.dto.ContributionRemoteReadDto
import com.example.homebudget.data.remote.dto.SavingsGoalRemoteDto
import com.example.homebudget.data.remote.dto.SavingsGoalRemoteReadDto
import com.example.homebudget.data.remote.dto.SavingsGoalUpdateDto
import io.github.jan.supabase.postgrest.from

object SavingsRemoteRepository {
    suspend fun insertGoal(uid: String, goal: SavingsGoal): Long {
        val dto = SavingsGoalRemoteDto(
            user_id = uid,
            title = goal.title,
            target_amount = goal.targetAmount,
            saved_amount = goal.savedAmount,
            end_date = goal.endDate,
            shared_with = goal.sharedWith,
            notification_completed_sent = goal.notificationCompletedSent
        )

        val result = SupabaseClient.client
            .from("savings_goals")
            .insert(dto) {
                select()
            }
            .decodeSingle<SavingsGoalRemoteReadDto>()

        return result.id
    }

    suspend fun updateGoal(remoteId: Long, goal: SavingsGoal) {
        val dto = SavingsGoalUpdateDto(
            title = goal.title,
            target_amount = goal.targetAmount,
            saved_amount = goal.savedAmount,
            end_date = goal.endDate,
            shared_with = goal.sharedWith,
            notification_completed_sent = goal.notificationCompletedSent
        )
        SupabaseClient.client
            .from("savings_goals")
            .update(dto) {
                filter { eq("id", remoteId) }
            }
    }

    suspend fun deleteGoal(remoteId: Long) {
        SupabaseClient.client
            .from("savings_goals")
            .delete {
                filter { eq("id", remoteId) }
            }
    }

    suspend fun insertContribution(uid: String, goalRemoteId: Long, c: Contribution): Long {
        val dto = ContributionRemoteDto(
            user_id = uid,
            goal_id = goalRemoteId,
            person_name = c.personName,
            amount = c.amount,
            timestamp = c.timestamp
        )

        val inserted = SupabaseClient.client
            .from("contributions")
            .insert(dto) {
                select()
            }
            .decodeSingle<ContributionRemoteReadDto>()

        return inserted.id
    }

    suspend fun fetchGoals(uid: String): List<SavingsGoalRemoteReadDto> {
        return SupabaseClient.client
            .from("savings_goals")
            .select {
                filter { eq("user_id", uid) }
            }
            .decodeList()
    }

    suspend fun fetchContributions(uid: String): List<ContributionRemoteReadDto> {
        return SupabaseClient.client
            .from("contributions")
            .select {
                filter { eq("user_id", uid) }
            }
            .decodeList()
    }

    suspend fun deleteAllForUser(supabaseUid: String) {
        SupabaseClient.client
            .from("savings_goals")
            .delete {
                filter { eq("user_id", supabaseUid) }
            }
    }
}