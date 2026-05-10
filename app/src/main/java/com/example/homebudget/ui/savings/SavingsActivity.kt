package com.example.homebudget.ui.savings

import android.app.AlertDialog
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.text.InputFilter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Contribution
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.data.remote.repository.SavingsRemoteRepository
import com.example.homebudget.data.sync.PendingSyncHelper
import com.example.homebudget.data.sync.SyncConstants
import com.example.homebudget.notifications.scheduler.SavingsGoalAlarmScheduler
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.ui.common.loading.LoadingDialogController
import com.example.homebudget.ui.common.loading.LoadingOverlayController
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.money.MoneyUtils
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

//SavingsActivity.kt â€“ ekran zarzÄ…dzania oszczÄ™dnoĹ›ciami uĹĽytkownika.
class SavingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptySaving: TextView
    private lateinit var adapter: SavingsGoalAdapter
    private lateinit var loadingOverlay: LoadingOverlayController
    private lateinit var loadingDialog: LoadingDialogController
    private var userId: Int = -1
    private var selectedEndDate: Long? = null  // <- wybrana data zakoĹ„czenia

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_savings)
        loadingOverlay = LoadingOverlayController(this)
        loadingDialog = LoadingDialogController(this)

        recyclerView = findViewById(R.id.recyclerGoals)
        emptySaving = findViewById(R.id.textEmptySaving)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SavingsGoalAdapter(
            emptyList(),
            onAddAmountClick = { goal -> showAddAmountDialog(goal) },
            onWithdrawClick = { goal -> showWithdrawDialog(goal) },
            onDeleteClick = { goal -> deleteGoal(goal) },
            onEditClick = { goal -> showEditGoalDialog(goal) },
            onViewContributionsClick = { goal -> showContributionsDialogModern(goal) }
        )
        recyclerView.adapter = adapter

        // Pobranie userId z Intentu
        userId = Prefs.getUserId(this)
        if (userId == -1) {
            Toast.makeText(this, "BĹ‚Ä…d: brak uĹĽytkownika", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ZaĹ‚adowanie listy
        loadGoals()

        // ObsĹ‚uga przycisku dodawania
        findViewById<Button>(R.id.buttonAddGoal).setOnClickListener {
            showAddGoalDialog()
        }

        // ObsĹ‚uga przycisku powrotu do ekranu gĹ‚Ăłwnego
        findViewById<Button>(R.id.buttonBackToMain).setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java) // OdwoĹ‚anie gdzie wraca do wskazanego ekranu
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        rescheduleSavingsGoalReminders()
    }

    private fun loadGoals() {
        val db = AppDatabase.getDatabase(this)
        loadingOverlay.show("Ładowanie celów...")
        lifecycleScope.launch {
            try {
                val goals = withContext(Dispatchers.IO) {
                    db.savingsGoalDao().getGoalsForUser(userId)
                }
                if (goals.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptySaving.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptySaving.visibility = View.GONE
                }

                //Sortowanie-aktywne cele(niespeĹ‚nione) u gĂłry
                val sortedGoals = goals.sortedWith(
                    compareBy<SavingsGoal> { it.savedAmount >= it.targetAmount } //false -> aktywny, true -> zakoĹ„czony
                            .thenBy { it.endDate ?: Long.MAX_VALUE }
                ) //sortuj wg daty, jeĹ›li jest
                adapter.updateData(sortedGoals)
            } finally {
                loadingOverlay.hide()
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Long, String) -> Unit) {
        val locale = LocaleUtils.POLISH
        Locale.setDefault(locale)

        val cal = Calendar.getInstance(locale)
        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance(locale)
                selectedCal.set(year, month, dayOfMonth, 23, 59, 59)
                val timestamp = selectedCal.timeInMillis

                val formattedDate = SimpleDateFormat("dd.MM.yyyy", locale).format(selectedCal.time)
                onDateSelected(timestamp, formattedDate)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.firstDayOfWeek = Calendar.MONDAY
        dialog.show()
    }

    private fun setupMoneyInput(editText: EditText, requirePositive: Boolean = true) {
        val normalBorder = R.drawable.shape_search_border
        val errorBorder = R.drawable.shape_search_border_error

        //Blokada kropki i dopuszczenie tylko przecinka + max 2 miejsca po przecinku
        editText.filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, dend ->
                val newText = StringBuilder(dest)
                    .replace(dstart, dend, source.subSequence(start, end).toString())
                    .toString()

                val regex = Regex("^\\d+(?: ?\\d{0,3})*(?:,\\d{0,2})?$")
                if (newText.isEmpty() || newText.matches(regex)) null else ""
            }
        )

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = editText.text.toString().trim()

                if (text.isBlank()) {
                    editText.setBackgroundResource(errorBorder)
                    editText.error = "Podaj prawidĹ‚owÄ… kwotÄ™"
                    return@setOnFocusChangeListener
                }

                val normalizedText = when {
                    text.endsWith(",") -> text + "00"
                    text.contains(",") && text.substringAfter(",").length == 1 -> text + "0"
                    else -> text
                }

                val value = MoneyUtils.parseAmount(normalizedText)
                val isValid = if (requirePositive) {
                    value != null && value > 0
                } else {
                    value != null
                }

                if (isValid) {
                    editText.setText(MoneyFormatter.format(value!!))
                    editText.setSelection(editText.text.length)
                    editText.setBackgroundResource(normalBorder)
                    editText.error = null
                } else {
                    editText.setBackgroundResource(errorBorder)
                    editText.error = "Podaj prawidĹ‚owÄ… kwotÄ™"
                }
            } else {
                editText.setBackgroundResource(normalBorder)
            }
        }
    }

    // ---------------------- DODAWANIE CELU Z DATÄ„ ----------------------
    private fun showAddGoalDialog() {
        selectedEndDate = null
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val inputTitle = EditText(this).apply {
            hint = "Nazwa celu"
        }
        layout.addView(inputTitle)

        val inputAmount = EditText(this).apply {
            hint = "Kwota docelowa"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(inputAmount)
        setupMoneyInput(inputAmount)

        fun isTitleValid(): Boolean {
            return inputTitle.text.toString().trim().isNotEmpty()
        }

        fun updateFieldErrorState(field: EditText, isValid: Boolean) {
            if (isValid) {
                field.setBackgroundResource(R.drawable.shape_search_border)
            } else {
                field.setBackgroundResource(R.drawable.shape_search_border_error)
            }
        }

        inputTitle.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateFieldErrorState(inputTitle, isTitleValid())
            }
        }

        // đź”˝ Lista osĂłb z ustawieĹ„ (multi-choice)
        val buttonSelectPeople = Button(this)
        buttonSelectPeople.text = "Wybierz osoby (opcjonalnie)"
        layout.addView(buttonSelectPeople)

        var selectedPeople = mutableListOf<String>()

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SavingsActivity)
            val settings = withContext(Dispatchers.IO) {
                db.settingsDao().getSettingsForUser(userId)
            }
            val peopleArray = mutableListOf<String>()
            settings?.let {
                val jsonArray = JSONArray(it.peopleList)
                for (i in 0 until jsonArray.length()) {
                    peopleArray.add(jsonArray.getString(i))
                }
            }

            runOnUiThread {
                buttonSelectPeople.setOnClickListener {
                    val checked = BooleanArray(peopleArray.size)
                    AlertDialog.Builder(this@SavingsActivity)
                        .setTitle("Wybierz osoby (maks. 3)")
                        .setMultiChoiceItems(peopleArray.toTypedArray(), checked) { _,  which, ischecked ->
                            val person = peopleArray[which]
                            if (ischecked) {
                                if (selectedPeople.size < 3) selectedPeople.add(person)
                                else {
                                    Toast.makeText(this@SavingsActivity, "Maksymalnie 3 osoby!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                selectedPeople.remove(person)
                            }
                        }
                        .setPositiveButton("OK") { _, _ ->
                            if (selectedPeople.isEmpty()) {
                                buttonSelectPeople.text = "Wybierz osoby (opcjonalnie)"
                            } else {
                                buttonSelectPeople.text = "Wybrano: ${selectedPeople.joinToString(", ")}"
                            }
                        }
                        .setNegativeButton("Anuluj", null)
                        .show()
                }
            }
        }

        // Przycisk wyboru daty
        val buttonPickDate = Button(this)
        buttonPickDate.text = "Wybierz datÄ™ zakoĹ„czenia (opcjonalnie)"
        layout.addView(buttonPickDate)

        buttonPickDate.setOnClickListener {
            showDatePicker { timestamp, formatted ->
                selectedEndDate = timestamp
                buttonPickDate.text = "Data zakoĹ„czenia: $formatted"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Dodaj cel")
            .setView(layout)
            .setPositiveButton("Zapisz") { _, _ ->
                val title = inputTitle.text.toString()
                val amount = MoneyUtils.parseAmount(inputAmount.text.toString())
                if (title.isNotBlank() && amount != null && amount > 0) {
                    launchBlockingAction("Zapisywanie celu...") {
                        val db = AppDatabase.getDatabase(this@SavingsActivity)
                        val goal = SavingsGoal(
                            userId = userId,
                            title = title,
                            targetAmount = amount,
                            endDate = selectedEndDate,
                            sharedWith = if (selectedPeople.isEmpty()) null else selectedPeople.joinToString(", ")
                        )
                        val localId = withContext(Dispatchers.IO) {
                            db.savingsGoalDao().insert(goal).toInt()
                        }
                        val localGoal = goal.copy(id = localId)

                        val supabaseUid = Prefs.getSupabaseUid(this@SavingsActivity)
                        if (supabaseUid.isNullOrBlank()) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                    operation = SyncConstants.OP_INSERT,
                                    localId = localId,
                                    remoteId = null,
                                    payloadJson = Json.encodeToString(SavingsGoal.serializer(), localGoal)
                                )
                            )
                            WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                        } else {
                            try {
                                val remoteId = SavingsRemoteRepository.insertGoal(
                                    supabaseUid,
                                    goal.copy(id = localId)
                                )
                                db.savingsGoalDao()
                                    .update(goal.copy(id = localId, remoteId = remoteId))
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                        operation = SyncConstants.OP_INSERT,
                                        localId = localId,
                                        remoteId = null,
                                        payloadJson = Json.encodeToString(
                                            SavingsGoal.serializer(),
                                            goal.copy(id = localId)
                                        )
                                    )
                                )
                                WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                            }
                        }
                        if (Prefs.isNotificationsEnabled(this@SavingsActivity)) {
                            SavingsGoalAlarmScheduler.cancelAllReminders(this@SavingsActivity, localId)
                            SavingsGoalAlarmScheduler.scheduleAllRemindersForGoal(
                                this@SavingsActivity,
                                goal.copy(id = localId)
                            )
                        }
                        selectedEndDate = null
                    }
                } else {
                    Toast.makeText(this, "Podaj poprawne dane", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Edycja celu
    private fun showEditGoalDialog(goal: SavingsGoal) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL

        val inputTitle = EditText(this)
        inputTitle.setText(goal.title)
        layout.addView(inputTitle)

        val inputAmount = EditText(this)
        inputAmount.setText(MoneyFormatter.format(goal.targetAmount))
        inputAmount.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        layout.addView(inputAmount)
        setupMoneyInput(inputAmount)

        // đź”˝ Przycisk do wyboru osĂłb (zamiast spinnera)
        val buttonSelectPeople = Button(this)
        layout.addView(buttonSelectPeople)

        //Lista osĂłb juĹĽ wybranych
        val selectedPeople = mutableSetOf<String>()
        if (!goal.sharedWith.isNullOrBlank()) {
            selectedPeople.addAll(goal.sharedWith.split(",").map { it.trim() })
        }

        //Ustaw wstÄ™pny tekst przycisku
        buttonSelectPeople.text = if (selectedPeople.isEmpty()) {
            "Wybierz osoby (opcjonalnie)"
        } else {
            "Wybrano: ${selectedPeople.joinToString(", ")}"
        }

        //Wczytaj osoby z ustawieĹ„
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SavingsActivity)
            val settings = withContext(Dispatchers.IO) {
                db.settingsDao().getSettingsForUser(userId)
            }
            val peopleArray = mutableListOf<String>()
            settings?.let {
                val jsonArray = JSONArray(it.peopleList)
                for (i in 0 until jsonArray.length()) {
                    peopleArray.add(jsonArray.getString(i))
                }
            }

            runOnUiThread {
                buttonSelectPeople.setOnClickListener {
                    val checked = BooleanArray(peopleArray.size) { index ->
                        selectedPeople.contains(peopleArray[index])
                    }

                    AlertDialog.Builder(this@SavingsActivity)
                        .setTitle("Wybierz osoby (maks. 3)")
                        .setMultiChoiceItems(
                            peopleArray.toTypedArray(),
                            checked
                        ) { _, which, isChecked ->
                            val person = peopleArray[which]
                            if (isChecked) {
                                if (selectedPeople.size < 3 || selectedPeople.contains(person)) {
                                    if (!selectedPeople.contains(person)) selectedPeople.add(person)
                                } else {
                                    Toast.makeText(
                                        this@SavingsActivity,
                                        "Maksymalnie 3 osoby!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                selectedPeople.remove(person)
                            }
                        }
                        .setPositiveButton("OK") { _, _ ->
                            buttonSelectPeople.text = if (selectedPeople.isEmpty()) {
                                "Wybierz osoby (opcjonalnie)"
                            } else {
                                "Wybrano: ${selectedPeople.joinToString(", ")}"
                            }
                        }
                        .setNegativeButton("Anuluj", null)
                        .show()
                }
            }
        }

        // Przycisk wyboru daty
        val buttonPickDate = Button(this)
        var endDate: Long? = goal.endDate
        if (goal.endDate != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = goal.endDate }
            buttonPickDate.text = "Data zakoĹ„czenia: ${cal.get(Calendar.DAY_OF_MONTH)}.${cal.get(Calendar.MONTH) + 1}.${cal.get(Calendar.YEAR)}"
        } else {
            buttonPickDate.text = "Wybierz datÄ™ zakoĹ„czenia (opcjonalnie)"
        }
        layout.addView(buttonPickDate)

        buttonPickDate.setOnClickListener {
            showDatePicker { timestamp, formatted ->
                endDate = timestamp
                buttonPickDate.text = "Data zakoĹ„czenia: $formatted"
            }
        }
        val buttonRemoveDate = Button(this).apply {
            text = "âťŚ UsuĹ„ termin"
            visibility = if (goal.endDate != null) View.VISIBLE else View.GONE
        }
        layout.addView(buttonRemoveDate)
        buttonRemoveDate.setOnClickListener {
            endDate = null
            buttonPickDate.text = "Wybierz datÄ™ zakoĹ„czenia (opcjonalnie)"
            buttonRemoveDate.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Edytuj cel")
            .setView(layout)
            .setPositiveButton("Zapisz") { _, _ ->
                val newTitle = inputTitle.text.toString()
                val newAmount = MoneyUtils.parseAmount(inputAmount.text.toString())
                if (newTitle.isNotBlank() && newAmount != null && newAmount > 0) {
                    val db = AppDatabase.getDatabase(this)
                    launchBlockingAction("Aktualizowanie celu...") {
                        val updateGoal = goal.copy(
                                title = newTitle,
                                targetAmount = newAmount,
                                endDate = endDate,
                                sharedWith = if (selectedPeople.isEmpty()) null else selectedPeople.joinToString(", ")
                            )
                        //1. Zapis lokalny zawsze
                        withContext(Dispatchers.IO) {
                            db.savingsGoalDao().update(updateGoal)
                        }
                        val supabaseUid = Prefs.getSupabaseUid(this@SavingsActivity)
                        //2. Sync albo kolejka
                        if (supabaseUid.isNullOrBlank()) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                    operation = if (updateGoal.remoteId == null)
                                        SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE,
                                    localId = updateGoal.id,
                                    remoteId = updateGoal.remoteId,
                                    payloadJson = Json.encodeToString(SavingsGoal.serializer(), updateGoal)
                                )
                            )
                            WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                        } else {
                            try {
                                if (updateGoal.remoteId != null) {
                                    SavingsRemoteRepository.updateGoal(
                                        updateGoal.remoteId!!,
                                        updateGoal
                                    )
                                } else {
                                    val remoteId = SavingsRemoteRepository.insertGoal(
                                        supabaseUid,
                                        updateGoal
                                    )
                                    db.savingsGoalDao().updateRemoteId(updateGoal.id, remoteId)
                                }
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                        operation = if (updateGoal.remoteId == null)
                                            SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE,
                                        localId = updateGoal.id,
                                        remoteId = updateGoal.remoteId,
                                        payloadJson = Json.encodeToString(
                                            SavingsGoal.serializer(),
                                            updateGoal
                                        )
                                    )
                                )
                                WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                            }
                        }
                        // Najpierw wyczyĹ›Ä‡ stare
                        SavingsGoalAlarmScheduler.cancelAllReminders(this@SavingsActivity, updateGoal.id)
                        // JeĹ›li jest endDate i cel nie osiÄ…gniÄ™ty -> ustaw nowe
                        if (Prefs.isNotificationsEnabled(this@SavingsActivity)) {
                            SavingsGoalAlarmScheduler.scheduleAllRemindersForGoal(this@SavingsActivity, updateGoal)
                        }
                        loadGoals()
                    }
                } else {
                    Toast.makeText(this, "Podaj poprawne dane", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Dodawanie kwoty
    private fun showAddAmountDialog(goal: SavingsGoal) {
        // đź§± Tworzymy layout do dialogu
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        // đź§ľ Pole kwoty
        val inputAmount = EditText(this).apply {
            hint = "Kwota do dodania"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(inputAmount)
        setupMoneyInput(inputAmount)

        // đź‘Ą Spinner â€“ kto wpĹ‚aca
        val spinner = Spinner(this)
        layout.addView(spinner)

        //ZaĹ‚aduj listÄ™ osĂłb zwiÄ…zane z tym celem
        lifecycleScope.launch {
            val peopleList = mutableListOf("Tylko ja")

            //JeĹ›li cel jest wspĂłĹ‚dzielony
            if (!goal.sharedWith.isNullOrBlank()) {
                val sharedPeople = goal.sharedWith.split(",").map { it.trim() }
                peopleList.addAll(sharedPeople)
            }

            val adapterSpinner = ArrayAdapter(
                this@SavingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                peopleList
            )
            runOnUiThread {
                spinner.adapter = adapterSpinner
            }
        }

        // đźŞź Budujemy dialog
        AlertDialog.Builder(this)
            .setTitle("Dodaj oszczÄ™dnoĹ›ci")
            .setView(layout)
            .setPositiveButton("Dodaj") { _, _ ->
                val amount = MoneyUtils.parseAmount(inputAmount.text.toString())
                val selectedPerson = spinner.selectedItem?.toString() ?: "Tylko ja"

                if (amount != null && amount > 0) {
                    val db = AppDatabase.getDatabase(this)
                    launchBlockingAction("Zapisywanie wpłaty...") {
                        // Ile byĹ‚o przed?
                        val before = goal.savedAmount
                        // Ile po dodaniu?
                        val after = before + amount
                        // Czy wĹ‚aĹ›nie osiagnelismy 100%
                        val reachedNow = before < goal.targetAmount && after >= goal.targetAmount
                        // Czy powiadomienie moĹĽe zostaÄ‡ wysĹ‚ane?
                        val shouldSendNotification = reachedNow && !goal.notificationCompletedSent
                        //Zaktualizuj kwote w SavingGoal
                        val updatedGoal = goal.copy(savedAmount = after, notificationCompletedSent = goal.notificationCompletedSent || reachedNow)
                        // JeĹ›li cel osiÄ…gniÄ™to Teraz -> wyĹ›lik powiadomienie systemowe
                        if (shouldSendNotification && Prefs.isNotificationsEnabled(this@SavingsActivity)) {
                            // Anuluj istniejÄ…ce przypomnienia, nie maja sensu gdy cel zrobiony
                            SavingsGoalAlarmScheduler.cancelAllReminders(this@SavingsActivity, goal.id)
                            val title = "Cel osiÄ…gniÄ™ty!"
                            val text = "Gratulacje! â€ž${goal.title}â€ť zostaĹ‚ w 100% zrealizowany. Cel: ${MoneyFormatter.formatWithCurrency(goal.targetAmount)} đźŽ‰"
                            // Systemowe powiadomeinia
                            NotificationHelper.notifySavings(this@SavingsActivity, updatedGoal.id * 10000 + 999 /*unikalne ID*/, title, text)
                        }

                        val contribution = Contribution(
                            userId = userId,
                            goalId = goal.id,
                            personName = selectedPerson,
                            amount = amount
                        )
                        //1. Zapis lokalny zawsze
                        val localContributionId = withContext(Dispatchers.IO) {
                            db.contributionDao().insert(contribution).toInt()
                        }
                        withContext(Dispatchers.IO) {
                            db.savingsGoalDao().update(updatedGoal)
                        }

                        val supabaseUid = Prefs.getSupabaseUid(this@SavingsActivity)
                        val remoteGoalId = goal.remoteId
                        //2. Sync albo kolejka
                        if (supabaseUid.isNullOrBlank() || remoteGoalId == null) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_CONTRIBUTION,
                                    operation = SyncConstants.OP_INSERT,
                                    localId = localContributionId,
                                    remoteId = null,
                                    payloadJson = Json.encodeToString(Contribution.serializer(), contribution.copy(id = localContributionId))
                                )
                            )
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                    operation = if (updatedGoal.remoteId == null)
                                        SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE,
                                    localId = updatedGoal.id,
                                    remoteId = updatedGoal.remoteId,
                                    payloadJson = Json.encodeToString(SavingsGoal.serializer(), updatedGoal)
                                )
                            )
                            WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                        } else {
                            // Supabase - contribution
                            var shouldScheduleSync = false
                            try {
                                val remoteContributionId = SavingsRemoteRepository.insertContribution(
                                    supabaseUid,
                                    remoteGoalId,
                                    contribution
                                )
                                db.contributionDao().updateRemoteId(localContributionId, remoteContributionId)
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_CONTRIBUTION,
                                        operation = SyncConstants.OP_INSERT,
                                        localId = localContributionId,
                                        remoteId = null,
                                        payloadJson = Json.encodeToString(
                                            Contribution.serializer(),
                                            contribution.copy(id = localContributionId)
                                        )
                                    )
                                )
                                shouldScheduleSync = true
                            }
                            try {
                                SavingsRemoteRepository.updateGoal(remoteGoalId, updatedGoal)
                            } catch (e: Exception) {
                                PendingSyncHelper.enqueueOrMerge(
                                    db.pendingSyncDao(),
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                        operation = SyncConstants.OP_UPDATE,
                                        localId = updatedGoal.id,
                                        remoteId = updatedGoal.remoteId,
                                        payloadJson = Json.encodeToString(
                                            SavingsGoal.serializer(),
                                            updatedGoal
                                        )
                                    )
                                )
                                shouldScheduleSync = true
                            }
                            if (shouldScheduleSync) {
                                WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                            }
                        }
                        //OdĹ›wieĹĽ listÄ™ celĂłw
                        loadGoals()
                    }
                } else {
                    Toast.makeText(this, "Podaj poprawnÄ… kwotÄ™", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    // WypĹ‚acanie kwoty
    private fun showWithdrawDialog(goal: SavingsGoal) {
        // đź§± Tworzymy layout do dialogu
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        // đź§ľ Pole kwoty
        val inputAmount = EditText(this).apply {
            hint = "Kwota do wypĹ‚aty"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(inputAmount)
        setupMoneyInput(inputAmount)

        // đź‘Ą Spinner â€“ kto wpĹ‚aca
        val spinner = Spinner(this)
        layout.addView(spinner)
        //ZaĹ‚aduj listÄ™ osĂłb zwiÄ…zane z tym celem
        lifecycleScope.launch {
            val peopleList = mutableListOf("Tylko ja")
            //JeĹ›li cel jest wspĂłĹ‚dzielony
            if (!goal.sharedWith.isNullOrBlank()) {
                val sharedPeople = goal.sharedWith.split(",").map { it.trim() }
                peopleList.addAll(sharedPeople)
            }

            val adapterSpinner = ArrayAdapter(
                this@SavingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                peopleList
            )
            runOnUiThread {
                spinner.adapter = adapterSpinner
            }
        }
        // đźŞź Budujemy dialog
        AlertDialog.Builder(this)
            .setTitle("WypĹ‚aÄ‡ Ĺ›rodki")
            .setView(layout)
            .setPositiveButton("WypĹ‚aÄ‡") { _, _ ->
                val amount = MoneyUtils.parseAmount(inputAmount.text.toString())
                val selectedPerson = spinner.selectedItem.toString()
                if (amount == null || amount <= 0) {
                    AlertDialog.Builder(this)
                        .setTitle("Niepoprawna kwota")
                        .setMessage("Podaj kwotÄ™ wiÄ™kszÄ… od zera.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@setPositiveButton
                }
                if (amount > goal.savedAmount) {
                    AlertDialog.Builder(this)
                        .setTitle("Brak Ĺ›rodkĂłw")
                        .setMessage("Nie moĹĽesz wypĹ‚aciÄ‡ $amount zĹ‚. \n\n" +
                                "DostÄ™pne Ĺ›rodki: ${MoneyFormatter.formatWithCurrency(goal.savedAmount)}")
                        .setPositiveButton("OK", null)
                        .show()
                    Toast.makeText(this,"Podaj poprawnÄ… kwotÄ™", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                launchBlockingAction("Zapisywanie wypłaty...") {
                    val db = AppDatabase.getDatabase(this@SavingsActivity)
                    val updatedGoal = goal.copy(savedAmount = goal.savedAmount - amount)

                    val contribution = Contribution(
                        userId = userId,
                        goalId = goal.id,
                        personName = selectedPerson,
                        amount = -amount
                    )
                    //1. Zapis lokalny zawsze
                    val localContributionId = withContext(Dispatchers.IO) {
                        db.contributionDao().insert(contribution).toInt()
                    }
                    withContext(Dispatchers.IO) {
                        db.savingsGoalDao().update(updatedGoal)
                    }

                    val supabaseUid = Prefs.getSupabaseUid(this@SavingsActivity)
                    val remoteGoalId = goal.remoteId
                    //2. Sync albo kolejka
                    if (supabaseUid.isNullOrBlank() || remoteGoalId == null) {
                        PendingSyncHelper.enqueueOrMerge(
                            db.pendingSyncDao(),
                            PendingSync(
                                entityType = SyncConstants.ENTITY_CONTRIBUTION,
                                operation = SyncConstants.OP_INSERT,
                                localId = localContributionId,
                                remoteId = null,
                                payloadJson = Json.encodeToString(Contribution.serializer(), contribution.copy(id = localContributionId))
                            )
                        )
                        PendingSyncHelper.enqueueOrMerge(
                            db.pendingSyncDao(),
                            PendingSync(
                                entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                operation = if (updatedGoal.remoteId == null)
                                    SyncConstants.OP_INSERT else SyncConstants.OP_UPDATE,
                                localId = updatedGoal.id,
                                remoteId = updatedGoal.remoteId,
                                payloadJson = Json.encodeToString(SavingsGoal.serializer(), updatedGoal)
                            )
                        )
                        WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                    } else {
                        // Supabase - contribution
                        var shouldScheduleSync = false
                        try {
                            val remoteContributionId = SavingsRemoteRepository.insertContribution(
                                supabaseUid,
                                remoteGoalId,
                                contribution
                            )
                            db.contributionDao().updateRemoteId(localContributionId, remoteContributionId)
                        } catch (e: Exception) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_CONTRIBUTION,
                                    operation = SyncConstants.OP_INSERT,
                                    localId = localContributionId,
                                    remoteId = null,
                                    payloadJson = Json.encodeToString(
                                        Contribution.serializer(),
                                        contribution.copy(id = localContributionId)
                                    )
                                )
                            )
                            shouldScheduleSync = true
                        }
                        try {
                            // Supabase - update
                            SavingsRemoteRepository.updateGoal(remoteGoalId, updatedGoal)
                        } catch (e: Exception) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                    operation = SyncConstants.OP_UPDATE,
                                    localId = updatedGoal.id,
                                    remoteId = updatedGoal.remoteId,
                                    payloadJson = Json.encodeToString(
                                        SavingsGoal.serializer(),
                                        updatedGoal
                                    )
                                )
                            )
                            shouldScheduleSync = true
                        }
                        if (shouldScheduleSync) {
                            WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                        }
                    }
                    loadGoals()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Sprawdzanie historii wpĹ‚at do danego celu
    private fun rescheduleSavingsGoalReminders() {
        if (userId == -1 || !Prefs.isNotificationsEnabled(this)) return

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SavingsActivity)
            val goals = withContext(Dispatchers.IO) {
                db.savingsGoalDao().getGoalsForUser(userId)
            }

            goals.forEach { goal ->
                SavingsGoalAlarmScheduler.cancelAllReminders(this@SavingsActivity, goal.id)
                SavingsGoalAlarmScheduler.scheduleAllRemindersForGoal(this@SavingsActivity, goal)
            }
        }
    }

    private fun launchBlockingAction(message: String, block: suspend () -> Unit) {
        loadingDialog.show(message)
        lifecycleScope.launch {
            try {
                block()
            } finally {
                loadingDialog.hide()
            }
        }
    }

    private fun showContributionsDialog(goal: SavingsGoal) {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val contributions = withContext(Dispatchers.IO) {
                db.contributionDao().getContributionsForGoal(goal.id)
            }
            val grouped = contributions.groupBy { it.personName }
            val summary = grouped.entries.joinToString("\n") { (person, list) ->
                val sum = list.sumOf { it.amount }
                "â€˘ $person: ${MoneyFormatter.formatWithCurrency(abs(sum))}"
            }
            runOnUiThread {
                if (contributions.isEmpty()) {
                    AlertDialog.Builder(this@SavingsActivity)
                        .setTitle("Historia wpĹ‚at")
                        .setMessage("Brak wpĹ‚at dla tego celu.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val history = contributions.joinToString("\n\n") { c ->
                        val icon = if (c.amount >= 0) "+" else "-"
                        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", LocaleUtils.POLISH)
                            .format(Date(c.timestamp))
                        "$icon ${c.personName}: ${MoneyFormatter.formatWithCurrency(abs(c.amount))} ($date)"
                    }
                    val container = LinearLayout(this@SavingsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 20, 40, 20)
                    }
                    // Podsumowanie
                    val summaryTitle = TextView(this@SavingsActivity).apply {
                        text = "đź“Š PODSUMOWANIE:"
                        textSize = 15f
                        gravity = Gravity.CENTER
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 0, 0, 8)
                        setTextColor(
                            MaterialColors.getColor(
                                this@SavingsActivity,
                                android.R.attr.textColorPrimary,
                                getColor(android.R.color.black)
                            )
                        )
                    }
                    val summaryText = TextView(this@SavingsActivity).apply {
                        text = summary
                        textSize = 14f
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        setTextColor(
                            MaterialColors.getColor(
                                this@SavingsActivity,
                                android.R.attr.textColorPrimary,
                                getColor(android.R.color.black)
                            )
                        )
                    }
                    // Separator
                    val separator = TextView(this@SavingsActivity).apply {
                        text = "â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€"
                        setTextColor(getColor(android.R.color.darker_gray))
                        gravity = Gravity.CENTER
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 8, 0, 8)
                    }
                    // Historia
                    val historyTitle = TextView(this@SavingsActivity).apply {
                        text = "đź“ś HISTORIA:"
                        textSize = 15f
                        gravity = Gravity.CENTER
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 8, 0, 8)
                        setTextColor(
                            MaterialColors.getColor(
                                this@SavingsActivity,
                                android.R.attr.textColorPrimary,
                                getColor(android.R.color.black)
                            )
                        )
                    }
                    val historyText = TextView(this@SavingsActivity).apply {
                        text = history
                        textSize = 14f
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        setTextColor(
                            MaterialColors.getColor(
                                this@SavingsActivity,
                                android.R.attr.textColorPrimary,
                                getColor(android.R.color.black)
                            )
                        )
                    }
                    container.addView(summaryTitle)
                    container.addView(summaryText)
                    container.addView(separator)
                    container.addView(historyTitle)
                    container.addView(historyText)
                    AlertDialog.Builder(this@SavingsActivity)
                        .setTitle("Historia wpĹ‚at - ${goal.title}")
                        .setView(container)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun showContributionsDialogModern(goal: SavingsGoal) {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val contributions = withContext(Dispatchers.IO) {
                db.contributionDao().getContributionsForGoal(goal.id)
            }

            runOnUiThread {
                if (contributions.isEmpty()) {
                    AlertDialog.Builder(this@SavingsActivity)
                        .setTitle("Historia wpĹ‚at")
                        .setMessage("Brak wpĹ‚at dla tego celu.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@runOnUiThread
                }

                val grouped = contributions.groupBy { it.personName }
                val textColor = MaterialColors.getColor(
                    this@SavingsActivity,
                    android.R.attr.textColorPrimary,
                    getColor(android.R.color.black)
                )
                val positiveColor = MaterialColors.getColor(
                    this@SavingsActivity,
                    com.google.android.material.R.attr.colorPrimary,
                    getColor(android.R.color.holo_blue_dark)
                )
                val negativeColor = MaterialColors.getColor(
                    this@SavingsActivity,
                    com.google.android.material.R.attr.colorError,
                    getColor(android.R.color.holo_red_dark)
                )
                val density = resources.displayMetrics.density
                fun Int.dp(): Int = (this * density).toInt()

                val scrollView = ScrollView(this@SavingsActivity).apply {
                    isFillViewport = true
                }
                val container = LinearLayout(this@SavingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24.dp(), 12.dp(), 24.dp(), 12.dp())
                }
                scrollView.addView(container)

                val summaryTitle = TextView(this@SavingsActivity).apply {
                    text = "Podsumowanie:"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textColor)
                    setPadding(0, 0, 0, 8.dp())
                }
                container.addView(summaryTitle)

                grouped.forEach { (person, list) ->
                    val sum = list.sumOf { it.amount }
                    val sign = if (sum >= 0) "+" else "-"
                    val summaryRow = TextView(this@SavingsActivity).apply {
                        text = "$person: $sign${MoneyFormatter.formatWithCurrency(abs(sum))}"
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(if (sum < 0) negativeColor else textColor)
                        setPadding(0, 0, 0, 6.dp())
                    }
                    container.addView(summaryRow)
                }

                val separator = View(this@SavingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dp()
                    ).apply {
                        topMargin = 10.dp()
                        bottomMargin = 10.dp()
                    }
                    setBackgroundColor(getColor(android.R.color.darker_gray))
                }
                container.addView(separator)

                val historyTitle = TextView(this@SavingsActivity).apply {
                    text = "Historia:"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textColor)
                    setPadding(0, 0, 0, 8.dp())
                }
                container.addView(historyTitle)

                contributions.forEach { contribution ->
                    val date = SimpleDateFormat("dd.MM.yyyy", LocaleUtils.POLISH)
                        .format(Date(contribution.timestamp))
                    val sign = if (contribution.amount < 0) "-" else "+"
                    val historyRow = TextView(this@SavingsActivity).apply {
                        text = "${contribution.personName}: $sign${MoneyFormatter.formatWithCurrency(abs(contribution.amount))} â€˘ $date"
                        textSize = 14f
                        setTextColor(if (contribution.amount < 0) negativeColor else positiveColor)
                        setPadding(0, 2.dp(), 0, 2.dp())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = 6.dp()
                        }
                    }
                    container.addView(historyRow)
                }

                AlertDialog.Builder(this@SavingsActivity)
                    .setTitle("Historia wpĹ‚at - ${goal.title}")
                    .setView(scrollView)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // Usuwanie celu
    private fun deleteGoal(goal: SavingsGoal) {
        AlertDialog.Builder(this)
            .setTitle("UsuĹ„ cel")
            .setMessage("Czy na pewno chcesz usunÄ…Ä‡ cel \"${goal.title}\"?")
            .setPositiveButton("Tak") { _, _ ->
                // Najpierw anuluj wszystkie alarmy dla tego celu
                SavingsGoalAlarmScheduler.cancelAllReminders(this@SavingsActivity, goal.id)
                val db = AppDatabase.getDatabase(this)
                launchBlockingAction("Usuwanie celu...") {
                    val shouldQueueDelete = try {
                        if (goal.remoteId != null) {
                            SavingsRemoteRepository.deleteGoal(goal.remoteId!!)
                            false
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        true
                    }
                    if (shouldQueueDelete) {
                        PendingSyncHelper.enqueueOrMerge(
                            db.pendingSyncDao(),
                            PendingSync(
                                entityType = SyncConstants.ENTITY_SAVINGS_GOAL,
                                operation = SyncConstants.OP_DELETE,
                                localId = goal.id,
                                remoteId = goal.remoteId,
                                payloadJson = Json.encodeToString(SavingsGoal.serializer(), goal)
                            )
                        )
                        WorkSchedulerSupabase.scheduleSupabaseSync(this@SavingsActivity)
                    }
                    withContext(Dispatchers.IO) {
                        db.contributionDao().deleteByGoal(goal.id)
                        db.savingsGoalDao().delete(goal)
                    }
                    loadGoals()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
}
