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
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.scheduler.WorkScheduler
import kotlinx.coroutines.launch

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
            val email = editTextEmail.text.toString()
            val password = editTextPassword.text.toString()

            lifecycleScope.launch {
                val user = userDao.getUserByUsername(email)
                if (user != null && user.password == password) {
                    // Zapisanie preferencji jeśli zaznaczono checkbox
                    if (checkBoxRememberMe.isChecked) {
                        Prefs.setRememberMe(this@LoginActivity, checkBoxRememberMe.isChecked)
                        Prefs.setUserId(this@LoginActivity, user.id)
                    } else {
                        Prefs.setUserId(this@LoginActivity, user.id)
                        Prefs.setRememberMe(this@LoginActivity, checkBoxRememberMe.isChecked)
                    }

                    //Zaktualizuj datę ostatniego logowania
                    lifecycleScope.launch {
                        userDao.updateLastLogin(user.id, System.currentTimeMillis())
                    }

                    val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                    intent.putExtra("USER_ID", user.id)
                    startActivity(intent)
                    finish()
                } else {
                    runOnUiThread {
                        editTextEmail.setBackgroundResource(R.drawable.shape_search_border_error)
                        editTextPassword.setBackgroundResource(R.drawable.shape_search_border_error)
                        // Pokaż komunikat pod formualrzem
                        textLoginError.visibility = View.VISIBLE
                        Toast.makeText(this@LoginActivity, "Nieprawidłowy email lub hasło", Toast.LENGTH_SHORT).show()
                        // Cofnięcie błędów po chwili
                        editTextEmail.postDelayed({
                            editTextEmail.setBackgroundResource(R.drawable.shape_search_border)
                            editTextPassword.setBackgroundResource(R.drawable.shape_search_border)
                            textLoginError.visibility = View.GONE
                        }, 1500)
                    }
                }
            }
        }
    }
}