package com.example.homebudget.ui.billsplanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.utils.money.MoneyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

//RecurringExpenseAdapter.kt – adapter dla listy cyklicznych wydatków.
class RecurringExpenseAdapter(
    private var bills: List<Expense>,
    private val onEditClick: (Expense) -> Unit,
    private val onDeleteClick: (Expense) -> Unit,
    private val onStatusChange: (Expense, String) -> Unit
) : RecyclerView.Adapter<RecurringExpenseAdapter.BillViewHolder>() {

    class BillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        val statusBar: View = itemView.findViewById(R.id.statusBar)
        val textDescription: TextView = itemView.findViewById(R.id.textBillDescription)
        val textAmount: TextView = itemView.findViewById(R.id.textBillAmount)
        val textNote: TextView = itemView.findViewById(R.id.textBillNote)
        val textDate: TextView = itemView.findViewById(R.id.textBillDate)
        val textRecurring: TextView = itemView.findViewById(R.id.textBillRecurring)
        val textStatus: TextView = itemView.findViewById(R.id.textBillStatus)
        val buttonMarkPaid: Button = itemView.findViewById(R.id.buttonMarkPaid)
        val buttonEdit: Button = itemView.findViewById(R.id.buttonEditBill)
        val buttonDelete: Button = itemView.findViewById(R.id.buttonDeleteBill)
    }

    private fun getStatusColor(context: Context, status: String): Int {
        return  when (status.lowercase()) {
            "opłacony" -> context.getColor(R.color.statusPaid)
            "nieopłacony" -> context.getColor(R.color.statusUnpaid)
            else -> context.getColor(R.color.statusUnpaid)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recurring_expense, parent, false)
        return BillViewHolder(view)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        val bill = bills[position]

        holder.textDescription.text = "Opis: ${bill.description ?: "-"}"
        holder.textAmount.text = "Kwota: ${MoneyFormatter.formatWithCurrency(bill.amount)}"

        //wyświetlamy notatkę tylko jeśli istnieje
        if (!bill.note.isNullOrBlank()) {
            holder.textNote.text = "Notatka: ${bill.note}"
            holder.textNote.visibility = View.VISIBLE
        } else {
            holder.textNote.visibility = View.GONE
        }

        //Formatowanie daty
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateText = "Termin płatności: ${sdf.format(Date(bill.date))}"

        val daysInfo = if (bill.status =="nieopłacony") {
            getDaysInfo(bill.date)
        } else {
            ""
        }
        holder.textDate.text = if (daysInfo.isNotBlank()) {
            "$dateText $daysInfo"
        } else {
            dateText
        }

        val intervalText = when (bill.repeatInterval) {
            1 -> "co miesiąc"
            2 -> "co 2 miesiące"
            3 -> "co 3 miesiące"
            6 -> "co 6 miesięcy"
            12 -> "co 12 miesięcy"
            else -> "co ${bill.repeatInterval} miesięcy"
        }
        holder.textRecurring.text = "Powtarza się: $intervalText"

        val context = holder.itemView.context
        if (bill.status == "nieopłacony") {
            holder.buttonMarkPaid.visibility = View.VISIBLE
            holder.buttonMarkPaid.setOnClickListener { onStatusChange(bill, "opłacony") }
        } else {
            holder.buttonMarkPaid.visibility = View.GONE
        }

        holder.textStatus.text = "Status: ${bill.status.replaceFirstChar { it.uppercase() }}"
        val statusColor = getStatusColor(context, bill.status)
        holder.textStatus.setTextColor(statusColor)
        holder.statusBar.setBackgroundColor(statusColor)

        holder.buttonEdit.setOnClickListener { onEditClick(bill) }
        holder.buttonDelete.setOnClickListener { onDeleteClick(bill) }
    }

    override fun getItemCount(): Int = bills.size

    fun updateData(newBills: List<Expense>) {
        bills = newBills
        notifyDataSetChanged()
    }
    private fun getDaysInfo(targetMillis: Long): String {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val target = Calendar.getInstance().apply {
            timeInMillis = targetMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val diffDays = ((target - today) / (24 * 60 * 60 * 1000)).toInt()

        return when {
            diffDays == 0 -> "⏰ dziś"
            diffDays == 1 -> "⏳ jutro"
            diffDays > 1 -> "⏳ za $diffDays dni"
            diffDays == -1 -> "⚠\uFE0F wczoraj"
            diffDays < -1 -> "⚠\uFE0F po terminie"
            else -> ""
        }
    }
}