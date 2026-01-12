package com.example.homebudget.ui.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.entity.User
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

//RegisterActivity.kt – ekran rejestracji nowego użytkownika.
class RegisterActivity : AppCompatActivity() {

    private lateinit var nameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var confirmPasswordField: EditText
    private lateinit var checkboxShowPassword: CheckBox
    private lateinit var checkboxTerms: CheckBox
    private lateinit var registerButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var loginText: TextView
    private lateinit var textReadTerms: TextView
    private lateinit var textTermsError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Ekran rejestracji zawsze będzie miał motyw jasny
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Inicjalizacja widoków (użyte Twoje ID z layoutu)
        nameField = findViewById(R.id.editTextName)
        emailField = findViewById(R.id.editTextEmail)
        passwordField = findViewById(R.id.editTextPassword)
        confirmPasswordField = findViewById(R.id.editTextConfirmPassword)
        checkboxShowPassword = findViewById(R.id.checkboxShowPassword)
        checkboxTerms = findViewById(R.id.checkBoxTerms)
        registerButton = findViewById(R.id.buttonRegister)
        progressBar = findViewById(R.id.progressBar)
        loginText = findViewById(R.id.textLogin)
        findViewById<View>(R.id.imagePasswordInfo).setOnClickListener {
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

        //Tworzy klikalny fragment "Regulamin"
        val termsText = "Akceptuję Regulamin"
        val spannable = SpannableString(termsText)

        //Ustawiamy klikalny fragment (tylko słowo "Regulamin")
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                //Otwieramy osobny ekran z regulaminem
                val intent = Intent(this@RegisterActivity, TermsActivity::class.java)
                startActivityForResult(intent, 1001)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.parseColor("#1976D2") //niebieski jak w linku
                ds.isUnderlineText = true
            }
        }

        //Zakres dla słowa "Regulamin"
        spannable.setSpan(clickableSpan, 10, termsText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        //Ustawiamy tekst z linkiem
        checkboxTerms.text = spannable
        checkboxTerms.movementMethod = LinkMovementMethod.getInstance()
        checkboxTerms.highlightColor = Color.TRANSPARENT

        // Wyświetlanie błędu gdy nie zaakceptujemy Regulaminu
        textTermsError = findViewById(R.id.textTermsError)

        val db = AppDatabase.Companion.getDatabase(this)
        val userDao = db.userDao()
        val settingsDao = db.settingsDao()

        // Pokaz/ukryj hasła
        checkboxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                passwordField.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                confirmPasswordField.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                confirmPasswordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordField.setSelection(passwordField.text.length)
            confirmPasswordField.setSelection(confirmPasswordField.text.length)
        }

        // Rejestracja
        registerButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim().lowercase()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val acceptedTerms = checkboxTerms.isChecked

            var isValid = true

            // Reset błędów
            nameField.error = null
            emailField.error = null
            passwordField.error = null
            confirmPasswordField.error = null

            // Walidacja
            if (email.isEmpty()) {
                showFieldError(emailField, "Email jest wymagany")
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFieldError(emailField, "Niepoprawny adres email")
                isValid = false
            }
            if (password.isEmpty()) {
                showFieldError(passwordField, "Wpisz hasło")
                isValid = false
            }
            if (password != confirmPassword) {
                showFieldError(confirmPasswordField, "Hasła się nie zgadzają")
                isValid = false
            }
            if (!acceptedTerms) {
                textTermsError.visibility = View.VISIBLE
                checkboxTerms.setTextColor(Color.RED)
                isValid = false
                return@setOnClickListener //Zatrzyma działanie przycisku
            } else {
                textTermsError.visibility = View.GONE
                checkboxTerms.setTextColor(Color.BLACK)
            }

            if (!isPasswordValid(password)) {
                showFieldError(passwordField, "Hasło nie spełnia wymagań!")
                isValid = false
            }
            if (!isValid) return@setOnClickListener

            // Loader
            progressBar.visibility = View.VISIBLE
            registerButton.isEnabled = false

            lifecycleScope.launch {
                val existingUser = withContext(Dispatchers.IO) {
                    userDao.getUserByUsername(email)
                }
                if (existingUser != null) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        registerButton.isEnabled = true
                        emailField.error = "Email już istnieje"
                    }
                } else {
                    // Tworzymy nowego użytkownika
                    val currentTime = System.currentTimeMillis()
                    val safeName = name.trim().take(20)
                    val newUser = User(
                        id = 0,
                        name = safeName,
                        username = email,
                        password = password,
                        createdAt = currentTime,
                        lastLogin = currentTime
                    )

                    // Wstawienie usera i pobranie ID
                    val userId = withContext(Dispatchers.IO) {
                        userDao.insertUser(newUser).toInt()
                    }

                    // Zapisz USER_ID do SharedPreferences
                    withContext(Dispatchers.Main) {
                        Prefs.setUserId(this@RegisterActivity, userId)
                    }

                    // Tworzymy domyślne ustawienia dla nowego użytkownika
                    val defaultSettings = Settings(
                        id = 0,
                        userId = userId,
                        categories = "[\"Jedzenie\",\"Transport\",\"Rachunki\",\"Rozrywka\",\"Inne\"]",
                        currency = "PLN",
                        period = "Miesięczny",
                        savingsGoal = 0.0
                    )
                    withContext(Dispatchers.IO) {
                        settingsDao.insertSettings(defaultSettings)
                    }

                    //Tworzymy też początkowy rekord w MonthlyBudget z budżetem = 0.0
                    val monthlyBudgetDao = db.monthlyBudgetDao()
                    val currentDate = Calendar.getInstance()
                    val currentYear = currentDate.get(Calendar.YEAR)
                    val currentMonth = currentDate.get(Calendar.MONTH) + 1

                    val newBudget = MonthlyBudget(
                        userId = userId,
                        year = currentYear,
                        month = currentMonth,
                        budget = 0.0,
                        isDefault = false
                    )
                    withContext(Dispatchers.IO) {
                        monthlyBudgetDao.insertBudget(newBudget)
                    }

                    //Zapisz domyślny motyw dla nowego użytkownika (jasny)
                    Prefs.setAppTheme(this@RegisterActivity, "light")

                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        registerButton.isEnabled = true
                        Toast.makeText(this@RegisterActivity, "✅ Konto utworzone", Toast.LENGTH_SHORT).show()

                        // Przenosimy do Dashboard (USER_ID już zapisany w prefs)
                        val intent = Intent(this@RegisterActivity, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        // Powrót do logowania
        loginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // wraca do LoginActivity
        }
    }

    private fun showFieldError(editText: EditText, message:String) {
        editText.setBackgroundResource(R.drawable.shape_search_border_error)
        editText.error = message

        editText.postDelayed({
            editText.setBackgroundResource(R.drawable.shape_search_border)
        }, 1500)
    }

    private fun isPasswordValid(password: String): Boolean {
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$")
        return regex.matches(password)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                checkboxTerms.isChecked = true
                Toast.makeText(this, "Regulamin został zaakceptowany ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }
}