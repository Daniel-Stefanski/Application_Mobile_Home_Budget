package com.example.homebudget.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.dao.SettingsDao
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.scheduler.DashboardBudgetAlarmScheduler
import com.example.homebudget.ui.auth.LoginActivity
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.color.ColorPalette
import com.example.homebudget.utils.color.ColorUtils
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.utils.settings.SettingsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//SettingsActivity.kt – ekran ustawień aplikacji.
class SettingsActivity : AppCompatActivity(){

    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        userId = Prefs.getUserId(this)
        if (userId == -1) {
            Toast.makeText(this, "Błąd: brak użytkownika", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        //Sekcja główne
        val sectionAccount = findViewById<LinearLayout>(R.id.sectionAccount)
        val userDao = AppDatabase.Companion.getDatabase(this).userDao()
        val editName = findViewById<EditText>(R.id.editTextName)
        val editEmail = findViewById<EditText>(R.id.editTextEmail)
        val editOldPassword = findViewById<EditText>(R.id.editTextOldPassword)
        val editNewPassword = findViewById<EditText>(R.id.editTextNewPassword)
        val checkboxShowPasswordSettings = findViewById<CheckBox>(R.id.checkboxShowPasswordSettings)
        val checkboxNotifications = findViewById<CheckBox>(R.id.checkboxNotifications)
        val textCreatedAt = findViewById<TextView>(R.id.textCreatedAt)
        val textLastLogin = findViewById<TextView>(R.id.textLastLogin)
        val buttonDeleteAccount = findViewById<TextView>(R.id.buttonDeleteAccount)
        val buttonSaveChanges = findViewById<Button>(R.id.buttonSaveAccountChanges)

        val sectionPreferences = findViewById<LinearLayout>(R.id.sectionPreferences)

        val sectionTheme = findViewById<LinearLayout>(R.id.sectionTheme)
        val rowLight = findViewById<LinearLayout>(R.id.rowLight)
        val rowDark = findViewById<LinearLayout>(R.id.rowDark)
        val rowSystem = findViewById<LinearLayout>(R.id.rowSystem)
        val radioLight = findViewById<RadioButton>(R.id.radioLight)
        val radioDark = findViewById<RadioButton>(R.id.radioDark)
        val radioSystem = findViewById<RadioButton>(R.id.radioSystem)

        val sectionCategories = findViewById<LinearLayout>(R.id.sectionCategories)
        val settingsDao = AppDatabase.Companion.getDatabase(this).settingsDao()

        val sectionPeople = findViewById<LinearLayout>(R.id.sectionPeople)

        //Nagłówki sekcji (kliknięcie do rozwijania)
        val headerAccount = findViewById<View>(R.id.headerAccount)
        val headerAcountTitle = headerAccount.findViewById<TextView>(R.id.textHeaderTitle)
        val headerAccountArrow = headerAccount.findViewById<TextView>(R.id.textHeaderArrow)
        headerAcountTitle.text = "\uD83D\uDC64 Ustawienia konta"

        val headerPreferences = findViewById<View>(R.id.headerPreferences)
        val headerPreferencesTitle = headerPreferences.findViewById<TextView>(R.id.textHeaderTitle)
        val headerPreferencesArrow = headerPreferences.findViewById<TextView>(R.id.textHeaderArrow)
        headerPreferencesTitle.text = "\uD83D\uDD27 Preferencje użytkownika"

        val headerTheme = findViewById<View>(R.id.headerTheme)
        val headerThemeTitle = headerTheme.findViewById<TextView>(R.id.textHeaderTitle)
        val headerThemeArrow = headerTheme.findViewById<TextView>(R.id.textHeaderArrow)
        headerThemeTitle.text = "\uD83C\uDFA8 Ustawienia motywu"

        val headerCategories = findViewById<View>(R.id.headerCategories)
        val headerCategoriesTitle = headerCategories.findViewById<TextView>(R.id.textHeaderTitle)
        val headerCategoriesArrow = headerCategories.findViewById<TextView>(R.id.textHeaderArrow)
        headerCategoriesTitle.text = "\uD83D\uDCC2 Ustawienia kategorii"

        val headerPeople = findViewById<View>(R.id.headerPeople)
        val headerPeopleTitle = headerPeople.findViewById<TextView>(R.id.textHeaderTitle)
        val headerPeopleArrow = headerPeople.findViewById<TextView>(R.id.textHeaderArrow)
        headerPeopleTitle.text = "\uD83D\uDC65 Osoby"

        val secondaryColor = getSecondaryTextColor()
        val labelLight = rowLight.getChildAt(2) as TextView
        val labelDark = rowDark.getChildAt(2) as TextView
        val labelSystem = rowSystem.getChildAt(2) as TextView

        textCreatedAt.setTextColor(secondaryColor)
        textLastLogin.setTextColor(secondaryColor)
        labelLight.setTextColor(secondaryColor)
        labelDark.setTextColor(secondaryColor)
        labelSystem.setTextColor(secondaryColor)

        // Załadowanie danych użytkownika
        lifecycleScope.launch {
            val currentUser = userDao.getUserById(userId)
            currentUser?.let {
                runOnUiThread {
                    editName.setText(it.name)
                    editEmail.setText(it.username)

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    textCreatedAt.text = "Data utworzenia konta: ${dateFormat.format(Date(it.createdAt))}"
                    textLastLogin.text = "Ostatnie logowanie: ${dateFormat.format(Date(it.lastLogin))}"
                }
            }
        }
        findViewById<View>(R.id.imagePasswordInfoSettings).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Wymagania hasła")
                .setMessage(
                    """
            • minimum 8 znaków
            • co najmniej 1 mała litera
            • co najmniej 1 duża litera
            • co najmniej 1 cyfra
            • co najmniej 1 znak specjalny
            """.trimIndent()
                )
                .setPositiveButton("OK", null)
                .show()
        }
        //Pokaż/Ukryj hasło
        checkboxShowPasswordSettings.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editOldPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                editNewPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editOldPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                editNewPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            editOldPassword.setSelection(editOldPassword.text.length)
            editNewPassword.setSelection(editNewPassword.text.length)
        }

        //Włącz/wyłącz powiadomienia
        //Wczytanie
        checkboxNotifications.isChecked = Prefs.isNotificationsEnabled(this)
        //Zapisz
        checkboxNotifications.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setNotificationsEnabled(this, isChecked)
            if (isChecked) {
                // 🔕 Przy wyłączeniu anulujemy alarm
                DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(this)
            } else {
                // 🔕 Przy wyłączeniu anulujemy alarm
                DashboardBudgetAlarmScheduler.cancelScheduledBudgetCheck(this)
            }
            val message = if (isChecked) {
                "\uD83D\uDD14 Powiadomienia zostały włączone."
            } else {
                "\uD83D\uDD15 Powiadomienia zostały wyłączone. Przypomnienia o rachunkach nie będą aktywne."
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        editName.filters = arrayOf(InputFilter.LengthFilter(20))
        buttonSaveChanges.setOnClickListener {
            val newName = editName.text.toString().trim().take(20)
            val newEmail = editEmail.text.toString().trim()
            val oldPass = editOldPassword.text.toString().trim()
            val newPass = editNewPassword.text.toString().trim()

            lifecycleScope.launch {
                val user = userDao.getUserById(userId)
                if (user != null) {
                    var update = false

                    //Zmaina imienia
                    if (newName.isNotEmpty() && newName != user.name) {
                        userDao.updateUserName(userId, newName)
                        update = true
                    }

                    //Zmiana emaila (sprawdzenie czy nie istnieje)
                    if (newEmail.isNotEmpty() && newEmail != user.username) {
                        val existing = userDao.getUserByUsername(newEmail)
                        if (existing == null) {
                            userDao.updateUserEmail(userId, newEmail)
                            update = true
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@SettingsActivity, "Ten email jest już zajęty",
                                    Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }
                    }

                    //Zmiana hasła
                    if (oldPass.isNotEmpty() || newPass.isNotEmpty()) {
                        if (oldPass.isEmpty() || newPass.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    "Podaj stare i nowe hasło",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                        if (!isPasswordValid(newPass)) {
                            withContext(Dispatchers.Main) {
                                editNewPassword.error = "Hasło nie spełnia wymagań"
                            }
                            return@launch
                        }
                        if (oldPass == user.password) {
                            userDao.updateUserPassword(userId, newPass)
                            update = true
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    "Stare hasło nie poprawne",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                    }

                    if (update) {
                        runOnUiThread {
                            Toast.makeText(this@SettingsActivity, "Dane konta zaktualizowane",
                                Toast.LENGTH_SHORT).show()
                            editOldPassword.text.clear()
                            editNewPassword.text.clear()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@SettingsActivity, "Brak zmian do zpaisania", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        //Wczytywanie zapisanej preferencji motywu
        val savedTheme = Prefs.getAppTheme(this)
        when (savedTheme) {
            "light" -> radioLight.isChecked = true
            "dark" -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        //Obsługa zmiany motywu
        fun selectTheme(mode: String) {
            when (mode) {
                "light" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    radioLight.isChecked = true
                    radioDark.isChecked = false
                    radioSystem.isChecked = false
                }
                "dark" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    radioLight.isChecked = false
                    radioDark.isChecked = true
                    radioSystem.isChecked = false
                }
                "system" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    radioLight.isChecked = false
                    radioDark.isChecked = false
                    radioSystem.isChecked = true
                }
            }

            Prefs.setAppTheme(this, mode)

            delegate.applyDayNight()
            recreate()
        }

        fun setThemeRowClick(row: View, radio: RadioButton, mode: String) {
            row.setOnClickListener { selectTheme(mode) }
            radio.setOnClickListener { selectTheme(mode) }
        }
        setThemeRowClick(rowLight, radioLight, "light")
        setThemeRowClick(rowDark, radioDark, "dark")
        setThemeRowClick(rowSystem, radioSystem, "system")

        //Przycisk resetu danych
        val buttonReset = findViewById<Button>(R.id.buttonResetData)

        //Przycisk powrotu
        val buttonBack = findViewById<Button>(R.id.buttonBackToMain)

        //Przycisk wyloguj
        val buttonLogout = findViewById<Button>(R.id.buttonLogout)

        //Początkowo ukrywamy sekcję
        sectionAccount.visibility = View.GONE
        sectionPreferences.visibility = View.GONE
        sectionTheme.visibility = View.GONE
        sectionCategories.visibility = View.GONE
        sectionPeople.visibility = View.GONE

        val allSections = listOf(
            sectionAccount to headerAccountArrow,
            sectionPreferences to headerPreferencesArrow,
            sectionTheme to headerThemeArrow,
            sectionCategories to headerCategoriesArrow,
            sectionPeople to headerPeopleArrow
        )
        //Klikniecie rozwijające
        headerAccount.setOnClickListener {
            closeAllSectionExcept(sectionAccount, headerAccountArrow, allSections)
            toggleSection(sectionAccount, headerAccountArrow)
        }

        headerPreferences.setOnClickListener {
            closeAllSectionExcept(sectionPreferences, headerPreferencesArrow, allSections)
            toggleSection(sectionPreferences, headerPreferencesArrow)

            if (sectionPreferences.visibility == View.VISIBLE) {
                loadPreferencesData(settingsDao)
            }
        }

        headerTheme.setOnClickListener {
            closeAllSectionExcept(sectionTheme, headerThemeArrow, allSections)
            toggleSection(sectionTheme, headerThemeArrow)
        }

        headerCategories.setOnClickListener {
            closeAllSectionExcept(sectionCategories, headerCategoriesArrow, allSections)
            toggleSection(sectionCategories, headerCategoriesArrow)

            if (sectionCategories.visibility == View.VISIBLE) {
                loadCategoriesList(settingsDao, userId, sectionCategories)
            }
        }

        headerPeople.setOnClickListener {
            closeAllSectionExcept(sectionPeople, headerPeopleArrow, allSections)
            toggleSection(sectionPeople, headerPeopleArrow)

            if (sectionPeople.visibility == View.VISIBLE) {
                loadPeopleList(settingsDao, userId, sectionPeople)
            }
        }

        //Reset danych
        buttonReset.setOnClickListener {
            val titleView = layoutInflater.inflate(R.layout.dialog_title, null)
            titleView.findViewById<TextView>(R.id.dialogTitle).text = "Resetuj dane"
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage("Czy na pewno chcesz zresetować wszystkie dane (wydatki, cele, usatwienia)?\n"
                        + "Dane konta użytkownika zostaną zachowane.")
                .setPositiveButton("Tak") { _, _ ->
                    //Dialog ładowania
                    val loadingView = layoutInflater.inflate(R.layout.dialog_loading, null)
                    loadingView.findViewById<TextView>(R.id.loadingText).text = "Resetowanie danych..."
                    val loadingDialog = AlertDialog.Builder(this)
                        .setView(loadingView)
                        .setCancelable(false)
                        .create()
                    loadingDialog.show()

                    lifecycleScope.launch {
                        val db = AppDatabase.Companion.getDatabase(this@SettingsActivity)
                        val expenseDao = db.expenseDao()
                        val savingsDao = db.savingsGoalDao()
                        val settingsDao = db.settingsDao()

                        //Usuń dane powiązane z użytkownikiem
                        expenseDao.deleteAll(userId)
                        savingsDao.getGoalsForUser(userId).forEach { goal -> savingsDao.delete(goal) }

                        //Przywróć domyślne ustawienia
                        val defaultCategories = listOf("Jedzenie","Transport","Rachunki","Rozrywka","Inne")
                        val defaultColors = mapOf(
                            "Jedzenie" to "#4CAF50",
                            "Transport" to "#2196F3",
                            "Rachunki" to "#FF9800",
                            "Rozrywka" to "#9C27B0",
                            "Inne" to "#9E9E9E"
                        )
                        val categoriesJson = defaultCategories.joinToString(prefix = "[", postfix = "]") {"\"$it\""}
                        val colorsJson = JSONObject(defaultColors).toString()

                        settingsDao.updateSettings(
                            userId = userId,
                            categories = categoriesJson,
                            currency = "PLN",
                            period = "Miesięczny",
                            savingsGoal = 0.0,
                            categoryColors = colorsJson,
                            peopleList =  "[]"
                        )

                        // Przywróć motyw do domyślnego (jasny)
                        Prefs.setAppTheme(this@SettingsActivity, "light")

                        withContext(Dispatchers.Main) {
                            loadingDialog.dismiss()
                            Toast.makeText(
                                this@SettingsActivity,
                                "Dane zostały zresetowane",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Odśwież motyw — daj mu chwilę na zastosowanie
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                            //Odśwież ekran lub wróć do Dashboard
                            val intent =
                                Intent(this@SettingsActivity, DashboardActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        //Powrót
        buttonBack.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }

        //Wyloguj
        buttonLogout.setOnClickListener {
            val titleView = layoutInflater.inflate(R.layout.dialog_title, null)
            titleView.findViewById<TextView>(R.id.dialogTitle).text = "Wyloguj się"
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage("Czy na pewno chcesz się wylogować z aplikacji?")
                .setPositiveButton("Tak") { _, _ ->
                    // Czyścimy wszystkie prefs prze Prefs.kt
                    Prefs.resetAll(this)
                    // Zachowujemy aktualny motyw (taki jaki był ustawiony)
                    Prefs.setAppTheme(this, savedTheme)
                    Toast.makeText(this, "Wylogowano pomyślnie", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }

        // Fukncja przycisku usuń konto
        buttonDeleteAccount.setOnClickListener {
            val titleView = layoutInflater.inflate(R.layout.dialog_title, null)
            titleView.findViewById<TextView>(R.id.dialogTitle).text = "Usuń konto"
            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setMessage("Czy na pewno chcesz trwale usunąć konto i wszystkie dane?")
                .setPositiveButton("Tak") { _, _ ->
                    val loadingView = layoutInflater.inflate(R.layout.dialog_loading, null)
                    loadingView.findViewById<TextView>(R.id.loadingText).text = "Usuwanie konta..."
                    val loadingDialog = AlertDialog.Builder(this)
                        .setView(loadingView)
                        .setCancelable(false)
                        .create()
                    loadingDialog.show()
                    lifecycleScope.launch {
                        userDao.deleteUser(userId)

                        // Wyczyść wszystkie dane z SharedPreferences
                        Prefs.resetAll(this@SettingsActivity)

                        withContext(Dispatchers.Main) {
                            loadingDialog.dismiss()
                            Toast.makeText(
                                this@SettingsActivity,
                                "Konto zostało usunięte",
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
    }

    private fun getSecondaryTextColor(): Int {
        val isDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

        return if (isDark) Color.parseColor("#DDDDDD") // jasnoszary
        else Color.parseColor("#444444")               // ciemnoszary
    }

    private fun showAddCategoryDialog(settingsDao: SettingsDao, userId: Int, sectionCategories: LinearLayout) {
        val context = this
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val inputCategory = dialogView.findViewById<EditText>(R.id.inputCategoryName)
        val colorPreview = dialogView.findViewById<View>(R.id.colorPreview)

        //Losowy kolor
        val color = Color.parseColor(ColorPalette.CATEGORY_COLORS.random())
        colorPreview.setBackgroundColor(color)

        val titleView = layoutInflater.inflate(R.layout.dialog_title, null)
        titleView.findViewById<TextView>(R.id.dialogTitle).text = "Dodaj kategorię \uD83D\uDDC2\uFE0F"

        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(titleView)
            .setView(dialogView)
            .setPositiveButton("Zapisz") { _, _ ->
                val newCategory = inputCategory.text.toString().trim().replaceFirstChar { it.uppercase() }

                if (newCategory.isEmpty()) {
                    Toast.makeText(context, "Podaj nazwę kategorii", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val settings = settingsDao.getSettingsForUser(userId)
                    if (settings != null) {
                        val categories = SettingsHelper.getCategories(settings)
                        val colors = SettingsHelper.getCategoryColors(settings)

                        if (!categories.contains(newCategory)) {
                            categories.add(newCategory)
                            colors.put(newCategory, String.format("#%06X", 0xFFFFFF and color))

                            settingsDao.update(
                                settings.copy(
                                    categories = SettingsHelper.writeCategories(categories),
                                    categoryColors = colors.toString()
                                )
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Dodano kategorię: $newCategory",
                                    Toast.LENGTH_SHORT
                                ).show()
                                loadCategoriesList(settingsDao, userId, sectionCategories)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Taka kategoria już istnieje",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.show()
    }

    private fun loadCategoriesList(settingsDao: SettingsDao, userId: Int, sectionCategories: LinearLayout) {
        lifecycleScope.launch {
            val settings = settingsDao.getSettingsForUser(userId)
            val context = this@SettingsActivity

            withContext(Dispatchers.Main) {
                sectionCategories.removeAllViews()
            }

            if (settings != null) {
                val categoriesList = SettingsHelper.getCategories(settings)
                withContext(Dispatchers.Main) {
                    if (categoriesList.isNotEmpty()) {
                        val categoryColorsMap = SettingsHelper.getCategoryColors(settings)
                        val colors = categoriesList.map { cat ->
                            val hex = categoryColorsMap.optString(cat, "")
                            if (hex.isNotEmpty()) Color.parseColor(hex)
                            else {
                                val newColor = ColorUtils.getRandomColor()
                                categoryColorsMap.put(
                                    cat,
                                    String.format("#%06X", 0xFFFFFF and newColor)
                                )
                                newColor
                            }
                        }

                        //Zaktualizuj bazę, jeśli doszły nowe kolory
                        settingsDao.update(
                            settings.copy(
                                categoryColors = categoryColorsMap.toString()
                            )
                        )

                        categoriesList.forEachIndexed { index, category ->
                            val row = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(8, 8, 8, 8)
                            }

                            val colorView = View(context).apply {
                                val size = (24 * resources.displayMetrics.density).toInt()
                                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                    setMargins(8, 0, 16, 0)
                                    gravity = Gravity.CENTER_VERTICAL
                                }

                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    cornerRadius = 8f * resources.displayMetrics.density
                                    setColor(colors[index % colors.size])
                                }

                                //Kliknięcie w kolor - otwiera wybór koloru
                                setOnClickListener {
                                    //24kolory - pastelowe + mocniejsze + neutralne
                                    val colorOptions = ColorPalette.CATEGORY_COLORS

                                    //Grid 4 kolumny
                                    val dialogLayout = GridLayout(context).apply {
                                        columnCount = 7
                                        rowCount = (colorOptions.size + 3) / 7
                                        setPadding(32, 32, 32, 32)
                                    }
                                    // Dialog wyboru koloru kategorii
                                    val colorDialog = AlertDialog.Builder(context)
                                        .setTitle("Wybierz nowy kolor")
                                        .setView(dialogLayout)
                                        .setNegativeButton("Anuluj", null)
                                        .create()

                                    val currentColorHex = categoryColorsMap.optString(category, "")
                                    colorOptions.forEach { hex ->
                                        val colorCircle = View(context).apply {
                                            val circleSize =
                                                (36 * resources.displayMetrics.density).toInt()
                                            val isCurrent =
                                                currentColorHex.equals(hex, ignoreCase = true)
                                            layoutParams = GridLayout.LayoutParams().apply {
                                                width = circleSize
                                                height = circleSize
                                                setMargins(12, 12, 12, 12)
                                            }
                                            background = GradientDrawable().apply {
                                                shape = GradientDrawable.OVAL
                                                setColor(Color.parseColor(hex))
                                                if (isCurrent) {
                                                    setStroke(
                                                        (4 * resources.displayMetrics.density).toInt(),
                                                        Color.BLACK
                                                    ) // Znacznik aktualnego koloru
                                                }
                                            }

                                            isEnabled = !isCurrent
                                            if (!isCurrent) {
                                                setOnClickListener {
                                                    lifecycleScope.launch {
                                                        val currentSettings =
                                                            settingsDao.getSettingsForUser(userId)
                                                        if (currentSettings != null) {
                                                            val categoryColors =
                                                                JSONObject(currentSettings.categoryColors)
                                                            categoryColors.put(category, hex)

                                                            settingsDao.update(
                                                                currentSettings.copy(
                                                                    categoryColors = categoryColors.toString()
                                                                )
                                                            )

                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Zmieniono kolor kategorii: $category",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                colorDialog.dismiss()
                                                                loadCategoriesList(
                                                                    settingsDao,
                                                                    userId,
                                                                    sectionCategories
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        dialogLayout.addView(colorCircle)
                                    }
                                    colorDialog.show()

                                }
                            }
                            val textColor = if (resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK
                                == Configuration.UI_MODE_NIGHT_YES
                            ) {
                                resources.getColor(R.color.dialogText, null) // jasny tekst
                            } else {
                                resources.getColor(R.color.dialogText, null) // czarny tekst
                            }
                            val textView = TextView(context).apply {
                                text = category
                                textSize = 16f
                                setTextColor(textColor)
                                layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                            }

                            val deleteButton = Button(context).apply {
                                text = "Usuń"
                                textSize = 14f
                                setPadding(24, 8, 24, 8)
                                backgroundTintList =
                                    getColorStateList(R.color.buttonPrimaryBackground)
                                setTextColor(getColor(R.color.buttonPrimaryText))
                                setOnClickListener {

                                    val titleView =
                                        layoutInflater.inflate(R.layout.dialog_title, null)
                                    titleView.findViewById<TextView>(R.id.dialogTitle).text =
                                        "Usuń kategorię \uD83D\uDDC2\uFE0F"

                                    AlertDialog.Builder(context)
                                        .setCustomTitle(titleView)
                                        .setMessage(
                                            "Czy na pewno chcesz usunąć kategorię: \"$category\"?\n" +
                                                    "Nie wpłynie to na już zapisane wydatki."
                                        )
                                        .setPositiveButton("Tak") { _, _ ->
                                            lifecycleScope.launch {
                                                val currentSettings =
                                                    settingsDao.getSettingsForUser(userId)
                                                if (currentSettings != null) {
                                                    //Aktualna lista kategorii
                                                    val categoriesList = currentSettings.categories
                                                        .removePrefix("[")
                                                        .removeSuffix("]")
                                                        .split(Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*\$)"))
                                                        .map { it.replace("\"", "").trim() }
                                                        .filter { it.isNotEmpty() }
                                                        .toMutableList()

                                                    //Usuń wybraną kategorię
                                                    categoriesList.remove(category)
                                                    val updatedCategories =
                                                        categoriesList.joinToString(
                                                            prefix = "[",
                                                            postfix = "]"
                                                        ) { "\"$it\"" }

                                                    //Nie usuwamy koloru usuniętej kategorii (historyczne dane)
                                                    val categoryColors = try {
                                                        JSONObject(currentSettings.categoryColors)
                                                    } catch (e: Exception) {
                                                        JSONObject()
                                                    }

                                                    //Zaktualizuj bazę, jeśli usunięto kategorię
                                                    settingsDao.update(
                                                        currentSettings.copy(
                                                            categories = updatedCategories
                                                        )
                                                    )

                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "Usunięto kategorię: $category",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        loadCategoriesList(
                                                            settingsDao,
                                                            userId,
                                                            sectionCategories
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        .setNegativeButton("Anuluj", null)
                                        .show()
                                }
                            }

                            row.addView(colorView)
                            row.addView(textView)
                            row.addView(deleteButton)
                            sectionCategories.addView(row)
                        }
                    } else {
                        val noCatText = TextView(context).apply {
                            text = "Brak zapisanych kategorii"
                            textSize = 14f
                            setPadding(8, 8, 8, 8)
                        }
                        noCatText.setTextColor(getSecondaryTextColor())
                        sectionCategories.addView(noCatText)
                    }
                    val addButton = Button(context).apply {
                        text = "Dodaj kategorię"
                        textSize = 14f
                        setPadding(16, 12, 16, 12)
                        backgroundTintList = getColorStateList(R.color.buttonPrimaryBackground)
                        setTextColor(getColor(R.color.buttonPrimaryText))
                        setOnClickListener {
                            showAddCategoryDialog(settingsDao, userId, sectionCategories)
                        }
                    }
                    sectionCategories.addView(addButton)
                }
            }
        }
    }

    private fun showAddPersonDialog(settingsDao: SettingsDao, userId: Int, sectionPeople: LinearLayout) {
        val context = this
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_person, null)
        val input = dialogView.findViewById<EditText>(R.id.inputPersonName)

        val titleView = layoutInflater.inflate(R.layout.dialog_title, null)
        titleView.findViewById<TextView>(R.id.dialogTitle).text = "Dodaj osobę \uD83D\uDC64"
        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(dialogView)
            .setPositiveButton("Zapisz") { _, _ ->
                val newPerson = input.text.toString().trim().split(" ").filter { it.isNotEmpty() }.joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() }}
                if (newPerson.isEmpty()) {
                    Toast.makeText(context, "Podaj nazwę osoby", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val settings = settingsDao.getSettingsForUser(userId)
                    if (settings != null) {
                        val existing = SettingsHelper.getPeople(settings)
                        if (existing.contains(newPerson)) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Taka osoba już istnieje",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }
                        val updatedPeople = existing.toMutableList()
                        updatedPeople.add(newPerson)

                        settingsDao.update(
                            settings.copy(
                                peopleList = SettingsHelper.writePeople(updatedPeople)
                            )
                        )

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Dodano osobę: $newPerson", Toast.LENGTH_SHORT)
                                .show()
                            loadPeopleList(settingsDao, userId, sectionPeople)
                        }
                    }
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun loadPeopleList(settingsDao: SettingsDao, userId: Int, sectionPeople: LinearLayout) {
        lifecycleScope.launch {
            val settings = settingsDao.getSettingsForUser(userId)
            val context = this@SettingsActivity

            withContext(Dispatchers.Main) {
                sectionPeople.removeAllViews() // czyścimy listę przed ponownym załadowaniem
            }

            if (settings != null) {
                val people = SettingsHelper.getPeople(settings).sorted()

                withContext(Dispatchers.Main) {
                    if (people.isNotEmpty()) {
                        people.forEachIndexed { i, name ->
                            val row = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(8, 8, 8, 8)
                            }

                            val textColor = if (resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                            ) {
                                resources.getColor(R.color.dialogText, null)
                            } else {
                                resources.getColor(R.color.dialogText, null)
                            }
                            val nameText = TextView(context).apply {
                                text = name
                                textSize = 16f
                                setTextColor(textColor)
                                layoutParams = LinearLayout.LayoutParams(
                                    0,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    1f
                                )
                            }

                            val deleteButton = Button(context).apply {
                                text = "Usuń"
                                textSize = 14f
                                setPadding(24, 8, 24, 8)
                                backgroundTintList =
                                    getColorStateList(R.color.buttonPrimaryBackground)
                                setTextColor(getColor(R.color.buttonPrimaryText))
                                setOnClickListener {
                                    val titleView =
                                        layoutInflater.inflate(R.layout.dialog_title, null)
                                    titleView.findViewById<TextView>(R.id.dialogTitle).text =
                                        "Usuń osobę \uD83D\uDC64"

                                    AlertDialog.Builder(context)
                                        .setCustomTitle(titleView)
                                        .setMessage("Czy na pewno chcesz usunąć osobę \"$name\" z listy?")
                                        .setPositiveButton("Tak") { _, _ ->
                                            lifecycleScope.launch {
                                                val currentSettings =
                                                    settingsDao.getSettingsForUser(userId)
                                                if (currentSettings != null) {
                                                    val people =
                                                        SettingsHelper.getPeople(currentSettings)
                                                    people.remove(name)
                                                    settingsDao.update(
                                                        currentSettings.copy(
                                                            peopleList = SettingsHelper.writePeople(
                                                                people
                                                            )
                                                        )
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            "Usunięto: $name",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        loadPeopleList(
                                                            settingsDao,
                                                            userId,
                                                            sectionPeople
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        .setNegativeButton("Anuluj", null)
                                        .show()
                                }
                            }

                            row.addView(nameText)
                            row.addView(deleteButton)
                            sectionPeople.addView(row)
                        }
                    } else {
                        val emptyText = TextView(context).apply {
                            text = "Brak dodanych osób"
                            textSize = 14f
                            setPadding(8, 8, 8, 8)
                        }
                        emptyText.setTextColor(getSecondaryTextColor())
                        sectionPeople.addView(emptyText)
                    }

                    val addButton = Button(context).apply {
                        text = "Dodaj osobę"
                        textSize = 14f
                        setPadding(16, 12, 16, 12)
                        backgroundTintList = getColorStateList(R.color.buttonPrimaryBackground)
                        setTextColor(getColor(R.color.buttonPrimaryText))
                        setOnClickListener {
                            showAddPersonDialog(settingsDao, userId, sectionPeople)
                        }
                    }
                    sectionPeople.addView(addButton)
                }
            }
        }
    }

    private fun loadPreferencesData(settingsDao: SettingsDao) {
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerDefaultCategory)
        val spinnerPayment = findViewById<Spinner>(R.id.spinnerDefaultPayment)
        lifecycleScope.launch {
            val settings = settingsDao.getSettingsForUser(userId)
            // Kategoria z bazy + brak
            val categories = mutableListOf("Brak")
            if (settings != null) {
                categories.addAll(SettingsHelper.getCategories(settings))
            }
            //Adapter kategorii
            val catAdapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                categories
            )
            catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = catAdapter
            // Odczyt zapisanej kategorii
            val savedCategory = Prefs.getDefaultCategory(this@SettingsActivity)
            val catIndex = categories.indexOf(savedCategory)
            if (catIndex >= 0) spinnerCategory.setSelection(catIndex)
            spinnerCategory.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    Prefs.setDefaultCategory(this@SettingsActivity, categories[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            })
            //Metody płatności
            val payments = listOf("Brak", "Gotówka", "Karta", "Blik", "Przelew")
            val payAdapter =
                ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, payments)
            payAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPayment.adapter = payAdapter
            // Odczyt zapisanej płatności
            val savedPayment = Prefs.getDefaultPayment(this@SettingsActivity)
            val payIndex = payments.indexOf(savedPayment)
            if (payIndex >= 0) spinnerPayment.setSelection(payIndex)
            spinnerPayment.setOnItemSelectedListener(object :
            AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    Prefs.setDefaultPayment(this@SettingsActivity, payments[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            })
        }
    }

    private fun toggleSection(section: LinearLayout, arrow: TextView) {
        if (section.visibility == View.GONE) {

            section.visibility = View.VISIBLE
            section.alpha = 0f
            section.animate().alpha(1f).setDuration(200).start()

            arrow.text = "▲"

        } else {

            section.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    section.visibility = View.GONE
                }
                .start()

            arrow.text = "▼"
        }
    }

    private fun closeAllSectionExcept(openSection: LinearLayout, openArrow: TextView, sections: List<Pair<LinearLayout, TextView>>) {
        for ((section, arrow) in sections) {
            if (section != openSection) {
                section.visibility = View.GONE
                arrow.text = "▼"
            }
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$")
        return regex.matches(password)
    }
}