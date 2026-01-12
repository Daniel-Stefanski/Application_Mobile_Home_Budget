package com.example.homebudget.ui.statistics

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.utils.money.MoneyFormatter
import com.example.homebudget.utils.settings.Prefs
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.ceil

class StatisticsActivity : AppCompatActivity() {

    private lateinit var barChart: BarChart
    private lateinit var textYear: TextView
    private lateinit var textYearTotal: TextView
    private lateinit var textYearBudget: TextView
    private lateinit var textYearSavings: TextView
    private lateinit var buttonPrevYear: ImageButton
    private lateinit var buttonNextYear: ImageButton
    private lateinit var monthDetailsPanel: LinearLayout
    private lateinit var textMonthTitle: TextView
    private lateinit var textMonthSpent: TextView
    private lateinit var textMonthBudget: TextView
    private lateinit var textMonthResult: TextView
    private lateinit var textMonthCount: TextView
    private lateinit var textMonthMin: TextView
    private lateinit var textMonthMax: TextView
    private lateinit var buttonShowMonthDetails: Button
    private lateinit var textCategoryChartTitle: TextView
    private lateinit var categoryBarChart: BarChart
    private lateinit var textPersonChartTitle: TextView
    private lateinit var personBarChart: BarChart
    private lateinit var buttonBackToMain: Button

    private var userId: Int = -1
    private var selectedYear = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        //Wczytaj userId
        userId = Prefs.getUserId(this)

        initViews()
        setupChartAppearance()
        loadYearStatistics()

        buttonPrevYear.setOnClickListener {
            selectedYear--
            updateYearLabel()
            updateYearButtons()
            loadYearStatistics()
        }
        buttonNextYear.setOnClickListener {
            if (selectedYear < LocalDate.now().year) {
                selectedYear++
                updateYearLabel()
                updateYearButtons()
                loadYearStatistics()
            } else {
                Toast.makeText(this, "Nie możesz przejść do przyszłego roku.", Toast.LENGTH_SHORT).show()
            }
        }
        buttonBackToMain.setOnClickListener {
            finish()// cofnięcie do DashboardActivity
        }
    }

    private fun initViews() {
        barChart = findViewById(R.id.barChartStats)
        textYear = findViewById(R.id.textYear)
        textYearTotal = findViewById(R.id.textYearTotal)
        textYearBudget = findViewById(R.id.textYearBudget)
        textYearSavings = findViewById(R.id.textYearSavings)
        buttonPrevYear = findViewById(R.id.buttonPrevYear)
        buttonNextYear = findViewById(R.id.buttonNextYear)
        monthDetailsPanel = findViewById(R.id.monthDetailsPanel)
        textMonthTitle = findViewById(R.id.textMonthTitle)
        textMonthSpent = findViewById(R.id.textMonthSpent)
        textMonthBudget = findViewById(R.id.textMonthBudget)
        textMonthResult = findViewById(R.id.textMonthResult)
        textMonthCount = findViewById(R.id.textMonthCount)
        textMonthMin = findViewById(R.id.textMonthMin)
        textMonthMax = findViewById(R.id.textMonthMax)
        buttonShowMonthDetails = findViewById(R.id.buttonShowMonthDetails)
        textCategoryChartTitle = findViewById(R.id.textCategoryChartTitle)
        categoryBarChart = findViewById(R.id.categoryBarChart)
        textPersonChartTitle = findViewById(R.id.textPersonChartTitle)
        personBarChart = findViewById(R.id.personBarChart)
        buttonBackToMain = findViewById(R.id.buttonBackToMain)

        updateYearLabel()
        updateYearButtons()
    }

    private fun updateYearLabel() {
        textYear.text = selectedYear.toString()
    }

    private fun updateYearButtons() {
        val currentYear = LocalDate.now().year
        if (selectedYear >= currentYear) {
            buttonNextYear.isEnabled = false
            buttonNextYear.alpha = 0.3f
        } else {
            buttonNextYear.isEnabled = true
            buttonNextYear.alpha = 1f
        }
    }

    private fun setupChartAppearance() {
        val isDark = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        barChart.description.isEnabled = false
        barChart.legend.isEnabled = true // później ustawimy legendę
        barChart.setDrawGridBackground(false)
        barChart.setTouchEnabled(true)

        // Tylko jedna oś Y
        barChart.axisRight.isEnabled = false

        // Kolory osi
        barChart.axisLeft.textColor = if (isDark) Color.WHITE else Color.BLACK
        barChart.xAxis.textColor = if (isDark) Color.WHITE else Color.BLACK

        // Usunięcie linii pomocniczych (opcjonalnie)
        barChart.axisLeft.setDrawGridLines(false)
        barChart.xAxis.setDrawGridLines(false)

        // Oś X – miesiące
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    }

    private fun loadYearStatistics() {
        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(this@StatisticsActivity)
            val expenseDao = db.expenseDao()
            val budgetDao = db.monthlyBudgetDao()

            var totalYearSpent = 0.0
            var totalYearBudgetSum = 0.0
            var highestMonthlyBudget = 0.0
            var highestMonthlySpent = 0.0

            val stackedEntries = ArrayList<BarEntry>() // Tworzy słupek który się nadpisuje danymi
            //i = indeks grupy (0..11), month = 1..12 dla dat
            for (i in 0 until 12) {
                val month = i + 1
                val start = getStartOfMonth(month)
                val end = getEndOfMonth(month)

                val expenses = withContext(Dispatchers.IO) {
                    expenseDao.getExpensesForUser(userId).filter { it.date in start..end }
                }
                val spent = expenses.sumOf { it.amount }
                val budget = withContext(Dispatchers.IO) {
                    budgetDao.getBudgetForMonth(userId, selectedYear, month)?.budget ?: 0.0
                }
                totalYearSpent += spent
                totalYearBudgetSum += budget
                highestMonthlyBudget = maxOf(highestMonthlyBudget, budget)
                highestMonthlySpent = maxOf(highestMonthlySpent, spent)
                val spentInside = minOf(spent, budget).toFloat() // Niebieski - wydatki do limitu budżetu
                val geenRest = maxOf(0.0, budget - spent).toFloat() // Zielony - pełna wysokość budżetu
                val exceed = maxOf(0.0, spent - budget).toFloat() // Czerwony - przekroczenie budżetu
                stackedEntries.add(
                    BarEntry(
                        i.toFloat(),
                        floatArrayOf(spentInside, geenRest, exceed)
                    )
                )
            }

            textYearTotal.text = "Suma wydatków: ${MoneyFormatter.formatWithCurrency(totalYearSpent)}"
            textYearBudget.text = "Budżet: ${MoneyFormatter.formatWithCurrency(totalYearBudgetSum)}"

            if (totalYearSpent <= totalYearBudgetSum) {
                val saved = totalYearBudgetSum - totalYearSpent //zaoszczędzono
                textYearSavings.text = "Zaoszczędzono: ${MoneyFormatter.formatWithCurrency(saved)}"
                textYearSavings.setTextColor(Color.parseColor("#2ECC71"))
            } else {
                val exceeded = totalYearSpent - totalYearBudgetSum //przekroczono
                textYearSavings.text = "Przekroczono: ${MoneyFormatter.formatWithCurrency(exceeded)}"
                textYearSavings.setTextColor(Color.parseColor("#E74C3C"))
            }
            //Obliczamy max wartość do skali os Y
            val maxValue = maxOf(highestMonthlyBudget, highestMonthlySpent)
            val step = 500f //odstępy pomiędzy wartościami
            val roundedMax = (Math.ceil(maxValue / step) * step).toFloat()

            // Kolory słupków
            val stackedSet = BarDataSet(stackedEntries, "").apply {
                colors = listOf(
                    Color.parseColor("#3498DB"), // pastelowy niebieski
                    Color.parseColor("#2ECC71"), // pastelowy zielony
                    Color.parseColor("#E74C3C") // pastelowy czerwony
                )
                stackLabels = arrayOf("Wydatki", "Budżet", "Przekroczenie")
                setDrawValues(false) // NIE pokazuj liczby nad słupkiem
            }

            //Nazwy miesięcy (indeks 0 = "Sty", 1 = "Lut", ...)
            val months = listOf("Sty", "Lut", "Mar", "Kwi", "Maj", "Cze",
                "Lip", "Sie", "Wrz", "Paź", "Lis", "Gru")

            barChart.xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(months)
                granularity = 1f
                labelCount = 12
                textSize = 12f
                xOffset = 10f
                yOffset = 5f
                position = XAxis.XAxisPosition.BOTTOM
            }
            //Ukryj niepotrzebne wartości
            barChart.axisLeft.apply {
                setDrawGridLines(false)
            }
            barChart.axisRight.isEnabled = false
            barChart.description.isEnabled = false

            //Kolor zależny od motywu
            val isDark = resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val axisColor = if (isDark) Color.parseColor("#A88DFF") else Color.parseColor("#835BFF") //linia główna
            val gridColor = if (isDark) Color.parseColor("#444444") else Color.parseColor("#DDDDDD") //linie pomocnicze
            val textColor = if (isDark) Color.WHITE else Color.BLACK
            barChart.axisLeft.apply { //oś Y
                axisMinimum = 0f
                axisMaximum = roundedMax
                granularity = step
                setLabelCount((roundedMax / step).toInt() + 1, true)
                // os Y
                setDrawAxisLine(true) // rysuje linię osi Y
                axisLineColor = axisColor // kolor osi (fiolet na screenie)
                axisLineWidth = 2f //grubość linii
                // Linie pomocnicze
                setDrawGridLines(true) // rysuje linie poziome
                setGridColor(gridColor) // kolor linii pomocniczych (żółty)
                gridLineWidth = 1f // grubość linii
                this.textColor = textColor
            }
            barChart.xAxis.apply {
                setDrawAxisLine(true) // rysuje linię osi X
                axisLineColor = axisColor // kolor osi (fiolet na screenie)
                axisLineWidth = 2f
                setDrawGridLines(false) // NIE chcemy pionowych linii
                this.textColor = textColor
            }
            val barData = BarData(stackedSet)
            barData.barWidth = 0.9f // 1słupek -> szeroki
            barChart.data = barData

            //Obsługa kliknięcia w miesiąc na wykresie
            barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e == null) return
                    val monthIndex = e.x.toInt() //0-11
                    findViewById<TextView>(R.id.textHintClickMonth).visibility = View.GONE
                    showMonthDetails(monthIndex)
                }

                override fun onNothingSelected() {}
            })

            barChart.xAxis.axisMinimum = -0.5f
            barChart.xAxis.axisMaximum = 11.5f

            //Legenda
            barChart.legend.textColor = if (isDark) Color.WHITE else Color.BLACK
            //Odśwież wykres
            barChart.animateY(800)
            barChart.invalidate()
        }
    }
    private fun showMonthDetails(monthIndex: Int) {
        var selectedMonthBudget = 0.0
        //ukryj poprzednie szczegóły
        textCategoryChartTitle.visibility = View.GONE
        categoryBarChart.visibility = View.GONE
        textPersonChartTitle.visibility = View.GONE
        personBarChart.visibility = View.GONE
        val month = monthIndex + 1
        val months = listOf("Styczeń", "Luty", "Marzec", "Kwiecień", "Maj", "Czerwiec",
            "Lipiec", "Sierpień", "Wrzesień", "Październik", "Listopad", "Grudzień")
        lifecycleScope.launch {
            val db = AppDatabase.Companion.getDatabase(this@StatisticsActivity)
            val expenseDao = db.expenseDao()
            val budgetDao = db.monthlyBudgetDao()
            val start = getStartOfMonth(month)
            val end = getEndOfMonth(month)
            val expenses = withContext(Dispatchers.IO) {
                expenseDao.getExpensesForUser(userId).filter { it.date in start..end }
            }
            val spent = expenses.sumOf { it.amount }
            selectedMonthBudget = withContext(Dispatchers.IO) {
                budgetDao.getBudgetForMonth(userId, selectedYear, month)?.budget ?: 0.0
            }
            val transactionsCount = expenses.size
            val minExpense = expenses.minOfOrNull { it.amount } ?: 0.0
            val maxExpense = expenses.maxOfOrNull { it.amount } ?: 0.0
            textMonthTitle.text = "${months[monthIndex]} $selectedYear"
            textMonthSpent.text = "Wydatki: ${MoneyFormatter.formatWithCurrency(spent)}"
            textMonthBudget.text = "Budżet: ${MoneyFormatter.formatWithCurrency(selectedMonthBudget)}"
            if (spent <= selectedMonthBudget) {
                val saved = selectedMonthBudget - spent
                textMonthResult.text = "Zaoszczędzono: ${MoneyFormatter.formatWithCurrency(saved)}"
                textMonthResult.setTextColor(Color.parseColor("#2ECC71"))
            } else {
                val exceeded = spent - selectedMonthBudget
                textMonthResult.text = "Przekroczono: ${MoneyFormatter.formatWithCurrency(exceeded)}"
                textMonthResult.setTextColor(Color.parseColor("#E74C3C"))
            }
            textMonthCount.text = "Liczba transakcji: $transactionsCount"
            textMonthMin.text = "Najmniejszy wydatek: ${MoneyFormatter.formatWithCurrency(minExpense)}"
            textMonthMax.text = "Największy wydatek: ${MoneyFormatter.formatWithCurrency(maxExpense)}"
            monthDetailsPanel.visibility = View.VISIBLE
        }
        buttonShowMonthDetails.setOnClickListener {
            lifecycleScope.launch {
                val db = AppDatabase.Companion.getDatabase(this@StatisticsActivity)
                val expenseDao = db.expenseDao()
                val start = getStartOfMonth(month)
                val end = getEndOfMonth(month)
                // Pobieranie sumy według kategorii
                val categories = withContext(Dispatchers.IO) {
                    expenseDao.getSumByCategoryForPeriod(userId, start, end)
                }
                // Pobieranie sumy według osób
                val people = withContext(Dispatchers.IO) {
                    expenseDao.getSumByPersonForPeriod(userId, start, end)
                }
                if (categories.isEmpty()) {
                    Toast.makeText(this@StatisticsActivity, "Brak danych dla tego miesiąca", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                // Tworzenie wykresu słupkowego dla kategorii
                val entries = ArrayList<BarEntry>()
                val labels = ArrayList<String>()
                val colors = ArrayList<Int>()
                // Pobierz kolory JEDEN raz zamiast w pętli
                val settings = withContext(Dispatchers.IO) {
                    db.settingsDao().getSettingsForUser(userId)
                }
                val colorMap = try {
                    JSONObject(settings?.categoryColors ?: "{}")
                } catch (e: Exception) {
                    JSONObject()
                }

                categories.forEachIndexed { index, item ->
                    val value = item.total ?: 0.0
                    val categoryName = item.category ?: "Inne"
                    entries.add(BarEntry(index.toFloat(), value.toFloat()))
                    labels.add(categoryName)
                    // Szybkie pobieranie koloru bez suspend
                    val hex = colorMap.optString(categoryName, "#999999")
                    colors.add(Color.parseColor(hex))
                }
                val dataSet = BarDataSet(entries, "Kategorie").apply {
                    this.colors = colors
                    setDrawValues(false)
                }
                val barData = BarData(dataSet)
                barData.barWidth = 0.6f
                categoryBarChart.data = barData
                textCategoryChartTitle.visibility = View.VISIBLE
                categoryBarChart.visibility = View.VISIBLE
                val isDark = resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val axisColor = if (isDark) Color.parseColor("#A88DFF") else Color.parseColor("#835BFF")
                val gridColor = if (isDark) Color.parseColor("#444444") else Color.parseColor("#DDDDDD")
                val textColor = if (isDark) Color.WHITE else Color.BLACK
                categoryBarChart.xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    setDrawAxisLine(true)
                    axisLineColor = axisColor
                    this.textColor = textColor
                    textSize = 12f
                }
                val maxCategoryValue = categories.maxOf { it.total ?: 0.0 }
                val dynamicStep = calculateDynamicStep(maxCategoryValue)
                val roundedMax = (ceil(maxCategoryValue / dynamicStep) * dynamicStep).toFloat()
                categoryBarChart.axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = roundedMax
                    granularity = dynamicStep
                    setLabelCount((roundedMax / dynamicStep).toInt() + 1, true)
                    setDrawAxisLine(true)
                    axisLineColor = axisColor
                    this.textColor = textColor
                    setDrawGridLines(true)
                    setGridColor(gridColor)
                    gridLineWidth = 1f
                }
                categoryBarChart.axisRight.isEnabled = false
                categoryBarChart.legend.isEnabled = false
                categoryBarChart.description.isEnabled = false
                categoryBarChart.invalidate()

                // Tworzenie wykresu słupkowego dla osób
                if (people.isNotEmpty()) {
                    val personEntries = ArrayList<BarEntry>()
                    val personLabels = ArrayList<String>()
                    people.forEachIndexed { index, item ->
                        val value = item.total
                        val name = item.person ?: "Nieznane"
                        personEntries.add(BarEntry(index.toFloat(), value.toFloat()))
                        personLabels.add(name)
                    }
                    val dataSet = BarDataSet(personEntries, "Wydatki według osób").apply {
                        color = Color.parseColor("#A88DFF")
                        setDrawValues(false)
                    }
                    val barData = BarData(dataSet)
                    barData.barWidth = 0.7f
                    personBarChart.data = barData
                    val isDark = resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    val axisColor = if (isDark) Color.parseColor("#A88DFF") else Color.parseColor("#835BFF")
                    val gridColor = if (isDark) Color.parseColor("#444444") else Color.parseColor("#DDDDDD")
                    val textColor = if (isDark) Color.WHITE else Color.BLACK
                    personBarChart.xAxis.apply {
                        valueFormatter = IndexAxisValueFormatter(personLabels)
                        position = XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        setDrawGridLines(false)
                        setDrawAxisLine(true)
                        axisLineColor = axisColor
                        this.textColor = textColor
                        textSize = 12f
                    }
                    val maxPersonValue = people.maxOf { it.total }
                    val dynamicStep = calculateDynamicStep(maxPersonValue)
                    val roundedMax = (ceil(maxPersonValue / dynamicStep) * dynamicStep).toFloat()
                    personBarChart.axisLeft.apply {
                        axisMinimum = 0f
                        axisMaximum = roundedMax
                        granularity = dynamicStep
                        setLabelCount((roundedMax / dynamicStep).toInt() + 1, true)
                        setDrawAxisLine(true)
                        axisLineColor = axisColor
                        this.textColor = textColor
                        setDrawGridLines(true)
                        setGridColor(gridColor)
                        gridLineWidth = 1f
                    }
                    personBarChart.axisRight.isEnabled = false
                    personBarChart.legend.isEnabled = false
                    personBarChart.description.isEnabled = false
                    textPersonChartTitle.visibility = View.VISIBLE
                    personBarChart.visibility = View.VISIBLE
                    personBarChart.invalidate()
                }
            }
        }
    }
    private fun calculateDynamicStep(maxValue: Double): Float {
        return when {
            maxValue <= 100 -> 20f //małe wartości
            maxValue <= 400 -> 100f //średnie wartości
            maxValue <= 2000 -> 200f // większe wartości
            else -> 500f // bardzo duże wartości - jak dotychczas
        }
    }

    private fun getStartOfMonth(month: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, month - 1, 1, 0, 0, 0)
        return cal.timeInMillis
    }

    private fun getEndOfMonth(month: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(selectedYear, month - 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        return cal.timeInMillis
    }
    private suspend fun getDynamicCategoryColor(category: String): Int {
        val db = AppDatabase.Companion.getDatabase(this@StatisticsActivity)
        val settings = db.settingsDao().getSettingsForUser(userId)
        if (settings == null) return  Color.GRAY
        val json = try {
            JSONObject(settings.categoryColors)
        } catch (e: Exception) {
            JSONObject()
        }
        val hex = json.optString(category, "")
        return if (hex.isNotEmpty()) {
            Color.parseColor(hex)
        } else {
            Color.GRAY
        }
    }
}