package com.example.homebudget.utils.money

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("pl-PL")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    private val formatter = DecimalFormat("#,##0.00", symbols)

    fun format(value: Double): String {
        return formatter.format(value)
    }

    fun formatWithCurrency(value: Double): String {
        return formatter.format(value) + " zł"
    }
}