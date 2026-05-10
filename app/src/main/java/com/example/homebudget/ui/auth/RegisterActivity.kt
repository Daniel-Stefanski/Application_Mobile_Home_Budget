package com.example.homebudget.ui.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
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
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.entity.User
import com.example.homebudget.data.remote.AuthRepository
import com.example.homebudget.ui.common.loading.LoadingDialogController
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.utils.settings.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// RegisterActivity.kt - ekran rejestracji nowego uzytkownika.
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
    private lateinit var loadingDialog: LoadingDialogController

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameField = findViewById(R.id.editTextName)
        emailField = findViewById(R.id.editTextEmail)
        passwordField = findViewById(R.id.editTextPassword)
        confirmPasswordField = findViewById(R.id.editTextConfirmPassword)
        checkboxShowPassword = findViewById(R.id.checkboxShowPassword)
        checkboxTerms = findViewById(R.id.checkBoxTerms)
        registerButton = findViewById(R.id.buttonRegister)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.GONE
        loadingDialog = LoadingDialogController(this)
        loginText = findViewById(R.id.textLogin)
        findViewById<View>(R.id.imagePasswordInfo).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Wymagania hasla")
                .setMessage(
                    """
                    • minimum 8 znakow
                    • co najmniej 1 mala litera
                    • co najmniej 1 duza litera
                    • co najmniej 1 cyfra
                    • co najmniej 1 znak specjalny
                    """.trimIndent()
                )
                .setPositiveButton("OK", null)
                .show()
        }

        val termsText = "Akceptuje Regulamin"
        val spannable = SpannableString(termsText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@RegisterActivity, TermsActivity::class.java)
                startActivityForResult(intent, 1001)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = Color.parseColor("#1976D2")
                ds.isUnderlineText = true
            }
        }

        spannable.setSpan(clickableSpan, 10, termsText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        checkboxTerms.text = spannable
        checkboxTerms.movementMethod = LinkMovementMethod.getInstance()
        checkboxTerms.highlightColor = Color.TRANSPARENT

        textTermsError = findViewById(R.id.textTermsError)

        val db = AppDatabase.getDatabase(this)
        val userDao = db.userDao()
        val settingsDao = db.settingsDao()

        emailField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (emailField.error != null) {
                    resetEmailErrorState()
                }
            }
        })

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

        registerButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim().lowercase()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val acceptedTerms = checkboxTerms.isChecked

            var isValid = true

            nameField.error = null
            resetEmailErrorState()
            passwordField.error = null
            confirmPasswordField.error = null

            if (email.isEmpty()) {
                showEmailError("Email jest wymagany")
                isValid = false
            } else if (!isEmailValid(email)) {
                showEmailError("Niepoprawny adres email")
                isValid = false
            }
            if (password.isEmpty()) {
                showFieldError(passwordField, "Wpisz haslo")
                isValid = false
            }
            if (password != confirmPassword) {
                showFieldError(confirmPasswordField, "Hasla sie nie zgadzaja")
                isValid = false
            }
            if (!acceptedTerms) {
                textTermsError.visibility = View.VISIBLE
                checkboxTerms.setTextColor(Color.RED)
                isValid = false
                return@setOnClickListener
            } else {
                textTermsError.visibility = View.GONE
                checkboxTerms.setTextColor(Color.BLACK)
            }

            if (!isPasswordValid(password)) {
                showFieldError(passwordField, "Haslo nie spelnia wymagan!")
                isValid = false
            }
            if (!isValid) return@setOnClickListener

            registerButton.isEnabled = false
            loadingDialog.show("Tworzenie konta...")

            lifecycleScope.launch {
                try {
                    val existingUser = withContext(Dispatchers.IO) {
                        userDao.getUserByUsername(email)
                    }
                    if (existingUser != null) {
                        runOnUiThread {
                            registerButton.isEnabled = true
                            showEmailError("Email juz istnieje")
                        }
                    } else {
                        val supaResult = AuthRepository.signUp(email, password)

                        if (supaResult.isFailure) {
                            val exception = supaResult.exceptionOrNull()
                            withContext(Dispatchers.Main) {
                                registerButton.isEnabled = true
                                if (isEmailAlreadyRegisteredError(exception)) {
                                    showEmailError("Email juz istnieje")
                                } else {
                                    Toast.makeText(
                                        this@RegisterActivity,
                                        "Supabase: ${exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            return@launch
                        }

                        val supabaseUser = supaResult.getOrThrow()
                        val supabaseUid = supabaseUser.id

                        withContext(Dispatchers.Main) {
                            Prefs.setSupabaseUid(this@RegisterActivity, supabaseUid)
                        }

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

                        val userId = withContext(Dispatchers.IO) {
                            userDao.insertUser(newUser).toInt()
                        }

                        withContext(Dispatchers.Main) {
                            Prefs.setUserId(this@RegisterActivity, userId)
                            Prefs.setAppThemeForUser(this@RegisterActivity, userId, "light")
                            ThemeHelper.applySavedTheme(Prefs.getAppTheme(this@RegisterActivity))
                        }

                        val defaultSettings = Settings(
                            userId = userId,
                            categories = "[\"Jedzenie\",\"Transport\",\"Rachunki\",\"Rozrywka\",\"Inne\"]",
                            currency = "PLN",
                            period = "Miesieczny",
                            savingsGoal = 0.0
                        )
                        withContext(Dispatchers.IO) {
                            settingsDao.insertSettings(defaultSettings)
                        }

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

                        runOnUiThread {
                            registerButton.isEnabled = true
                            Toast.makeText(this@RegisterActivity, "Konto utworzone", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@RegisterActivity, DashboardActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                } finally {
                    loadingDialog.hide()
                    registerButton.isEnabled = true
                }
            }
        }

        loginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showEmailError(message: String) {
        emailField.setBackgroundResource(R.drawable.shape_search_border_error)
        emailField.error = message
    }

    private fun resetEmailErrorState() {
        emailField.error = null
        emailField.setBackgroundResource(R.drawable.shape_search_border)
    }

    private fun showFieldError(editText: EditText, message: String) {
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

    private fun isEmailValid(email: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) return false
        if (email.contains("..")) return false

        val parts = email.split("@")
        if (parts.size != 2) return false

        val localPart = parts[0]
        val domainPart = parts[1]

        return localPart.isNotEmpty() &&
            domainPart.isNotEmpty() &&
            !localPart.startsWith(".") &&
            !localPart.endsWith(".") &&
            !domainPart.startsWith(".") &&
            !domainPart.endsWith(".")
    }

    private fun isEmailAlreadyRegisteredError(throwable: Throwable?): Boolean {
        val message = throwable?.message?.lowercase() ?: return false
        return message.contains("already registered") ||
            message.contains("email exists") ||
            message.contains("email already exists") ||
            message.contains("email address already registered") ||
            message.contains("user already registered") ||
            message.contains("duplicate key")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                checkboxTerms.isChecked = true
                Toast.makeText(this, "Regulamin zostal zaakceptowany", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
