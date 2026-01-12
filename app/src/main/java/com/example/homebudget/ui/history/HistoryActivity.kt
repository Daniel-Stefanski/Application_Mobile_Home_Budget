package com.example.homebudget.ui.history

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.widget.Button
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.view.View
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.app.DatePickerDialog
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat

//HistoryActivity.kt – ekran historii wydatków (z wyszukiwaniem, sortowaniem i filtrowaniem).
class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExpenseAdapter
    private lateinit var allExpenses: List<Expense>
    private var userId: Int = -1
    private lateinit var db: AppDatabase

    //Filtry zaawansowane
    private var startDate: String? = null
    private var endDate: String? = null
    private var minAmount: Double? = null
    private var maxAmount: Double? = null
    private var categoryFilter: String? = null
    private var paymentMethodFilter: String? = null
    private var onlyRecurring: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recyclerView = findViewById(R.id.recyclerExpenses)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ExpenseAdapter(emptyList())
        recyclerView.adapter = adapter

        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.queryHint = "Szukaj wydatku po opisie lub notatce..."
        searchView.isIconified = false
        searchView.clearFocus()
        val spinnerSort = findViewById<Spinner>(R.id.spinnerSort)
        val buttonAdvancedFilter = findViewById<Button>(R.id.buttonAdvancedFilter)

        //Pobranie userId z Intentu
        userId = Prefs.getUserId(this)
        if(userId == -1) {
            Toast.makeText(this, "Błąd: brak użytkownika", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
           allExpenses = db.expenseDao().getExpensesForUser(userId)
            runOnUiThread {
                //ustawienia danych
                adapter.updateData(allExpenses)
            }
        }

        //Obsługa wyszukiwania
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                updateFilteredData(newText, spinnerSort.selectedItemPosition)
                return true
            }
        })

        //Obsługa sortowania
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateFilteredData(searchView.query.toString(), position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        //Filtr zaawansowany
        buttonAdvancedFilter.setOnClickListener { showAdvancedFilterDialog(searchView, spinnerSort) }

        //Obsługa powrotu do ekranu głównego
        findViewById<Button>(R.id.buttonBackToMain).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showAdvancedFilterDialog(searchView: SearchView, spinnerSort: Spinner) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_advanced_filter, null)
        val editStartDate = dialogView.findViewById<EditText>(R.id.editStartDate)
        val editEndDate = dialogView.findViewById<EditText>(R.id.editEndDate)
        val editMinAmount = dialogView.findViewById<EditText>(R.id.editMinAmount)
        val editMaxAmount = dialogView.findViewById<EditText>(R.id.editMaxAmount)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val spinnerPayment = dialogView.findViewById<Spinner>(R.id.spinnerPayment)
        val checkRecurring = dialogView.findViewById<CheckBox>(R.id.checkRecurring)

        val categories = mutableListOf("Wszystkie")
        categories.addAll(allExpenses.mapNotNull { it.category }.distinct())
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        val paymentMethods = listOf("Wszystkie", "Gotówka", "Karta", "Blik", "Przelew")
        spinnerPayment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paymentMethods)

        //Ustaw poprzenie wartości
        editStartDate.setText(startDate ?: "")
        editEndDate.setText(endDate ?: "")
        editMinAmount.setText(minAmount?.toString() ?: "")
        editMaxAmount.setText(maxAmount?.toString() ?: "")
        spinnerCategory.setSelection(categories.indexOf(categoryFilter ?: "Wszystkie"))
        spinnerPayment.setSelection(paymentMethods.indexOf(paymentMethodFilter ?: "Wszystkie"))
        checkRecurring.isChecked = onlyRecurring

        //Obsługa wyboru daty
        val dateClickListener = View.OnClickListener { view ->
            //Ustawienie polskiego języka i formatu
            val locale = LocaleUtils.POLISH
            Locale.setDefault(locale)
            val editText = view as EditText
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val dateString = "%02d.%02d.%04d".format(dayOfMonth, month + 1, year)
                    editText.setText(dateString)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            //Ustaw pierwszy dzień tygodnia na poniedziałek
            datePicker.datePicker.firstDayOfWeek = Calendar.MONDAY
            datePicker.show()
        }
        editStartDate.setOnClickListener(dateClickListener)
        editEndDate.setOnClickListener(dateClickListener)

        AlertDialog.Builder(this)
            .setTitle("Filtr zaawansowany")
            .setView(dialogView)
            .setPositiveButton("Zastosuj") { _, _ ->
                startDate = editStartDate.text.toString().ifBlank { null }
                endDate = editEndDate.text.toString().ifBlank { null }
                minAmount = editMinAmount.text.toString().toDoubleOrNull()
                maxAmount = editMaxAmount.text.toString().toDoubleOrNull()
                val selectedCategory = spinnerCategory.selectedItem.toString()
                categoryFilter = if (selectedCategory == "Wszystkie") null else selectedCategory
                val selectedPayment = spinnerPayment.selectedItem.toString()
                paymentMethodFilter = if (selectedPayment == "Wszystkie") null else selectedPayment
                onlyRecurring = checkRecurring.isChecked

                updateFilteredData(searchView.query.toString(), spinnerSort.selectedItemPosition)
            }
            .setNegativeButton("Wyczyść") { _, _ ->
                startDate = null
                endDate = null
                minAmount = null
                maxAmount = null
                categoryFilter = null
                paymentMethodFilter = null
                onlyRecurring = false

                updateFilteredData(searchView.query.toString(), spinnerSort.selectedItemPosition)
            }
            .setNeutralButton("Anuluj", null)
            .show()
    }

    private fun updateFilteredData(searchQuery: String?, sortOption: Int) {
        var filtered = allExpenses

        //filtr kategorii
        categoryFilter?.let { selected ->
            filtered = filtered.filter { it.category == selected }
        }

        //filtr wyszukiwania
        if (!searchQuery.isNullOrBlank()) {
            filtered = filtered.filter {
                (it.description?.contains(searchQuery, true) == true) ||
                        (it.note?.contains(searchQuery, true) == true)
            }
        }

        //Filtrowanie daty (z konwersją String -> timestamp)
        if (startDate != null || endDate != null) {
            val formatter = SimpleDateFormat("dd.MM.yyyy", LocaleUtils.POLISH)
            val startTimestamp = startDate?.let { formatter.parse(it)?.time }
            val endTimestamp = endDate?.let {
                val parsed = formatter.parse(it)
                parsed?.time?.plus(24 * 60 * 60 * 1000 - 1)
            }

            filtered = filtered.filter { expense ->
                val expenseDate = expense.date
                val afterStart = startTimestamp?.let { expenseDate >= it } ?: true
                val beforeEnd = endTimestamp?.let { expenseDate <= it } ?: true
                afterStart && beforeEnd
            }
        }

        //Filtrowanie kwoty
        if (minAmount != null) filtered = filtered.filter { it.amount >= minAmount!! }
        if (maxAmount != null) filtered = filtered.filter { it.amount <=maxAmount!! }

        //Filtrowanie metody płatności
        if (paymentMethodFilter != null) {
            filtered = filtered.filter { it.paymentMethod == paymentMethodFilter }
        }

        //Filtrowanie powtarzalnych
        if (onlyRecurring) filtered = filtered.filter { it.isRecurring }

        //sortowanie
        filtered = when (sortOption) {
            0 -> filtered.sortedByDescending { it.date }//od najnowszych
            1 -> filtered.sortedBy { it.date } //od najstarszej
            2 -> filtered.sortedBy { it.amount } //kwota rosnąca
            3 -> filtered.sortedByDescending { it.amount } //kwota malejąca
            else -> filtered
        }
        val emptyState = findViewById<TextView>(R.id.textEmptyState)
        if (filtered.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
        adapter.updateData(filtered)
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            allExpenses = db.expenseDao().getExpensesForUser(userId)
            adapter.updateData(allExpenses)
        }
    }
}