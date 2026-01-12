package com.example.homebudget.ui.addexpense

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.money.MoneyUtils
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.utils.settings.SettingsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//AddExpenseActivity.kt – ekran dodawania nowego wydatku.
class AddExpenseActivity : AppCompatActivity() {

    private lateinit var amountInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var paymentSpinner: Spinner
    private lateinit var personSpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var recurringCheckBox: CheckBox
    private lateinit var layoutCycle: LinearLayout
    private lateinit var spinnerCycle: Spinner
    private var selectedDate: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_expense)

        amountInput = findViewById(R.id.inputAmount)
        descriptionInput = findViewById(R.id.inputDescription)
        noteInput = findViewById(R.id.inputNote)
        categorySpinner = findViewById(R.id.spinnerCategory)
        paymentSpinner = findViewById(R.id.spinnerPayment)
        personSpinner = findViewById(R.id.spinnerPerson)
        dateButton = findViewById(R.id.btnDate)
        saveButton = findViewById(R.id.btnSave)
        cancelButton = findViewById(R.id.btnCancel)
        recurringCheckBox = findViewById(R.id.checkboxRecurring)
        layoutCycle = findViewById(R.id.layoutCycle)
        spinnerCycle = findViewById(R.id.spinnerCycle)

        setupCategorySpinner()
        setupPaymentSpinner()
        setupPersonSpinner()
        setupDatePicker()
        setupValidation()
        setupErrorBorders()
        // Bloka kropki przed wpisaniem
        amountInput.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                val newText = StringBuilder(dest)
                    .replace(dstart, dend, source.subSequence(start, end).toString())
                    .toString()
                // REGUŁY:
                // - cyfry
                // - opcjonalny przecinek
                // - max 2 cyfry po przecinku
                val regex = Regex("^\\d+(?: ?\\d{0,3})*(?:,\\d{0,2})?$")
                if (newText.isEmpty() || newText.matches(regex)) null else ""
            }
        )
        //Konfiguracja spinnera z cyklami
        val cycles = listOf("1 miesiąc", "2 miesiące", "3 miesiące", "6 miesięcy", "12 miesięcy")
        val cycleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cycles)
        spinnerCycle.adapter = cycleAdapter

        //pokazuj/ukrywaj spinner w zależności od checkboxa
        recurringCheckBox.setOnCheckedChangeListener { _, isChecked ->
            layoutCycle.visibility = if (isChecked) LinearLayout.VISIBLE else LinearLayout.GONE
        }

        cancelButton.setOnClickListener {
            finish() // wraca do DashboardActivity
        }

        saveButton.setOnClickListener {
            saveExpense()
        }
    }

    private fun setupCategorySpinner() {
        val db = AppDatabase.Companion.getDatabase(this)
        val userId = Prefs.getUserId(this)

        lifecycleScope.launch {
            val settings = db.settingsDao().getSettingsForUser(userId)
            val categoriesList = if (settings != null) {
                SettingsHelper.getCategories(settings)
            } else emptyList()

            val categories = listOf("Brak") + categoriesList
            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(
                    this@AddExpenseActivity,
                    android.R.layout.simple_spinner_item,
                    categories
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
                // Ustawienie wartości domyślnej z ustawień
                val defaultCategory = Prefs.getDefaultCategory(this@AddExpenseActivity)
                val index = categories.indexOf(defaultCategory)
                categorySpinner.setSelection(if (index >= 0) index else 0)
            }
        }
    }

    private fun setupPaymentSpinner() {
        val payments = listOf("Brak", "Gotówka", "Karta", "Blik", "Przelew")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, payments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        paymentSpinner.adapter = adapter
        // Ustawienie wartości domyślnej z ustawień
        val defaultPayment = Prefs.getDefaultPayment(this@AddExpenseActivity)
        val index = payments.indexOf(defaultPayment)
        paymentSpinner.setSelection(if (index >= 0) index else 0)
    }

    private fun setupPersonSpinner() {
        val db = AppDatabase.Companion.getDatabase(this)
        val userId = Prefs.getUserId(this)

        lifecycleScope.launch {
            val settings = db.settingsDao().getSettingsForUser(userId)
            val people = if (settings != null && settings.peopleList.isNotEmpty()) {
                SettingsHelper.getPeople(settings)
            } else emptyList()

            val personList = listOf("Ja") + people

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(
                    this@AddExpenseActivity,
                    android.R.layout.simple_spinner_item,
                    personList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                personSpinner.adapter = adapter
                personSpinner.setSelection(0, false)
            }
        }
    }

    private fun setupDatePicker() {
        updateDateButton()
        dateButton.setOnClickListener {
            //Ustawienie polskiego języka i formatu
            val locale = LocaleUtils.POLISH
            Locale.setDefault(locale)
            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDate
            val dialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val newCal = Calendar.getInstance()
                    newCal.set(year, month, dayOfMonth)
                    selectedDate = newCal.timeInMillis
                    updateDateButton()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dialog.datePicker.firstDayOfWeek = Calendar.MONDAY
            dialog.show()
        }
    }

    private fun updateDateButton() {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        dateButton.text = sdf.format(selectedDate)
    }

    private fun setupValidation() {
        val watcher = {
            validateForm()
        }

        amountInput.addTextChangedListener { watcher() }
        descriptionInput.addTextChangedListener { watcher() }

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = watcher()
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) = watcher()
        }

        paymentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = watcher()
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) = watcher()
        }
    }

    private fun setupErrorBorders() {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        // Opis
        descriptionInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = descriptionInput.text.isNotBlank()
                descriptionInput.setBackgroundResource(if (valid) normalBorder else errorBorder)
            } else {
                descriptionInput.setBackgroundResource(normalBorder)
            }
        }
    }

    private fun validateForm() {
        val amountValid = MoneyUtils.parseAmount(amountInput.text.toString())?.let { it > 0 } == true
        val descriptionValid = descriptionInput.text.isNotBlank()
        val category = categorySpinner.selectedItem?.toString() ?: ""
        val payment = paymentSpinner.selectedItem?.toString() ?: ""

        val categoryValid = category.isNotBlank() && category != "Brak"
        val paymentValid = payment.isNotBlank() && payment != "Brak"

        saveButton.isEnabled = amountValid && descriptionValid && categoryValid && paymentValid

        // czerwona ramka + error
        if (!amountValid) amountInput.error = "Podaj prawidłową kwotę" else amountInput.error = null
        if (!descriptionValid) descriptionInput.error = "Wpisz opis" else descriptionInput.error = null

        //zabezpieczenie: sprawdzamy czy selectedView to TextView
        (categorySpinner.selectedView as? TextView)?.error = if (!categoryValid) "Wybierz kategorię" else null
        (paymentSpinner.selectedView as? TextView)?.error = if (!paymentValid) "Wybierz metodę" else null
    }

    private fun saveExpense() {
        val userId = Prefs.getUserId(this)
        if (userId == -1) {
            Toast.makeText(this, "Błąd: brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
            return
        }
        // Kwota
        amountInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = amountInput.text.toString()
                // Jeśli użytkownik zostawił "12," -> Nie formatuj oraz zabezpieczeni przed NULL
                if (text.isBlank() || text.endsWith(",")) return@setOnFocusChangeListener
                val value = MoneyUtils.parseAmount(text) ?: return@setOnFocusChangeListener
                // Tylko walidacja
                if (value <= 0) {
                    amountInput.error = "Podaj prawidłową kwotę"
                }
            }
        }

        val amount = MoneyUtils.parseAmount(amountInput.text.toString())
            ?: run {
                amountInput.error = "Podaj prawidłową kwotę"
                return
            }
        if (amount == null || amount <= 0) {
            amountInput.error = "Podaj prawidłową kwotę"
            return
        }
        val description = descriptionInput.text.toString()
        val note = noteInput.text.toString()
        val category = categorySpinner.selectedItem.toString()
        val paymentMethod = paymentSpinner.selectedItem.toString()
        val selectedPerson = personSpinner.selectedItem.toString()
        val person = if (selectedPerson.isBlank()) "Ja" else selectedPerson
        val isRecurring = recurringCheckBox.isChecked
        var repeatInterval = 1//domyślnie 1 miesiąc

        if (isRecurring) {
            val selectedCycle = spinnerCycle.selectedItem.toString()
            repeatInterval = selectedCycle.split(" ")[0].toInt() //wyciągamy liczbę z tekstu np."3 miesiące"
        }

        val expense = Expense(
            userId = userId,
            category = category,
            amount = amount,
            description = description,
            note = note,
            paymentMethod = paymentMethod,
            date = selectedDate,
            timestamp = System.currentTimeMillis(),
            isRecurring = isRecurring,
            repeatInterval = repeatInterval,
            person = person
        )

        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(this@AddExpenseActivity)
            withContext(Dispatchers.IO) {
                db.expenseDao().insertExpense(expense)
            }

            withContext(Dispatchers.Main) {
                // ✅ Dialog potwierdzający
                val dialog = AlertDialog.Builder(this@AddExpenseActivity)
                    .setTitle("✔️ Wydatek zapisany")
                    .setMessage("Czy chcesz dodać kolejny wydatek?")
                    .setPositiveButton("TAK") { _, _ ->
                        clearForm()
                    }
                    .setNegativeButton("NIE") { _, _ ->
                        val intent = Intent(this@AddExpenseActivity, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .create()
                dialog.show()
            }
        }
    }
    private fun clearForm() {
        amountInput.text.clear()
        descriptionInput.text.clear()
        noteInput.text.clear()
        categorySpinner.setSelection(0)
        paymentSpinner.setSelection(0)
        recurringCheckBox.isChecked = false
        spinnerCycle.setSelection(0)
        layoutCycle.visibility = LinearLayout.GONE
        selectedDate = System.currentTimeMillis()
        updateDateButton()
        saveButton.isEnabled = false
    }
}