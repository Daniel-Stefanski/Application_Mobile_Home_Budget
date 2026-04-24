package com.example.homebudget.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ResetPasswordActivity.kt - ekran resetowania hasla.
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var buttonResetPassword: Button
    private lateinit var textBackToLogin: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        editTextEmail = findViewById(R.id.editTextEmail)
        buttonResetPassword = findViewById(R.id.buttonResetPassword)
        textBackToLogin = findViewById(R.id.textBackToLogin)
        progressBar = findViewById(R.id.progressBar)

        buttonResetPassword.setOnClickListener {
            val email = editTextEmail.text.toString().trim().lowercase()

            editTextEmail.error = null

            if (email.isEmpty()) {
                showFieldError(editTextEmail, "Email jest wymagany")
                Toast.makeText(this, "Podaj adres email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFieldError(editTextEmail, "Niepoprawny adres email")
                Toast.makeText(this, "Wpisz poprawny adres email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendResetRequest(email)
        }

        textBackToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun sendResetRequest(email: String) {
        buttonResetPassword.isEnabled = false
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://jojigot576.app.n8n.cloud/webhook/request-password-reset")
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000

                    val jsonBody = JSONObject().apply {
                        put("email", email)
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
                            ?: "Błąd połączenia z serwerem. Przepraszamy"
                    }

                    Result.success(responseText)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            progressBar.visibility = View.GONE
            buttonResetPassword.isEnabled = true

            if (result.isSuccess) {
                Prefs.setPendingPasswordResetEmail(this@ResetPasswordActivity, email)
                showSuccessDialog()
            } else {
                Toast.makeText(this@ResetPasswordActivity, "Nie udało się wysyłać zgłoszenie resetu. Sprawdź internet i spróbuj ponownie.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSuccessDialog(){
        AlertDialog.Builder(this)
            .setTitle("Sprawdź pocztę")
            .setMessage("Jeśli konto istnieje, wysłaliśmy email z linkiem do autoryzacji resetu hasła.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFieldError(editText: EditText, message: String) {
        editText.setBackgroundResource(R.drawable.shape_search_border_error)
        editText.error = message

        editText.postDelayed({
            editText.setBackgroundResource(R.drawable.shape_search_border)
        }, 1500)
    }
}
