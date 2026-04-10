package com.example.homebudget.ui.auth

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.example.homebudget.R
import com.example.homebudget.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ResetPasswordActivity.kt - ekran resetowania hasla.
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var editTextNewPassword: EditText
    private lateinit var editTextConfirmPassword: EditText
    private lateinit var checkboxShowPassword: CheckBox
    private lateinit var buttonResetPassword: Button
    private lateinit var textBackToLogin: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_reset_password)

        editTextEmail = findViewById(R.id.editTextEmail)
        editTextNewPassword = findViewById(R.id.editTextNewPassword)
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
        checkboxShowPassword = findViewById(R.id.checkboxShowPassword)
        buttonResetPassword = findViewById(R.id.buttonResetPassword)
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

        checkboxShowPassword.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editTextNewPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                editTextNewPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            editTextNewPassword.setSelection(editTextNewPassword.text.length)
        }

        val db = AppDatabase.getDatabase(this)
        val userDao = db.userDao()

        buttonResetPassword.setOnClickListener {
            val email = editTextEmail.text.toString().trim().lowercase()
            val newPassword = editTextNewPassword.text.toString()
            val confirmPassword = editTextConfirmPassword.text.toString()

            editTextEmail.error = null
            editTextNewPassword.error = null
            editTextConfirmPassword.error = null

            buttonResetPassword.isEnabled = false
            progressBar.visibility = View.VISIBLE

            if (email.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                highlightErrorFields(email.isEmpty(), newPassword.isEmpty() || confirmPassword.isEmpty())
                Toast.makeText(this, "Wszystkie pola muszą być wypełnione", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                buttonResetPassword.isEnabled = true
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFieldError(editTextEmail, "Niepoprawny adres email")
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                showFieldError(editTextConfirmPassword, "Hasła się nie zgadzają")
                showFieldError(editTextNewPassword, "Hasła się nie zgadzają")
                return@setOnClickListener
            }
            if (!isPasswordValid(newPassword)) {
                showFieldError(editTextNewPassword, "Hasło nie spełnia wymagań")
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val user = withContext(Dispatchers.IO) {
                    userDao.getUserByUsername(email)
                }

                if (user != null) {
                    withContext(Dispatchers.IO) {
                        userDao.updatePassword(email, newPassword)
                    }
                    runOnUiThread {
                        showSuccessDialog()
                        Toast.makeText(this@ResetPasswordActivity, "Hasło zresetowane!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        buttonResetPassword.isEnabled = true
                        highlightErrorFields(true, false)
                        Toast.makeText(
                            this@ResetPasswordActivity,
                            "Nie znaleziono użytkownika o podanym adresie e-mail",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        textBackToLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showSuccessDialog() {
        progressBar.visibility = View.GONE
        buttonResetPassword.isEnabled = true

        AlertDialog.Builder(this)
            .setTitle("Sukces")
            .setMessage("Hasło zostało zresetowane!")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
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

    private fun highlightErrorFields(emailError: Boolean, passwordError: Boolean) {
        if (emailError) editTextEmail.setBackgroundResource(R.drawable.shape_search_border_error)
        if (passwordError) editTextNewPassword.setBackgroundResource(R.drawable.shape_search_border_error)

        editTextEmail.postDelayed({
            editTextEmail.setBackgroundResource(R.drawable.shape_search_border)
            editTextNewPassword.setBackgroundResource(R.drawable.shape_search_border)
        }, 1500)
    }
}
