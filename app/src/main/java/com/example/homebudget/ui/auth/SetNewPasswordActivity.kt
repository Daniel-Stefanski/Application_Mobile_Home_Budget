package com.example.homebudget.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SetNewPasswordActivity : AppCompatActivity() {
    private lateinit var editTextNewPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var checkboxShowPassword: CheckBox
    private lateinit var buttonSetPassword: Button
    private lateinit var textBackToLogin: TextView
    private lateinit var progressBar: ProgressBar

    private var resetToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_new_password)

        editTextNewPassword = findViewById(R.id.editTextNewPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        checkboxShowPassword = findViewById(R.id.checkboxShowPassword)
        buttonSetPassword = findViewById(R.id.buttonSetPassword)
        textBackToLogin = findViewById(R.id.textBackToLogin)
        progressBar = findViewById(R.id.progressBar)

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

        resetToken = intent?.data?.getQueryParameter("token")

        if (resetToken.isNullOrBlank()) {
            Toast.makeText(this, "Niepoprawny link resetu hasła", Toast.LENGTH_LONG).show()
            buttonSetPassword.isEnabled = false
        }

        checkboxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editTextNewPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                editTextConfirmPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editTextNewPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                editTextConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            editTextNewPassword.setSelection(editTextNewPassword.text.length)
            editTextConfirmPassword.setSelection(editTextConfirmPassword.text.length)
        }

        buttonSetPassword.setOnClickListener {
            val newPassword = editTextNewPassword.text.toString()
            val confirmPassword = editTextConfirmPassword.text.toString()

            editTextNewPassword.error = null
            editTextConfirmPassword.error = null

            if (resetToken.isNullOrBlank()) {
                Toast.makeText(this, "Brak tokena resetu hasła", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                if (newPassword.isEmpty()) showFieldError(editTextNewPassword, "Wpisz nowe hasło")
                if (confirmPassword.isEmpty()) showFieldError(editTextConfirmPassword, "Powtórz hasło")
                Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                showFieldError(editTextNewPassword, "Hasła się nie zgadzają")
                showFieldError(editTextConfirmPassword, "Hasła się nie zgadzają")
                Toast.makeText(this, "Hasła się nie zgadzają", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isPasswordValid(newPassword)) {
                showFieldError(editTextNewPassword, "Hasło nie spełnia wymagań")
                Toast.makeText(this, "Hasło nie spełnia wymagań", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendChangePasswordRequest(resetToken!!, newPassword)
        }

        textBackToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun sendChangePasswordRequest(token: String, newPassword: String) {
        buttonSetPassword.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://jojigot576.app.n8n.cloud/webhook/set-new-password")
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000

                    val jsonBody = JSONObject().apply {
                        put("token", token)
                        put("newPassword", newPassword)
                    }

                    BufferedWriter(OutputStreamWriter(connection.outputStream)).use { writer ->
                        writer.write(jsonBody.toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                            ?: "Nie udało się zmienić hasła."
                    }
                    Pair(responseCode, responseText)
                } catch (e: Exception) {
                    null
                }
            }

            progressBar.visibility = View.GONE
            buttonSetPassword.isEnabled = true

            if (result == null) {
                Toast.makeText(
                    this@SetNewPasswordActivity,
                    "Błąd połączenia. Sprawdź internet i spróbuj ponownie.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val (responseCode, responseText) = result

            if (responseCode in 200..299) {
                syncLocalPasswordIfPossible(newPassword)
                onPasswordChangedSuccessfully()
            } else {
                showServerError(responseText)
            }
        }
    }

    private fun onPasswordChangedSuccessfully() {
        // Po zmianie hasła czyścimy lokalną sesję.
        Prefs.clearPendingPasswordResetEmail(this)
        Prefs.clearSession(this)

        AlertDialog.Builder(this)
            .setTitle("Sukces")
            .setMessage("Hasło zostało zmienione. Możesz zalogować się nowym hasłem.")
            .setCancelable(false)
            .setPositiveButton("Przejdź do logowania") { _, _ ->
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    private fun showServerError(responseText: String) {
        val message = try {
            val json = JSONObject(responseText)
            json.optString("message", "Nie udało się zmienić hasła.")
        } catch (_: Exception) {
            "Nie udało się zmienić hasła."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showFieldError(editText: EditText, message: String) {
        editText.setBackgroundResource(R.drawable.shape_search_border_error)
        editText.error = message

        editText.postDelayed({
            editText.setBackgroundResource(R.drawable.shape_search_border)
        }, 1500)
    }

    private fun isPasswordValid(password: String): Boolean {
        val regex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$")
        return regex.matches(password)
    }

    private suspend fun syncLocalPasswordIfPossible(newPassword: String) {
        val email = Prefs.getPendingPasswordResetEmail(this)?.trim()?.lowercase()
            ?: return

        withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(this@SetNewPasswordActivity)
                .userDao()
                .updatePassword(email, newPassword)
        }
    }
}
