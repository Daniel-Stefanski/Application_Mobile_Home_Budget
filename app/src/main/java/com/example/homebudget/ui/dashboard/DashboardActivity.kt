package com.example.homebudget.ui.dashboard

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.icu.util.Calendar
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.ui.addexpense.AddExpenseActivity
import com.example.homebudget.ui.billsplanner.BillsPlannerActivity
import com.example.homebudget.ui.history.HistoryActivity
import com.example.homebudget.R
import com.example.homebudget.ui.savings.SavingsActivity
import com.example.homebudget.ui.settings.SettingsActivity
import com.example.homebudget.ui.statistics.StatisticsActivity
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.dto.CategorySum
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.remote.repository.MonthlyBudgetRemoteRepository
import com.example.homebudget.data.sync.DashboardSyncManager
import com.example.homebudget.data.sync.SyncConstants
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import com.example.homebudget.notifications.scheduler.DashboardBudgetAlarmScheduler
import com.example.homebudget.utils.color.ColorUtils
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.settings.Prefs
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.collections.iterator

//DashboardActivity.kt – główny ekran po zalogowaniu (nawigacja do pozostałych sekcji).
class DashboardActivity : AppCompatActivity() {

    private var userId: Int = -1
    private var firstLoadDone = false
    private lateinit var textWelcome: TextView
    private lateinit var pieChart: PieChart
    private lateinit var textSummary: TextView
    private lateinit var categoriesContainer: LinearLayout
    private lateinit var buttonAddExpense: Button
    private lateinit var buttonSetBudget: Button
    private lateinit var buttonHistory: Button
    private lateinit var buttonSavings: Button
    private lateinit var buttonPrevMonth: ImageButton
    private lateinit var textMonthYear: TextView
    private lateinit var buttonNextMonth: ImageButton
    private lateinit var buttonBillsPlanner: Button
    private lateinit var buttonStatistics: Button
    private lateinit var buttonSettings: Button
    private var selectedYear = LocalDate.now().year
    private var selectedMonth = LocalDate.now().monthValue

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Wczytanie i zastosowanie wybranego motywu aplikacji
        when (Prefs.getAppTheme(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        // Dopiero layout
        setContentView(R.layout.activity_dashboard)

        textWelcome = findViewById(R.id.textWelcome)
        pieChart = findViewById(R.id.pieChart)
        textSummary = findViewById(R.id.textSummary)
        categoriesContainer = findViewById(R.id.categoriesContainer)
        buttonAddExpense = findViewById(R.id.buttonAddExpense)
        buttonSetBudget = findViewById(R.id.buttonSetBudget)
        buttonHistory = findViewById(R.id.buttonHistory)
        buttonSavings = findViewById(R.id.buttonSavings)
        buttonPrevMonth = findViewById(R.id.buttonPrevMonth)
        textMonthYear = findViewById(R.id.textMonthYear)
        buttonNextMonth = findViewById(R.id.buttonNextMonth)
        buttonBillsPlanner = findViewById(R.id.buttonBillsPlanner)
        buttonStatistics = findViewById(R.id.buttonStatistics)
        buttonSettings = findViewById(R.id.buttonSettings)

        userId = Prefs.getUserId(this)

        //Przywitanie użytkownika
        if (userId != -1) {
            lifecycleScope.launch {
                val db = AppDatabase.Companion.getDatabase(this@DashboardActivity)
                val user = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(userId)
                }
                withContext(Dispatchers.Main) {
                    if (user != null) {
                        textWelcome.text = "Witaj, ${user.name} w aplikacji HomeBudget \uD83D\uDC4B"
                    } else {
                        textWelcome.text = "Witaj w aplikacji HomeBudget \uD83D\uDC4B"
                    }
                }
            }
        } else {
            textWelcome.text = "Witaj w aplikacji HomeBudget \uD83D\uDC4B"
        }

        //Automatyczne czyszczenie nieużywanych kolorów - max raz na 7 dni
        val lastCleanup = Prefs.getLastColorCleanup(this)
        val currentTime = System.currentTimeMillis()
        val sevenDaysMillis = 7 * 24 * 60 * 60 * 1000L
        if (currentTime - lastCleanup > sevenDaysMillis) {
            Log.d("DashboardActivity", "Czyszczenie kolorów wykonane")
            cleanupUnusedCategoryColors()
            Prefs.setLastColorCleanup(this, currentTime)
        } else {
            Log.d("DashboardActivity", "Czyszczenie pominięte - ostatnie było niedawno")
        }

        setupChartAppearance()

        // Pobranie aktualnego miesiąca i roku
        val today = LocalDate.now()
        val month = today.month.getDisplayName(TextStyle.FULL, Locale("pl"))
        val year = today.year
        textMonthYear.text = "${month.replaceFirstChar { it.uppercase() }} $year"

        // Obsługa kliknięcia poprzedni miesiąc
        updateMonthLabel()
        buttonPrevMonth.setOnClickListener {
            selectedMonth--
            if (selectedMonth < 1) {
                selectedMonth = 12
                selectedYear--
            }
            updateMonthLabel()
            updateMonthButtons()
            loadAndShowData()
        }

        // Obsługa kliknięcia następny miesiąc
        buttonNextMonth.setOnClickListener {
            val today = LocalDate.now()
            // Blokada identyczna jak w Statistics
            if (selectedYear == today.year && selectedMonth == today.monthValue) {
                return@setOnClickListener
            }
            selectedMonth++
            if (selectedMonth > 12) {
                selectedMonth = 1
                selectedYear++
            }

            updateMonthLabel()
            updateMonthButtons()
            loadAndShowData()
        }

        // Obsługa kliknięcia "Dodaj wydatek"
        buttonAddExpense.setOnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            startActivity(intent)
        }

        // Obsługa kliknięcia "Ustaw budżet"
        buttonSetBudget.setOnClickListener {
            showSetBudgetDialog()
        }

        // Obsługa kliknięcia "Historia wydatków"
        buttonHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        // Obsługa kliknięcia "Cele oszczędnościowe"
        buttonSavings.setOnClickListener {
            val intent = Intent(this, SavingsActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        // Obsługa kliknięcia "Planer rachunków"
        buttonBillsPlanner.setOnClickListener {
            val intent = Intent(this, BillsPlannerActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        //Obsługa kliknięcia "Statystyki"
        buttonStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            intent.putExtra("USER_ID",userId)
            startActivity(intent)
        }

        // Obsługa kliknięcia "Ustawienia"
        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
        DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(this)
    }

    override fun onStart() {
        super.onStart()
        if (firstLoadDone) return
        firstLoadDone = true
        lifecycleScope.launch {
            //1. Najpierw pokaż dane lokalnie (nawet stare)
            loadAndShowData()
            //2. Następnie sync z Supabase do Room (w tle)
            withContext(Dispatchers.IO) {
                DashboardSyncManager.sync(this@DashboardActivity)
            }
            WorkSchedulerSupabase.scheduleSupabaseSync(this@DashboardActivity)
            //3. Odświeżenie UI po sync
            loadAndShowData()
        }
    }

    private fun updateMonthLabel() {
        val monthName = Month.of(selectedMonth)
            .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl-PL"))
            .replaceFirstChar { it.uppercase() }
        textMonthYear.text = "$monthName $selectedYear"

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        textMonthYear.setTextColor(if (isDarkMode) Color.parseColor("#F1F1F1") else Color.parseColor("#202020"))
    }

    private fun updateMonthButtons() {
        val today = LocalDate.now()
        val isCurrentMonth = selectedYear == today.year && selectedMonth == today.monthValue

        if (isCurrentMonth) {
            buttonNextMonth.isEnabled = false
            buttonNextMonth.alpha = 0.3f
        } else {
            buttonNextMonth.isEnabled = true
            buttonNextMonth.alpha = 1f
        }
    }

    private fun cleanupUnusedCategoryColors() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.Companion.getDatabase(this@DashboardActivity)
            val settingsDao = db.settingsDao()
            val expenseDao = db.expenseDao()

            val settings = settingsDao.getSettingsForUser(userId)
            if (settings == null) return@launch

            //Kategorie zapisane w ustawieniach
            val activeCategories = try {
                JSONObject(settings.categories)
            } catch (e: Exception) {
                JSONObject()
            }

            val categoryList = mutableSetOf<String>()
            val keys = activeCategories.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (name.isNotBlank()) categoryList.add(name)
            }

            //Kategorie użyte w wydatkach
            val usedCategories = expenseDao.getAllExpensesForUser(userId)
                .mapNotNull { it.category }
                .filter { it.isNotBlank() }

            categoryList.addAll(usedCategories)

            //Kolory z JSON-a
            val categoryColorsJson = try {
                JSONObject(settings.categoryColors)
            } catch (e: Exception) {
                JSONObject()
            }

            val toRemove = mutableListOf<String>()
            val colorkeys = categoryColorsJson.keys()
            while (colorkeys.hasNext()) {
                val key = colorkeys.next()
                if (!categoryList.contains(key)) {
                    toRemove.add(key)
                }
            }

            //Usuń nieużywane
            if (toRemove.isNotEmpty()) {
                toRemove.forEach { categoryColorsJson.remove(it) }
                settingsDao.updateSettings(
                    userId = settings.userId,
                    categories = settings.categories,
                    currency = settings.currency,
                    period = settings.period,
                    savingsGoal = settings.savingsGoal,
                    categoryColors = categoryColorsJson.toString(),
                    peopleList = settings.peopleList,
                    defaultCategory = settings.defaultCategory,
                    defaultPaymentMethod = settings.defaultPaymentMethod
                )
            }
        }
    }

    private fun setupChartAppearance() {
        val isDarkMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val holeColor = if (isDarkMode) Color.parseColor("#1C1C1E") else Color.WHITE
        val labelColor = if (isDarkMode) Color.WHITE else Color.BLACK

        // Pobierz kolor tła z aktualnego motywu (jasny/ciemny)
        val backgroundColor = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, backgroundColor, true)

        pieChart.setUsePercentValues(true) // teraz procenty zamiast kwoty
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(backgroundColor.data)
        pieChart.setTransparentCircleColor(holeColor)
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 61f
        pieChart.setDrawCenterText(true)
        pieChart.description.isEnabled = false
        pieChart.legend.isWordWrapEnabled = true
        pieChart.setEntryLabelColor(labelColor)
        pieChart.setCenterTextColor(labelColor)
        pieChart.setEntryLabelTextSize(12f)
        //Wyłącz domyślną legendę (mini legendę pod wykresem)
        pieChart.legend.isEnabled = false
    }

    private fun loadAndShowData() {
        // 🔍 Sprawdzenie motywu
        val isDarkMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(this@DashboardActivity)
            val settingsDao = db.settingsDao()
            val expenseDao = db.expenseDao()
            val monthlyBudgetDao = db.monthlyBudgetDao()

            val userId = Prefs.getUserId(this@DashboardActivity)
            if (userId == -1) {
                textSummary.text = "Brak zalogowanego użytkownika"
                pieChart.clear()
                categoriesContainer.removeAllViews()
                return@launch
            }

            val settings: Settings? = withContext(Dispatchers.IO) {
                settingsDao.getSettingsForUser(userId)
            }

            if (settings == null) {
                textSummary.text = "Brak ustawień dla użytkownika"
                pieChart.clear()
                categoriesContainer.removeAllViews()
                return@launch
            }

            // 🔹 Sprawdź czy kolory kategorii są puste i zainicjalizuj jeśli trzeba
            val expenses = expenseDao.getAllExpensesForUser(userId)
            val usedCategories = expenses.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct()
            val colorsJson = try {
                JSONObject(settings.categoryColors)
            } catch (e: Exception) {
                JSONObject()
            }
            var changed = false
            usedCategories.forEach { cat ->
                if (!colorsJson.has(cat)) {
                    colorsJson.put(cat, ColorUtils.getRandomColorHex())
                    changed = true
                }
            }
            if (changed) {
                // aktualizacja ustawień w bazie
                settingsDao.updateSettings(
                    userId = settings.userId,
                    categories = settings.categories,
                    currency = settings.currency,
                    period = settings.period,
                    savingsGoal = settings.savingsGoal,
                    categoryColors = colorsJson.toString(),
                    peopleList = settings.peopleList,
                    defaultCategory = settings.defaultCategory,
                    defaultPaymentMethod = settings.defaultPaymentMethod
                )
            }
            // Po aktualizacji ustawień - pobierz je ponownie, aby wkres miał aktualne kolory
            val newSettings = settingsDao.getSettingsForUser(userId)
            val categoryColorsMap = try {
                JSONObject(newSettings?.categoryColors ?: "{}")
            } catch (e: Exception) {
                JSONObject()
            }

            val calendarStart = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calendarEnd = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth - 1, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val startDate = calendarStart.timeInMillis
            val endDate = calendarEnd.timeInMillis
            val sums: List<CategorySum> = withContext(Dispatchers.IO) {
                expenseDao.getSumByCategoryForPeriod(userId, startDate, endDate)
            }

            val sumsMap = mutableMapOf<String, Double>()
            var totalSpent = 0.0
            for (s in sums) {
                val cat = s.category ?: "Inne"
                val tot = s.total ?: 0.0
                sumsMap[cat] = tot
                totalSpent += tot
            }

            val entries = ArrayList<PieEntry>()
            val displayCategories = ArrayList<String>()
            //Tworzymy wykres na podstawie danych historycznych - nawet jesli kategoria już nie istnieje
            for ((cat, total) in sumsMap) {
                val categoryName = cat.ifBlank { "Inne" } //Jeśli puste, to "Inne"
                entries.add(PieEntry(total.toFloat(), categoryName))
                displayCategories.add(categoryName)
            }

            val NO_DATA_COLOR = Color.parseColor("#8FEAFF")
            val hasAnyValue = entries.any { it.value > 0f }
            val isNoData = !hasAnyValue
            if (isNoData) {
                entries.clear()
                entries.add(PieEntry(1f, "Brak wydatków"))
            }

            val dataSet = PieDataSet(entries, "")
            dataSet.sliceSpace = 3f
            dataSet.selectionShift = 8f

            val colors = if (isNoData) {
                listOf(NO_DATA_COLOR)
            } else {
                entries.map { entry ->
                    val hex = categoryColorsMap.optString(entry.label, "")
                    if (hex.isNotEmpty()) Color.parseColor(hex)
                    else ColorUtils.getRandomColor()
                }
            }
            dataSet.colors = colors

            val data = PieData(dataSet)
            data.setValueFormatter(PercentFormatter(pieChart)) // procenty na wykresie
            data.setValueTextSize(12f)
            data.setValueTextColor(Color.WHITE)
            pieChart.data = data

            // Ustawienie bieżącego budżetu dla wybranego miesiąca z tabeli MonthlyBudget
            var currentMonthBudgetEntity = withContext(Dispatchers.IO) {
                monthlyBudgetDao.getBudgetForMonth(userId, selectedYear, selectedMonth)
            }

            // Sprawdzamy, czy wybrany miesiac jest w przyszłości
            val today = LocalDate.now()
            val isFutureMonth = selectedYear > today.year || (selectedYear == today.year && selectedMonth > today.monthValue)
            if (currentMonthBudgetEntity == null && isFutureMonth) {
                val defaultBudget = withContext(Dispatchers.IO) {
                    monthlyBudgetDao.getAllBudgetsForUser(userId).firstOrNull { it.isDefault }
                }
                if (defaultBudget != null) {
                    val newBudget = defaultBudget.copy(
                        id = 0,
                        year = selectedYear,
                        month = selectedMonth,
                        isDefault = false
                    )
                    withContext(Dispatchers.IO) {
                        monthlyBudgetDao.insertBudget(newBudget)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DashboardActivity, "Nowy miesiąc został utworzony automatycznie.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    currentMonthBudgetEntity = newBudget
                }
            }

            val currentMonthBudget = currentMonthBudgetEntity?.budget ?: 0.0

            val percentUsed = if (currentMonthBudget > 0) {
                (totalSpent / currentMonthBudget) * 100
            } else 0.0

            //Text wewnątrz koła
            val centerText = if (isNoData) {
                "Brak danych\nwprowadzonych"
            } else {
                "Łącznie wydano:\n${String.Companion.format(Locale.forLanguageTag("pl-PL"), "%.1f%%", percentUsed)}"
            }
            pieChart.centerText = centerText
            pieChart.setCenterTextSize(if (isNoData) 15f else 16f) //większy rozmiar czcionki
            val centerTextColor = when {
                isNoData && isDarkMode -> Color.WHITE
                isNoData && !isDarkMode -> Color.BLACK
                percentUsed >= 100 -> Color.parseColor("#E74C3C") // Czerwony
                isDarkMode -> Color.WHITE
                else -> Color.BLACK
            }
            pieChart.setCenterTextColor(centerTextColor)
            pieChart.invalidate()

            categoriesContainer.removeAllViews()

            for (i in entries.indices) {
                val label = entries[i].label
                val value = entries[i].value.toDouble()
                val color = colors[i]

                val row = LinearLayout(this@DashboardActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 8, 8, 8)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val colorDot = View(this@DashboardActivity).apply {
                    val dotSize = (20 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        setMargins(0, 0, 16, 0)
                    }
                    //Ustawiamy tło z pliku drawable (z obramowaniem)
                    background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.shape_dot_border)

                    //Nadpisujemy kolor środka (solid color z XML-a)
                    background?.mutate()?.let { drawable ->
                        if (drawable is GradientDrawable) {
                            drawable.setColor(color) //Kolor kategorii ze zmiennej 'color'
                        }
                    }
                }

                val text = TextView(this@DashboardActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    val formatted = MoneyFormatter.formatWithCurrency(value)
                    text = "$label - $formatted" //legenda pokazuje kwoty
                    textSize = 16f
                    val textColor = if (isDarkMode) Color.parseColor("#F1F1F1") else Color.parseColor("#202020")
                    setTextColor(textColor)
                    setShadowLayer(1.2f, 1f, 1f, if (isDarkMode) Color.parseColor("#55000000") else Color.parseColor("#22000000"))
                }
                row.addView(colorDot)
                row.addView(text)
                categoriesContainer.addView(row)
            }

            val summaryColor = if (isDarkMode) Color.parseColor("#EAEAEA") else Color.parseColor("#202020")
            textSummary.setTextColor(summaryColor)
            val builder = StringBuilder()
            builder.append("📊 Wydano: ${MoneyFormatter.formatWithCurrency(totalSpent)}\n\n")
            builder.append("💰 Budżet: ${MoneyFormatter.formatWithCurrency(currentMonthBudget)}")
            val spannable = SpannableString(
                if (currentMonthBudget > 0 && totalSpent > currentMonthBudget) {
                    val exceeded = totalSpent - currentMonthBudget
                    builder.append("\n\n❌ Przekroczono o: ${MoneyFormatter.formatWithCurrency(exceeded)}")
                    builder.toString()
                } else {
                    builder.toString()
                }
            )
            // Kolor tylko dla przekroczenia
            if (currentMonthBudget > 0 && totalSpent > currentMonthBudget) {
                val start = spannable.indexOf("❌")
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#E74C3C")),
                    start,
                    spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            textSummary.text = spannable
            textSummary.textSize = 16f

            // 🔸 Sprawdzenie, czy przekroczono 80% lub 100% budżetu
            checkBudgetWarning(totalSpent, currentMonthBudget)
        }
    }

    private fun showSetBudgetDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 16, 32, 16)

        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        layout.addView(editText)

        val checkBox = CheckBox(this)
        checkBox.text = "Powtarzaj co miesiąc"
        layout.addView(checkBox)

        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(this@DashboardActivity)
            val monthlyBudgetDao = db.monthlyBudgetDao()

            val currentBudget = withContext(Dispatchers.IO) {
                monthlyBudgetDao.getBudgetForMonth(userId, selectedYear, selectedMonth)
            }
            if (currentBudget != null) {
                editText.setText(currentBudget.budget.toString())
                checkBox.isChecked = currentBudget.isDefault
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Ustaw budżet")
            .setView(layout)
            .setPositiveButton("Zapisz") { _, _ ->
                if (isPastMonth()) {
                    Toast.makeText(this,"Nie możesz edytować budżetu dla zakończonego miesiąca.",
                        Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newBudget = editText.text.toString().toDoubleOrNull()
                val repeat = checkBox.isChecked
                if (newBudget != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.Companion.getDatabase(this@DashboardActivity)
                        val monthlyBudgetDao = db.monthlyBudgetDao()

                        var budgetForMonth = monthlyBudgetDao.getBudgetForMonth(userId, selectedYear, selectedMonth)
                        if (budgetForMonth == null) {
                            budgetForMonth = MonthlyBudget(
                                userId = userId,
                                year = selectedYear,
                                month = selectedMonth,
                                budget = newBudget,
                                isDefault = repeat
                            )
                            monthlyBudgetDao.insertBudget(budgetForMonth)
                        } else {
                            budgetForMonth = budgetForMonth.copy(
                                budget = newBudget,
                                isDefault = repeat
                            )
                            monthlyBudgetDao.updateBudget(budgetForMonth)
                        }
                        // Supabase
                        val supabaseUid = Prefs.getSupabaseUid(this@DashboardActivity)
                        if (supabaseUid != null) {
                            try {
                                MonthlyBudgetRemoteRepository.upsertBudget(
                                    supabaseUid = supabaseUid,
                                    budget = budgetForMonth
                                )
                            } catch (e: Exception) {
                                db.pendingSyncDao().insert(
                                    PendingSync(
                                        entityType = SyncConstants.ENTITY_BUDGET,
                                        operation = SyncConstants.OP_UPDATE, // Upsert traktujemy jako update
                                        localId = budgetForMonth.id,
                                        remoteId = null,
                                        payloadJson = Json.encodeToString(
                                            MonthlyBudget.serializer(),
                                            budgetForMonth
                                        )
                                    )
                                )
                                // Uruchom worker
                                WorkSchedulerSupabase.scheduleSupabaseSync(this@DashboardActivity)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            loadAndShowData()
                        }
                    }
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
        DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(this@DashboardActivity)
        val isPast = isPastMonth()
        editText.isEnabled = !isPast
        checkBox.isEnabled = !isPast
        if (isPast) {
            editText.alpha = 0.5f
            checkBox.alpha = 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        if (!firstLoadDone) {
            loadAndShowData()
        }
        updateMonthLabel()
        updateMonthButtons()
    }

    //Sprawdza, czy przekroczono 80% lub 100% budżetu
    private fun checkBudgetWarning(totalExpenses: Double, monthlyBudget: Double) {
        if (monthlyBudget <= 0) return //zabezpieczenie, jeśli brak budżetu

        val percentUsed = (totalExpenses / monthlyBudget) * 100
        val today = LocalDate.now().toString() // Pobiera aktualny dzień
        val last80 = Prefs.getLast80WarningDate(this)
        val last100 = Prefs.getLast100WarningDate(this)
        // Alert 100%
        if (percentUsed >= 100) {
            // Pokazujemy tylko jeśli nie pokazano dziś
            if (last100 != today) {
                //100% - czerwony alert
                showBudgetDialog(
                    "⚠️ Przekroczyłeś budżet!",
                    "Twój budżet został przekroczony o ${MoneyFormatter.formatWithCurrency(totalExpenses - monthlyBudget)}",
                    "#FF4444" // czerwony
                )
                // Zapisujemy datę
                Prefs.setLast100WarningDate(this, today)
            }
            return
        }
        // Alert 80%
        if (percentUsed >= 80) {
            // Pokazujemy tylko jeśli nie pokazano dziś
            if (last80 != today) {
                // 80% – pomarańczowe ostrzeżenie
                showBudgetDialog(
                    "\uD83D\uDFE0 Uwaga – zbliżasz się do limitu!",
                    "Wykorzystałeś już %.1f%% budżetu. Do przekroczenia brakuje %.2f zł."
                        .format(percentUsed, monthlyBudget - totalExpenses),
                    "#FFA500" // pomarańczowy
                )
                // Zapisujemy datę
                Prefs.setLast80WarningDate(this, today)
            }
            return
        }
    }
    private fun isPastMonth(): Boolean {
        val today = LocalDate.now()
        return selectedYear < today.year ||
                (selectedYear == today.year && selectedMonth < today.monthValue)
    }
    // 🔸 Pomocnicza funkcja wyświetlająca okienko dialogowe
    private fun showBudgetDialog(title: String, message: String, colorHex: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor(colorHex))
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.parseColor(colorHex))
        }
        dialog.show()
    }
}