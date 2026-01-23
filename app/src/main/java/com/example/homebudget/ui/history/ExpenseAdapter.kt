package com.example.homebudget.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.utils.money.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.*

//ExpenseAdapter.kt – adapter RecyclerView do wyświetlania listy wydatków w historii.
class ExpenseAdapter(private var groupedItems: List<ListItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // Reprezentacja elementu listy: może być nagłówkiem lub wydatkiem
    sealed class ListItem {
        data class Header(val date: String) : ListItem()
        data class ExpenseItem(val expense: Expense) : ListItem()
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textHeaderDate: TextView = itemView.findViewById(R.id.textHeaderDate)
    }

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textCategory: TextView = itemView.findViewById(R.id.textCategory)
        val textDescription: TextView = itemView.findViewById(R.id.textDescription)
        val textAmount: TextView = itemView.findViewById(R.id.textAmount)
        val textPayment: TextView = itemView.findViewById(R.id.textPayment)
        val textPerson: TextView = itemView.findViewById(R.id.textPerson)
        val textNote: TextView = itemView.findViewById(R.id.textNote)
        val textDate: TextView = itemView.findViewById(R.id.textDate)
        val textRecurring: TextView = itemView.findViewById(R.id.textRecurring)
    }

    override fun getItemViewType(position: Int): Int {
        return when (groupedItems[position]) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.ExpenseItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_expense, parent, false)
            ExpenseViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = groupedItems[position]) {
            is ListItem.Header -> {
                (holder as HeaderViewHolder).textHeaderDate.text = item.date
            }
            is ListItem.ExpenseItem -> {
                val expense = item.expense
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(expense.date))

                (holder as ExpenseViewHolder).apply {
                    textCategory.text = "Kategoria: ${expense.category}"
                    textAmount.text = "Kwota: ${MoneyFormatter.formatWithCurrency(expense.amount)}"
                    textDescription.text = "Opis: ${expense.description ?: "-"}"
                    if (expense.note.isNullOrBlank()) {
                        textNote.visibility = View.GONE
                    } else {
                        textNote.visibility = View.VISIBLE
                        textNote.text = "Notatka: ${expense.note}"
                    }
                    textPayment.text = "Metoda płatności: ${expense.paymentMethod}"
                    textPerson.text = "Osoba: ${expense.person ?: "Brak"}"
                    textDate.text = "Data: $formattedDate"
                    if (expense.isRecurring) {
                        textRecurring.visibility = View.VISIBLE
                        textRecurring.text = "Powtarzalny wydatek co ${expense.repeatInterval} mies."
                    } else {
                        textRecurring.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = groupedItems.size

    fun updateData(expenses: List<Expense>) {
        groupedItems = groupByDate(expenses)
        notifyDataSetChanged()
    }

    private fun groupByDate(expenses: List<Expense>): List<ListItem> {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val grouped = mutableListOf<ListItem>()

        expenses
            .sortedByDescending { it.date }
            .groupBy { dateFormat.format(Date(it.date)) } // tylko grupowanie!
            .forEach { (date, items) ->
                grouped.add(ListItem.Header(date))
                items.forEach { grouped.add(ListItem.ExpenseItem(it)) }
            }

        return grouped
    }
}