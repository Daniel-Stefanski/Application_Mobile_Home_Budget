package com.example.homebudget.ui.billsplanner

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.remote.repository.ExpenseRemoteRepository
import com.example.homebudget.data.sync.PendingSyncHelper
import com.example.homebudget.data.sync.SyncConstants
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// BillsPlannerActivity.kt - ekran planowania rachunkow/cyklicznych platnosci.
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
            onStatusChange = { bill, newStatus -> onStatusChange(bill, newStatus) }
        )
        recyclerView.adapter = adapter

        buttonAddBill = findViewById(R.id.buttonAddBill)
        textBillsSummary = findViewById(R.id.textBillsSummary)

        userId = Prefs.getUserId(this)

        if (userId != -1) {
            loadRecurringBills()
        } else {
            Toast.makeText(this, "Brak zalogowanego uzytkownika", Toast.LENGTH_SHORT).show()
        }

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

            val sortedBills = recurringBills.sortedWith(
                compareBy<Expense> { isPaidStatus(it.status) }
                    .thenBy { it.date }
            )

            adapter.updateData(sortedBills)

            val emptyPlanner = findViewById<TextView>(R.id.textEmptyPlanner)
            if (sortedBills.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyPlanner.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyPlanner.visibility = View.GONE
            }

            val totalCount = sortedBills.size
            val totalAmount = sortedBills.sumOf { it.amount }
            textBillsSummary.text = "Rachunki: $totalCount | Suma: ${MoneyFormatter.formatWithCurrency(totalAmount)}"
        }
    }

    private fun deleteBill(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle("Usun z planowania rachunkow")
            .setMessage("Czy na pewno chcesz usunac ten rachunek z planowania?")
            .setPositiveButton("Tak") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@BillsPlannerActivity)
                    withContext(Dispatchers.IO) {
                        BillsAlarmScheduler.cancelAllReminders(this@BillsPlannerActivity, expense.id)

                        val updatedExpense = expense.copy(isRecurring = false)
                        db.expenseDao().updateExpense(updatedExpense)
                        val supabaseUid = Prefs.getSupabaseUid(this@BillsPlannerActivity)
                        if (supabaseUid.isNullOrBlank()) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_EXPENSE,
                                    operation = SyncConstants.OP_UPDATE,
                                    localId = updatedExpense.id,
                                    remoteId = updatedExpense.remoteId,
                                    payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                                )
                            )
                        } else {
                            try {
                                if (updatedExpense.remoteId != null) {
                                    ExpenseRemoteRepository.updateExpense(
                                        supabaseUid = supabaseUid,
                                        remoteId = updatedExpense.remoteId!!,
                                        expense = updatedExpense
                                    )
                                } else {
                                    val remoteId = ExpenseRemoteRepository.insertExpense(supabaseUid, updatedExpense)
                                    db.expenseDao().updateRemoteId(updatedExpense.id, remoteId)
                                }
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_EXPENSE,
                                        operation = if (updatedExpense.remoteId == null)
                                            SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE,
                                        localId = updatedExpense.id,
                                        remoteId = updatedExpense.remoteId,
                                        payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                                    )
                                )
                            }
                        }
                    }
                    WorkSchedulerSupabase.scheduleSupabaseSync(this@BillsPlannerActivity)
                    loadRecurringBills()
                    Toast.makeText(this@BillsPlannerActivity, "Rachunek usuniety z planera", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun onStatusChange(expense: Expense, newStatus: String) {
        val db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            val updatedExpense = expense.copy(status = newStatus)

            withContext(Dispatchers.IO) {
                db.expenseDao().updateExpense(updatedExpense)

                val supabaseUid = Prefs.getSupabaseUid(this@BillsPlannerActivity)

                if (!supabaseUid.isNullOrBlank()) {
                    try {
                        if (updatedExpense.remoteId != null) {
                            ExpenseRemoteRepository.updateExpense(
                                supabaseUid = supabaseUid,
                                remoteId = updatedExpense.remoteId!!,
                                expense = updatedExpense
                            )
                        } else {
                            val remoteId = ExpenseRemoteRepository.insertExpense(supabaseUid, updatedExpense)
                            db.expenseDao().updateRemoteId(updatedExpense.id, remoteId)
                        }
                    } catch (e: Exception) {
                        val op = if (updatedExpense.remoteId == null)
                            SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE

                        PendingSyncHelper.enqueueOrMerge(
                            db.pendingSyncDao(),
                            PendingSync(
                                entityType = SyncConstants.ENTITY_EXPENSE,
                                operation = op,
                                localId = updatedExpense.id,
                                remoteId = updatedExpense.remoteId,
                                payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                            )
                        )
                        Log.e("BILLS", "Supabase status update FAILED", e)
                    }
                } else {
                    PendingSyncHelper.enqueueOrMerge(
                        db.pendingSyncDao(),
                        PendingSync(
                            entityType = SyncConstants.ENTITY_EXPENSE,
                            operation = SyncConstants.OP_UPDATE,
                            localId = updatedExpense.id,
                            remoteId = updatedExpense.remoteId,
                            payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                        )
                    )
                }
            }

            WorkSchedulerSupabase.scheduleSupabaseSync(this@BillsPlannerActivity)

            if (isPaidStatus(newStatus)) {
                BillsAlarmScheduler.cancelAllReminders(this@BillsPlannerActivity, expense.id)
            } else {
                val notificationsEnabled = Prefs.isNotificationsEnabled(this@BillsPlannerActivity)
                if (notificationsEnabled) {
                    BillsAlarmScheduler.scheduleAllRemindersForDate(this@BillsPlannerActivity, expense.id, expense.date)
                }
            }

            loadRecurringBills()
            Toast.makeText(this@BillsPlannerActivity, "Status zamieniono na $newStatus.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPaidStatus(status: String): Boolean {
        return status.trim().lowercase().startsWith("op")
    }

    override fun onResume() {
        super.onResume()
        if (userId != -1) {
            loadRecurringBills()
        }
    }
}
