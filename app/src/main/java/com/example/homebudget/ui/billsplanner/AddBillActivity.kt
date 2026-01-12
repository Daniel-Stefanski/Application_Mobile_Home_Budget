package com.example.homebudget.ui.billsplanner

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.money.MoneyUtils
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

//AddBillActivity.kt – ekran dodawania rachunku do planera (cyklicznego lub jednorazowego).
class AddBillActivity : AppCompatActivity() {

    private lateinit var inputDescription: EditText
    private lateinit var inputAmount: EditText
    private lateinit var inputNote: EditText
    private lateinit var textSelectedDate: TextView
    private lateinit var buttonSelectDate: Button
    private lateinit var buttonSaveBill: Button
    private lateinit var buttonCancel: Button
    private lateinit var spinnerInterval: Spinner
    private lateinit var checkboxMarkPaid: CheckBox
    private var editBillId: Int? = null

    private var selectedDate: Long = System.currentTimeMillis()
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_bill)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 100)
            }
        }

        inputDescription = findViewById(R.id.inputDescription)
        inputAmount = findViewById(R.id.inputAmount)
        inputNote = findViewById(R.id.inputNote)
        spinnerInterval = findViewById(R.id.spinnerInterval)
        textSelectedDate = findViewById(R.id.textSelectedDate)
        buttonSelectDate = findViewById(R.id.buttonSelectDate)
        buttonSaveBill = findViewById(R.id.buttonSaveBill)
        buttonCancel = findViewById(R.id.buttonCancel)
        checkboxMarkPaid = findViewById(R.id.checkboxMarkPaid)

        editBillId = intent.getIntExtra("EDIT_BILL_ID", -1).takeIf { it != -1 }
        if (editBillId != null) {
            loadBillForEdit(editBillId!!)
            buttonSaveBill.text = "\uD83D\uDCBE Zapisz zmiany"
        }

        userId = Prefs.getUserId(this)

        updateSelectedDateLabel()
        setupAmountFormattingAndErrors()
        setupDescriptionErrors()

        //opcje wyboru interwału
        val intervals = listOf("1 miesiąc", "2 miesiące", "3 miesiące", "6 miesięcy", "12 miesięcy")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)
        spinnerInterval.adapter = adapterSpinner

        buttonSelectDate.setOnClickListener {
            val local = LocaleUtils.POLISH
            Locale.setDefault(local)

            val cal = Calendar.getInstance()
            cal.timeInMillis = selectedDate

            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val newCal = Calendar.getInstance()
                    newCal.set(year, month, day)
                    selectedDate = newCal.timeInMillis
                    updateSelectedDateLabel()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.firstDayOfWeek = Calendar.MONDAY
            val datePickerField = DatePickerDialog::class.java.getDeclaredField("mDatePicker")
            datePickerField.isAccessible = true
            datePicker.show()
        }

        buttonSaveBill.setOnClickListener {
            val description = inputDescription.text.toString()
            val amountText = inputAmount.text.toString()
            val note = inputNote.text.toString().ifBlank { null } // 💡

            //Walidacja opisu
            if (description.isBlank()) {
                inputDescription.setBackgroundResource(R.drawable.shape_search_border_error)
                Toast.makeText(this, "Uzupełnij opis rachunku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amountText.isBlank()) {
                inputAmount.setBackgroundResource(R.drawable.shape_search_border_error)
                Toast.makeText(this, "Uzupełnij kwotę", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //Bezpieczne parsowanie kwoty (obsługa spacji i przecinków)
            val amount = MoneyUtils.parseAmount(amountText)

            if (amount == null || amount <= 0.0) {
                inputAmount.setBackgroundResource(R.drawable.shape_search_border_error)
                Toast.makeText(this, "Kwota musi być dodatnią liczbą", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //Walidacja daty - nie może być przeszłości
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            if (selectedDate < todayStart) {
                Toast.makeText(this, "Termin płatności nie może być w przeszłości", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedIntervalText = spinnerInterval.selectedItem.toString()
            val repeatInterval = selectedIntervalText.split(" ")[0].toInt() // wyciągamy liczbę np. "3"

            val statusValue = if (checkboxMarkPaid.isChecked) "opłacony" else "nieopłacony"

            val expense = Expense(
                userId = userId,
                category = "Rachunki",
                amount = amount,
                description = description,
                note = note,
                paymentMethod = "Przelew",
                date = selectedDate,
                timestamp = System.currentTimeMillis(),
                isRecurring = true,
                repeatInterval = repeatInterval,
                status = statusValue
            )

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@AddBillActivity)
                withContext(Dispatchers.IO) {
                    if (editBillId == null) {
                        // Dodawanie
                        val newId = db.expenseDao().insertExpense(expense).toInt()
                        if (expense.status == "nieopłacony") {
                            BillsAlarmScheduler.scheduleAllRemindersForDate(this@AddBillActivity, newId, expense.date)
                        }
                    } else {
                        // Edycja
                        db.expenseDao().updateExpenseFull(
                            editBillId!!,
                            description,
                            amount,
                            note,
                            selectedDate,
                            repeatInterval
                        )
                        BillsAlarmScheduler.cancelAllReminders(this@AddBillActivity, editBillId!!)
                        if (statusValue == "nieopłacony") {
                            BillsAlarmScheduler.scheduleAllRemindersForDate(this@AddBillActivity,editBillId!!, selectedDate)
                        }
                    }
                }

                // create notification channel (upewnij się, że kanał istnieje)
                NotificationHelper.createNotificationChannel(this@AddBillActivity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddBillActivity, if (editBillId == null) "Rachunek został dodany" else "Rachunek został zaktualizowany", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@AddBillActivity, BillsPlannerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
            }
        }

        buttonCancel.setOnClickListener { finish() }
    }

    private fun updateSelectedDateLabel() {
        val sdf = SimpleDateFormat("dd.MM.yyyy", LocaleUtils.POLISH)
        textSelectedDate.text = "Wybrana data: ${sdf.format(Date(selectedDate))}"
    }
    // Edycja rachunku
    private fun loadBillForEdit(id: Int) {
        lifecycleScope.launch {
            val bill = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@AddBillActivity)
                    .expenseDao()
                    .getExpenseById(id)
            } ?: return@launch
            inputDescription.setText(bill.description)
            inputAmount.setText(MoneyUtils.formatAmount(bill.amount))
            inputNote.setText(bill.note)
            selectedDate = bill.date
            updateSelectedDateLabel()
            checkboxMarkPaid.isChecked = bill.status == "opłacony"
        }
    }

    // ✅ Formatowanie kwoty + czerwone obramowanie jak w AddExpenseActivity
    private fun setupAmountFormattingAndErrors() {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        inputAmount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                inputAmount.setBackgroundResource(normalBorder)
            } else {
                val value = MoneyUtils.parseAmount(inputAmount.text.toString())
                if (value != null && value > 0) {
                    inputAmount.setText(MoneyUtils.formatAmount(value))
                    inputAmount.setSelection(inputAmount.text.length)
                    inputAmount.setBackgroundResource(R.drawable.shape_search_border)
                } else {
                    inputAmount.setBackgroundResource(R.drawable.shape_search_border_error)
                }
            }
        }
    }

    // ✅ Walidacja opisu z użyciem search_border_error
    private fun setupDescriptionErrors() {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        inputDescription.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                inputDescription.setBackgroundResource(normalBorder)
            } else {
                val valid = inputDescription.text.isNotBlank()
                inputDescription.setBackgroundResource(if (valid) normalBorder else errorBorder)
            }
        }
    }
}