package com.example.homebudget.ui.billsplanner

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import android.text.InputFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.addTextChangedListener
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.remote.repository.ExpenseRemoteRepository
import com.example.homebudget.data.sync.PendingSyncHelper
import com.example.homebudget.data.sync.SyncConstants
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.ui.common.loading.LoadingDialogController
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.money.MoneyUtils
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

//AddBillActivity.kt – ekran dodawania rachunku do planera (cyklicznego lub jednorazowego).
class AddBillActivity : AppCompatActivity() {

    private lateinit var inputDescription: EditText
    private lateinit var inputAmount: EditText
    private lateinit var inputNote: EditText
    private lateinit var buttonSelectDate: Button
    private lateinit var buttonSaveBill: Button
    private lateinit var buttonCancel: Button
    private lateinit var spinnerInterval: Spinner
    private lateinit var checkboxMarkPaid: CheckBox
    private lateinit var loadingDialog: LoadingDialogController
    private var editBillId: Int? = null
    private val intervalOptions = listOf("1 miesiąc", "2 miesiące", "3 miesiące", "6 miesięcy", "12 miesięcy")

    private var selectedDate: Long = System.currentTimeMillis()
    private var userId: Int = -1
    private var originalDescription: String? = null
    private var originalAmount: Double? = null
    private var originalNote: String? = null
    private var originalDate: Long? = null
    private var originalRepeatInterval: Int? = null
    private var originalPaid: Boolean? = null
    private var isEditDataLoaded = false

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
        buttonSelectDate = findViewById(R.id.buttonSelectDate)
        buttonSaveBill = findViewById(R.id.buttonSaveBill)
        buttonCancel = findViewById(R.id.buttonCancel)
        checkboxMarkPaid = findViewById(R.id.checkboxMarkPaid)
        loadingDialog = LoadingDialogController(this)
        updateSaveButtonState(false)

        editBillId = intent.getIntExtra("EDIT_BILL_ID", -1).takeIf { it != -1 }
        if (editBillId != null) {
            loadBillForEdit(editBillId!!)
            buttonSaveBill.text = "\uD83D\uDCBE Zapisz zmiany"
        }

        userId = Prefs.getUserId(this)

        updateSelectedDateLabel()
        setupAmountFormattingAndErrors()
        setupDescriptionErrors()
        setupValidation()

        inputAmount.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                val newText = StringBuilder(dest)
                    .replace(dstart, dend, source.subSequence(start, end).toString())
                    .toString()

                val regex = Regex("^\\d+(?: ?\\d{0,3})*(?:,\\d{0,2})?$")
                if (newText.isEmpty() || newText.matches(regex)) null else ""
            }
        )

        //opcje wyboru interwału
        val intervals = listOf("1 miesiąc", "2 miesiące", "3 miesiące", "6 miesięcy", "12 miesięcy")
        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervalOptions)
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
                    validateForm()
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

            buttonSaveBill.isEnabled = false
            loadingDialog.show(if (editBillId == null) "Zapisywanie rachunku..." else "Aktualizowanie rachunku...")
            lifecycleScope.launch {
                try {
                    val db = AppDatabase.getDatabase(this@AddBillActivity)
                withContext(Dispatchers.IO) {
                    if (editBillId == null) {
                        val localId = db.expenseDao().insertExpense(expense).toInt()
                        val supabaseUid = Prefs.getSupabaseUid(this@AddBillActivity)
                        if (supabaseUid.isNullOrBlank()) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_EXPENSE,
                                    operation = SyncConstants.OP_INSERT,
                                    localId = localId,
                                    remoteId = null,
                                    payloadJson = Json.encodeToString(
                                        Expense.serializer(),
                                        expense.copy(id = localId)
                                    )
                                )
                            )
                        } else {
                            try {
                                val remoteId = ExpenseRemoteRepository.insertExpense(
                                    supabaseUid,
                                    expense.copy(id = localId)
                                )
                                db.expenseDao().updateRemoteId(localId, remoteId)
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_EXPENSE,
                                        operation = SyncConstants.OP_INSERT,
                                        localId = localId,
                                        remoteId = null,
                                        payloadJson = Json.encodeToString(Expense.serializer(), expense.copy(id = localId))
                                    )
                                )
                            }
                        }
                        if (statusValue == "nieopłacony") {
                            BillsAlarmScheduler.cancelAllReminders(this@AddBillActivity, localId)
                            BillsAlarmScheduler.scheduleAllRemindersForDate(
                                this@AddBillActivity,
                                localId,
                                selectedDate
                            )
                        }
                    } else {
                        // Edycja
                        // Pobierz aktualny obiekt
                        val existingExpense = db.expenseDao().getExpenseById(editBillId!!) ?: return@withContext
                        // Zaktualizuj lokalnie
                        val updatedExpense = existingExpense.copy(
                            description = description,
                            amount = amount,
                            note = note,
                            date = selectedDate,
                            repeatInterval = repeatInterval,
                            status = statusValue
                        )
                        db.expenseDao().updateExpense(updatedExpense)
                        // Wyślij do Supabase
                        val supabaseUid = Prefs.getSupabaseUid(this@AddBillActivity)
                        if (supabaseUid.isNullOrBlank()) {
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
                                        payloadJson = Json.encodeToString(
                                            Expense.serializer(),
                                            updatedExpense
                                        )
                                    )
                                )
                            }
                        }
                        // Alarmy
                        BillsAlarmScheduler.cancelAllReminders(this@AddBillActivity, editBillId!!)
                        if (statusValue == "nieopłacony") {
                            BillsAlarmScheduler.scheduleAllRemindersForDate(this@AddBillActivity,editBillId!!, selectedDate)
                        }
                    }
                }
                WorkSchedulerSupabase.scheduleSupabaseSync(this@AddBillActivity)
                // create notification channel (upewnij się, że kanał istnieje)
                NotificationHelper.createNotificationChannel(this@AddBillActivity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddBillActivity, if (editBillId == null) "Rachunek został dodany" else "Rachunek został zaktualizowany", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@AddBillActivity, BillsPlannerActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                    }
                } finally {
                    loadingDialog.hide()
                    validateForm()
                }
            }
        }

        buttonCancel.setOnClickListener { finish() }
    }

    private fun updateSelectedDateLabel() {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        buttonSelectDate.text = sdf.format(Date(selectedDate))
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
            val intervalIndex = intervalOptions.indexOfFirst { it.startsWith("${bill.repeatInterval} ") }
            if (intervalIndex >= 0) {
                spinnerInterval.setSelection(intervalIndex, false)
            }
            checkboxMarkPaid.isChecked = isPaidStatus(bill.status)
            originalDescription = bill.description?.trim().orEmpty()
            originalAmount = bill.amount
            originalNote = bill.note?.trim().orEmpty()
            originalDate = bill.date
            originalRepeatInterval = bill.repeatInterval
            originalPaid = isPaidStatus(bill.status)
            isEditDataLoaded = true
            validateForm()
        }
    }

    // ✅ Formatowanie kwoty + czerwone obramowanie jak w AddExpenseActivity
    private fun setupAmountFormattingAndErrors() {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        inputAmount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = inputAmount.text.toString().trim()
                if (text.isBlank()) {
                    inputAmount.setBackgroundResource(errorBorder)
                    inputAmount.error = "Podaj prawidłową kwotę"
                    return@setOnFocusChangeListener
                }

                val normalizedText = when {
                    text.endsWith(",") -> text + "00"
                    text.contains(",") && text.substringAfter(",").length == 1 -> text + "0"
                    else -> text
                }

                val value = MoneyUtils.parseAmount(normalizedText)
                if (value != null && value > 0) {
                    inputAmount.setText(MoneyFormatter.format(value))
                    inputAmount.setSelection(inputAmount.text.length)
                    inputAmount.setBackgroundResource(normalBorder)
                    inputAmount.error = null
                } else {
                    inputAmount.setBackgroundResource(errorBorder)
                    inputAmount.error = "Podaj prawidłową kwotę"
                }
            } else {
                inputAmount.setBackgroundResource(normalBorder)
            }
        }
    }

    // ✅ Walidacja opisu z użyciem search_border_error
    private fun setupDescriptionErrors() {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        inputDescription.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = inputDescription.text.isNotBlank()
                inputDescription.setBackgroundResource(if (valid) normalBorder else errorBorder)
                inputDescription.error = if (valid) null else "Wpisz opis"
            } else {
                inputDescription.setBackgroundResource(normalBorder)
            }
        }
    }

    private fun setupValidation() {
        val watcher = { validateForm() }

        inputDescription.addTextChangedListener { watcher() }
        inputAmount.addTextChangedListener { watcher() }
        inputNote.addTextChangedListener { watcher() }
        checkboxMarkPaid.setOnCheckedChangeListener { _, _ -> watcher() }

        spinnerInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = watcher()

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = watcher()
        }
    }

    private fun validateForm() {
        val descriptionValid = inputDescription.text.isNotBlank()
        val amountValid = MoneyUtils.parseAmount(inputAmount.text.toString())?.let { it > 0 } == true
        val intervalValid = spinnerInterval.selectedItem != null
        val hasChanges = if (editBillId != null) {
            isEditDataLoaded && hasFormChanges()
        } else {
            true
        }

        updateSaveButtonState(descriptionValid && amountValid && intervalValid && hasChanges)

        if (!amountValid) inputAmount.error = "Podaj prawidłową kwotę" else inputAmount.error = null
        if (!descriptionValid) inputDescription.error = "Wpisz opis" else inputDescription.error = null
    }

    private fun hasFormChanges(): Boolean {
        val currentDescription = inputDescription.text.toString().trim()
        val currentAmount = MoneyUtils.parseAmount(inputAmount.text.toString())
        val currentNote = inputNote.text.toString().trim()
        val currentRepeatInterval = spinnerInterval.selectedItem
            ?.toString()
            ?.substringBefore(" ")
            ?.toIntOrNull()
        val currentPaid = checkboxMarkPaid.isChecked

        val descriptionChanged = currentDescription != (originalDescription ?: "")
        val amountChanged = currentAmount != null &&
            kotlin.math.abs(currentAmount - (originalAmount ?: 0.0)) > 0.0001
        val noteChanged = currentNote != (originalNote ?: "")
        val dateChanged = !isSameCalendarDay(selectedDate, originalDate ?: selectedDate)
        val intervalChanged = currentRepeatInterval != originalRepeatInterval
        val paidChanged = currentPaid != (originalPaid ?: false)

        return descriptionChanged || amountChanged || noteChanged || dateChanged || intervalChanged || paidChanged
    }

    private fun isSameCalendarDay(first: Long, second: Long): Boolean {
        val firstCalendar = Calendar.getInstance().apply { timeInMillis = first }
        val secondCalendar = Calendar.getInstance().apply { timeInMillis = second }

        return firstCalendar.get(Calendar.YEAR) == secondCalendar.get(Calendar.YEAR) &&
            firstCalendar.get(Calendar.DAY_OF_YEAR) == secondCalendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun isPaidStatus(status: String?): Boolean {
        return status?.trim()?.startsWith("op", ignoreCase = true) == true
    }

    private fun updateSaveButtonState(isEnabled: Boolean) {
        buttonSaveBill.isEnabled = isEnabled
        buttonSaveBill.alpha = if (isEnabled) 1f else 0.45f
    }
}
