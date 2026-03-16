package com.example.homebudget.ui.savings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.money.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

//SavingsGoalAdapter.kt – adapter RecyclerView dla celów oszczędnościowych.
class SavingsGoalAdapter(
    private var goals: List<SavingsGoal>,
    private val onAddAmountClick: (SavingsGoal) -> Unit,
    private val onWithdrawClick: (SavingsGoal) -> Unit,
    private val onDeleteClick: (SavingsGoal) -> Unit,
    private val onEditClick: (SavingsGoal) -> Unit,
    private val onViewContributionsClick: (SavingsGoal) -> Unit
) : RecyclerView.Adapter<SavingsGoalAdapter.SavingsGoalViewHolder>() {

    class SavingsGoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        val textTarget: TextView = itemView.findViewById(R.id.textTarget)
        val textSaved: TextView = itemView.findViewById(R.id.textSaved)
        val textProgress: TextView = itemView.findViewById(R.id.textProgress)
        val textSharedWith: TextView = itemView.findViewById(R.id.textSharedWith)
        val textDaysLeft: TextView = itemView.findViewById(R.id.textDaysLeft)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val buttonAdd: Button = itemView.findViewById(R.id.buttonAddAmount)
        val buttonWithdraw: Button = itemView.findViewById(R.id.buttonWithdraw)
        val buttonEdit: Button = itemView.findViewById(R.id.buttonEditGoal)
        val buttonDelete: Button = itemView.findViewById(R.id.buttonDeleteGoal)
        val buttonViewContributions: Button = itemView.findViewById(R.id.buttonViewContributions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavingsGoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saving_goal, parent, false)
        return SavingsGoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavingsGoalViewHolder, position: Int) {
        val goal = goals[position]

        //Domyślny tytuł
        var titletext = "Cel: ${goal.title}"
        //Ostrzeżenie, jeśli termin za <=3 dni i cel nieukończony
        if (goal.endDate != null && goal.savedAmount < goal.targetAmount) {
            val now = System.currentTimeMillis()
            val daysLeft = TimeUnit.MILLISECONDS.toDays(goal.endDate - now)
            if (daysLeft in 0..3) {
                titletext += " ⚠\uFE0F"
            }
        }

        holder.textTitle.text = titletext
        holder.textTarget.text = "Kwota docelowa: ${MoneyFormatter.formatWithCurrency(goal.targetAmount)}"
        holder.textSaved.text = "Zaoszczędzono: ${MoneyFormatter.formatWithCurrency(goal.savedAmount)}"

        holder.textSharedWith.text = if (goal.sharedWith.isNullOrBlank()) {
            "Z kim: Tylko ja"
        } else {
            "Z kim: ${goal.sharedWith}"
        }

        // ✅ Procentowy postęp
        val progress = if (goal.targetAmount > 0)
            ((goal.savedAmount / goal.targetAmount) * 100).toInt()
        else 0
        holder.progressBar.progress = progress.coerceAtMost(100)

        if (goal.savedAmount >= goal.targetAmount) {
            holder.textProgress.text = "\uD83C\uDF89 Gratulacje! Cel osiągnięty!"
        } else {
            holder.textProgress.text = "Postęp: ${progress.coerceAtMost(100)}%"
        }

        // ✅ Liczba dni do końca
        if (goal.endDate != null) {
            val now = System.currentTimeMillis()
            val diff = goal.endDate - now
            val daysLeft = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            val formattedDate = SimpleDateFormat("dd.MM.yyyy", LocaleUtils.POLISH)
                .format(Date(goal.endDate))
            holder.textDaysLeft.text = if (daysLeft >= 0) {
                "Termin: $formattedDate\nPozostało dni: $daysLeft"
            } else {
                "Termin: $formattedDate\nPo terminie o: ${-daysLeft} dni"
            }
        } else {
            holder.textDaysLeft.text = "Bez terminu"
        }

        // Obsługa kliknięć
        holder.buttonAdd.setOnClickListener { onAddAmountClick(goal) }
        holder.buttonWithdraw.setOnClickListener { onWithdrawClick(goal) }
        holder.buttonEdit.setOnClickListener { onEditClick(goal) }
        holder.buttonDelete.setOnClickListener { onDeleteClick(goal) }
        holder.buttonViewContributions.setOnClickListener { onViewContributionsClick(goal) }
        // Zablokowanie przycisku dodaj kwotę gdy cel zostanie osiągnięty
        val isCompleted = goal.savedAmount >= goal.targetAmount
        holder.buttonAdd.isEnabled = !isCompleted
        holder.buttonAdd.alpha = if (isCompleted) 0.5f else 1f
        // Zablokowanie przycisku wypłaty kwoty gdy cel ma 0.
        val canWithdraw = goal.savedAmount > 0
        holder.buttonWithdraw.isEnabled = canWithdraw
        holder.buttonWithdraw.alpha = if (canWithdraw) 1f else 0.5f
    }

    override fun getItemCount(): Int = goals.size

    fun updateData(newGoals: List<SavingsGoal>) {
        goals = newGoals
        notifyDataSetChanged()
    }
}