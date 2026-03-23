package com.example.homebudget.ui.auth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.dao.UserDao
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.User
import com.example.homebudget.data.remote.AuthRepository
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.work.scheduler.WorkScheduler
import com.example.homebudget.work.worker.WorkSchedulerSupabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        NotificationHelper.createNotificationChannel(this)
        WorkScheduler.scheduleDailyCheck(this)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        ensureNotificationPermission()

        restoreSupabaseUidFromSession()

        val rememberMe = Prefs.isRememberMeEnabled(this)
        val savedUserId = Prefs.getUserId(this)
        if (rememberMe && savedUserId != -1) {
            WorkSchedulerSupabase.scheduleSupabaseSync(this)
            startActivity(Intent(this, DashboardActivity::class.java).apply {
                putExtra("USER_ID", savedUserId)
            })
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        userDao = AppDatabase.getDatabase(this).userDao()

        editTextEmail = findViewById(R.id.editTextEmail)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonLogin = findViewById(R.id.buttonLogin)
        textRegister = findViewById(R.id.textRegister)
        textForgotPassword = findViewById(R.id.textForgotPassword)
        textLoginError = findViewById(R.id.textLoginError)
        checkBoxRememberMe = findViewById(R.id.checkBoxRememberMe)
        checkBoxShowPassword = findViewById(R.id.checkboxShowPassword)

        checkBoxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            editTextPassword.inputType = if (isChecked) {
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            editTextPassword.setSelection(editTextPassword.text.length)
        }

        textRegister.paint.isUnderlineText = true
        textForgotPassword.paint.isUnderlineText = true

        textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        textForgotPassword.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        buttonLogin.setOnClickListener {
            textLoginError.visibility = View.GONE
            val email = editTextEmail.text.toString().trim().lowercase()
            val password = editTextPassword.text.toString()

            lifecycleScope.launch {
                val localUser = withContext(Dispatchers.IO) {
                    userDao.getUserByUsername(email)
                }
                val offlineMode = !isNetworkAvailable()

                val result = AuthRepository.signIn(email, password)
                if (result.isFailure) {
                    if (offlineMode && localUser?.password == password) {
                        finishLogin(localUser.id, checkBoxRememberMe.isChecked, offline = true)
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        textLoginError.visibility = View.VISIBLE
                        Toast.makeText(
                            this@LoginActivity,
                            if (offlineMode) {
                                "Brak internetu i brak poprawnych danych lokalnych do logowania"
                            } else {
                                "Nieprawidlowy email lub haslo"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val supabaseUser = result.getOrThrow()
                Prefs.setSupabaseUid(this@LoginActivity, supabaseUser.id)
                WorkSchedulerSupabase.scheduleSupabaseSync(this@LoginActivity)

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
                    withContext(Dispatchers.IO) {
                        userDao.updatePassword(email, password)
                        userDao.updateLastLogin(localUser.id, System.currentTimeMillis())
                    }
                    localUser.id
                }

                finishLogin(userId, checkBoxRememberMe.isChecked, offline = false)
            }
        }
    }

    private suspend fun finishLogin(userId: Int, rememberMe: Boolean, offline: Boolean) {
        withContext(Dispatchers.IO) {
            userDao.updateLastLogin(userId, System.currentTimeMillis())
        }
        Prefs.setUserId(this, userId)
        Prefs.setRememberMe(this, rememberMe)
        WorkSchedulerSupabase.scheduleSupabaseSync(this)

        withContext(Dispatchers.Main) {
            if (offline) {
                Toast.makeText(
                    this@LoginActivity,
                    "Tryb offline: zalogowano na lokalnych danych",
                    Toast.LENGTH_SHORT
                ).show()
            }
            startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
            finish()
        }
    }

    private fun restoreSupabaseUidFromSession() {
        AuthRepository.getCurrentUser()?.id?.let { uid ->
            Prefs.setSupabaseUid(this, uid)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }
}
