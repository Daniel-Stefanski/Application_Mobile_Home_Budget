package com.example.homebudget.utils.color

import com.github.mikephil.charting.utils.ColorTemplate

//ColorUtils.kt – narzędzia do generowania i przypisywania kolorów (np. do wykresów, kategorii).
object ColorUtils {

    fun getRandomColor(): Int {
        val allColors = mutableListOf<Int>(). apply {
            addAll(ColorTemplate.MATERIAL_COLORS.toList())
            addAll(ColorTemplate.VORDIPLOM_COLORS.toList())
            addAll(ColorTemplate.COLORFUL_COLORS.toList())
        }
        return allColors.random()
    }

    fun getRandomColorHex(): String {
        val color = getRandomColor()
        return String.format("#%06X", 0xFFFFFF and color)
    }
}