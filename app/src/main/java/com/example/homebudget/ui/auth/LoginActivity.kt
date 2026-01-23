package com.example.homebudget.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.R
import com.example.homebudget.data.dao.UserDao
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.User
import com.example.homebudget.data.remote.AuthRepository
import com.example.homebudget.data.remote.testFetchExpenses
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.scheduler.WorkScheduler
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//LoginActivity.kt (wcześniej MainActivity) – ekran logowania do aplikacji.
class LoginActivity : AppCompatActivity() {

    private lateinit var userDao: UserDao
    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonLogin: Button
    private lateinit var textRegister: TextView
    private lateinit var textForgotPassword: TextView
    private lateinit var textLoginError: TextView
    private lateinit var checkBoxRememberMe: CheckBox
    private lateinit var checkBoxShowPassword: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Utworzenie kanału powiadomień (Android 8+)
        NotificationHelper.createNotificationChannel(this)
        WorkScheduler.scheduleDailyCheck(this)

        // Ekran logowania zawsze będzie miał motyw jasny
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Sprawdzenie, czy użytkownik ma ustawione "Zapamiętaj mnie"
        val rememberMe = Prefs.isRememberMeEnabled(this)
        val savedUserId = Prefs.getUserId(this)

        if (rememberMe && savedUserId != -1) {
            // Automatyczne logowanie
            val intent = Intent(this, DashboardActivity::class.java)
            intent.putExtra("USER_ID", savedUserId)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        userDao = AppDatabase.Companion.getDatabase(this).userDao()

        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        textRegister = findViewById(R.id.textRegister)
        textForgotPassword = findViewById(R.id.textForgotPassword)
        textLoginError = findViewById(R.id.textLoginError)
        checkBoxRememberMe = findViewById(R.id.checkBoxRememberMe)
        checkBoxShowPassword = findViewById(R.id.checkboxShowPassword)

        //Checkbox pokaż/ukryj hasło
        checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            //Ustaw kursor na końcu po zmianie typu
            editTextPassword.setSelection(editTextPassword.text.length)
        }

        //Podkreślenie linków przejścia rejestracji i resetu
        textRegister.paint.isUnderlineText = true
        textForgotPassword.paint.isUnderlineText = true

        // Przejście do rejestracji
        textRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Przejście do resetu hasła
        textForgotPassword.setOnClickListener {
            val intent = Intent(this, ResetPasswordActivity::class.java)
            startActivity(intent)
        }

        // Logowanie
        buttonLogin.setOnClickListener {
            // Zabezpieczenie przed niechcianym miganie komunikatu
            textLoginError.visibility = View.GONE
            val email = editTextEmail.text.toString().trim().lowercase()
            val password = editTextPassword.text.toString()

            lifecycleScope.launch {
                // Logowanie w Supabase
                val result = AuthRepository.signIn(email, password)
                if (result.isFailure) {
                    withContext(Dispatchers.Main) {
                        textLoginError.visibility = View.VISIBLE
                        Toast.makeText(
                            this@LoginActivity,
                            "❌ Nieprawidłowy email lub hasło",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                // Sukces logowania
                val supabaseUser = result.getOrThrow()
                val supabaseUid = supabaseUser.id
                // Zapisz Supabase_UID
                Prefs.setSupabaseUid(this@LoginActivity, supabaseUid)
                WorkSchedulerSupabase.scheduleSupabaseSync(this@LoginActivity)

                // Sprawdź lokalnego usera (Room)
                var localUser = withContext(Dispatchers.IO) {
                    userDao.getUserByUsername(email)
                }
                // Jeśli brak -> twórz lokalnego usera (office cache)
                val userId = if (localUser == null) {
                    val now = System.currentTimeMillis()
                    val newUser = User(
                        username = email,
                        password = password,
                        name = "",
                        createdAt = now,
                        lastLogin = now
                    )
                    withContext(Dispatchers.IO) {
                        userDao.insertUser(newUser).toInt()
                    }
                } else {
                    // Update last login
                    withContext(Dispatchers.IO) {
                        userDao.updateLastLogin(localUser.id, System.currentTimeMillis())
                    }
                    localUser.id
                }
                // Zapisz USER_ID i Remember me
                Prefs.setUserId(this@LoginActivity, userId)
                Prefs.setRememberMe(this@LoginActivity, checkBoxRememberMe.isChecked)
                // Dashboard
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                    finish()
                }
            }
        }
        lifecycleScope.launch {
            val result = testFetchExpenses()
            if (result.isSuccess) {
                Toast.makeText(this@LoginActivity, "✅ Supabase OK", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@LoginActivity, "❌ ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}