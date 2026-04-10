package com.example.homebudget.ui.auth

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.homebudget.R

// TermsActivity.kt - ekran z regulaminem aplikacji.
class TermsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)

        title = "Regulamin aplikacji"

        val textTerms: TextView = findViewById(R.id.textTerms)
        textTerms.text = """
        Regulamin aplikacji HomeBudget
        
        1. Aplikacja HomeBudget służy do zarządzania budżetem domowym oraz planowania wydatków.
        2. Wszystkie dane użytkownika są przechowywane lokalnie na jego urządzeniu. Aplikacja może korzystać z usług chmurowych zgodnie z jej funkcjonalnością.
        3. Użytkownik ponosi pełną odpowiedzialność za wprowadzane dane oraz ich poprawność.
        4. Twórca aplikacji nie ponosi odpowiedzialności za ewentualne straty finansowe wynikające z korzystania z aplikacji.
        5. Aplikacja ma charakter pomocniczy i nie zastępuje profesjonalnych usług księgowych ani doradczych.
        6. Użytkownik jest odpowiedzialny za wykonywanie kopii zapasowych swoich danych. Usunięcie aplikacji może skutkować utratą danych lokalnych.
        7. Aplikacja może być rozwijana i zmieniana w przyszłości, co może wiązać się ze zmianą funkcjonalności.
        8. Korzystanie z aplikacji oznacza akceptację niniejszego regulaminu.
        
        Klikając „Akceptuję” wyrażasz zgodę na powyższe warunki.
        """.trimIndent()

        val buttonClose: Button = findViewById(R.id.buttonClose)
        val buttonAccept: Button = findViewById(R.id.buttonAccept)

        buttonClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        buttonAccept.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }
}
