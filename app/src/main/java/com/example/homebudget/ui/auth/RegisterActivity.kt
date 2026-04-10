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
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.entity.User
import com.example.homebudget.data.remote.AuthRepository
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.settings.Prefs
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
            emailField.error = null
            passwordField.error = null
            confirmPasswordField.error = null

            if (email.isEmpty()) {
                showFieldError(emailField, "Email jest wymagany")
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFieldError(emailField, "Niepoprawny adres email")
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
                        emailField.error = "Email juz istnieje"
                    }
                } else {
                    val supaResult = AuthRepository.signUp(email, password)

                    if (supaResult.isFailure) {
                        withContext(Dispatchers.Main) {
                            progressBar.visibility = View.GONE
                            registerButton.isEnabled = true
                            Toast.makeText(
                                this@RegisterActivity,
                                "Supabase: ${supaResult.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
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
                        progressBar.visibility = View.GONE
                        registerButton.isEnabled = true
                        Toast.makeText(this@RegisterActivity, "Konto utworzone", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@RegisterActivity, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        loginText.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
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
