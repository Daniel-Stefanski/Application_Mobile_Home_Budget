package com.example.homebudget.ui.billsplanner

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//BillsPlannerActivity.kt – ekran planowania rachunków/cyklicznych płatności.
class BillsPlannerActivity : AppCompatActivity() {

    private var userId: Int = -1
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecurringExpenseAdapter
    private lateinit var buttonAddBill: Button
    private lateinit var textBillsSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bills_planner)

        recyclerView = findViewById(R.id.recyclerBills)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = RecurringExpenseAdapter(
            emptyList(),
            onEditClick = { bill ->
                val intent = Intent(this, AddBillActivity::class.java)
                intent.putExtra("EDIT_BILL_ID", bill.id)
                startActivity(intent)
            },
            onDeleteClick = { bill -> deleteBill(bill) },
            onStatusChange = { bill , newStatus -> onStatusChange(bill, newStatus) }
        )
        recyclerView.adapter = adapter

        val spinnerSort: Spinner = findViewById(R.id.spinnerSortBills)
        //Lista opcji sortowania
        val sortOptions = listOf(
            "Data rosnąco",
            "Data malejąco",
            "Kwota rosnąco",
            "Kwota malejąco"
        )
        spinnerSort.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, sortOptions
        )
        //nasłuchiwanie zmian
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadRecurringBills() //zawsze przeładowuje listę już posortowaną
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        buttonAddBill = findViewById(R.id.buttonAddBill)
        textBillsSummary = findViewById(R.id.textBillsSummary)

        userId = Prefs.getUserId(this)

        if (userId != -1) {
            loadRecurringBills()
        } else {
            Toast.makeText(this, "Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
        }

        // Powrót
        findViewById<Button>(R.id.buttonBackToMain).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        buttonAddBill.setOnClickListener {
            val intent = Intent(this, AddBillActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadRecurringBills() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@BillsPlannerActivity)
            val recurringBills: List<Expense> = withContext(Dispatchers.IO) {
                db.expenseDao().getRecurringExpenses(userId)
            }

            // sortowanie według spinnera
            val spinnerSort: Spinner = findViewById(R.id.spinnerSortBills)
            val selectedSort = spinnerSort.selectedItem.toString()

            val sortedBills = when (selectedSort) {
                "Data rosnąco" -> recurringBills.sortedBy { it.date }
                "Data malejąco" -> recurringBills.sortedByDescending { it.date }
                "Kwota rosnąco" -> recurringBills.sortedBy { it.amount }
                "Kwota malejąco" -> recurringBills.sortedByDescending { it.amount }
                else -> recurringBills
            }

            //aktualizacja listy
            adapter.updateData(sortedBills)

            // Komunikat pustej listy
            val emptyPlanner = findViewById<TextView>(R.id.textEmptyPlanner)
            if (sortedBills.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyPlanner.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyPlanner.visibility = View.GONE
            }
            //aktualizacja podsumowania
            val totalCount = sortedBills.size
            val totalAmount = sortedBills.sumOf { it.amount }
            textBillsSummary.text = "Rachunki: $totalCount | Suma: ${MoneyFormatter.formatWithCurrency(totalAmount)}"
        }
    }

    private fun deleteBill(expense: Expense) {
        BillsAlarmScheduler.cancelAllReminders(this@BillsPlannerActivity, expense.id)

        AlertDialog.Builder(this)
            .setTitle("Usuń rachunek")
            .setMessage("Czy na pewno chcesz usunąć ten rachunek?")
            .setPositiveButton("Tak") { _, _ ->
                val db = AppDatabase.getDatabase(this)
                lifecycleScope.launch {
                    db.expenseDao().unsetRecurring(expense.id)
                    loadRecurringBills()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun onStatusChange(expense: Expense, newStatus: String) {
        val db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.expenseDao().updateStatus(expense.id, newStatus)
            }

            // jeśli ustawiliśmy na opłacony -> anuluj przypomnienia
            if (newStatus == "opłacony") {
                BillsAlarmScheduler.cancelAllReminders(this@BillsPlannerActivity, expense.id)
            } else {
                // jeśli ustawiliśmy na nieopłacony -> zaplanuj przypomnienia, ale najpierw sprawdź ustawienia
                val notificationsEnabled = Prefs.isNotificationsEnabled(this@BillsPlannerActivity)
                if (notificationsEnabled) {
                    BillsAlarmScheduler.scheduleAllRemindersForDate(this@BillsPlannerActivity, expense.id, expense.date)
                }
            }
            loadRecurringBills()
            Toast.makeText(this@BillsPlannerActivity, "Status zamieniono na $newStatus.", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()
        if (userId != -1) {
            loadRecurringBills()
        }
    }
}