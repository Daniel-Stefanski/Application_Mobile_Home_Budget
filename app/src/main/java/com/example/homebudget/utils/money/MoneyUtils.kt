package com.example.homebudget.utils.money

import java.text.NumberFormat
import java.util.Locale

object MoneyUtils {
    private val polishLocale = Locale.forLanguageTag("pl-PL")

    // Parsuje kwotę w formacie PL (obsługuje spacje, przecinki, NBSP, separatory tysięcy)
    fun parseAmount(input: String): Double? {
        if (input.isBlank()) return null

        val cleaned = input
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace(",", ".")   // zamiana , na .
            .replace(Regex("[^0-9.]"), "") // usuwa separator tysięcy np. 1.200,50 → 1200.50

        return cleaned.toDoubleOrNull()
    }

    //Formatuje kwotę jako “1 234,56”
    fun formatAmount(value: Double): String {
        val nf = NumberFormat.getNumberInstance(polishLocale)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        return nf.format(value)
    }
}