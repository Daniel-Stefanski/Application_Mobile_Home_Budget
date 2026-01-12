package com.example.homebudget.notifications

import android.R.drawable
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.homebudget.ui.billsplanner.BillsPlannerActivity
import com.example.homebudget.ui.dashboard.DashboardActivity
import com.example.homebudget.ui.savings.SavingsActivity

//NotificationHelper.kt – obsługuje tworzenie i wysyłanie powiadomień o rachunkach.
object NotificationHelper {
    const val CHANNEL_ID_BILLS = "bills_channel"
    const val CHANNEL_NAME_BILLS = "Przypomnienia rachunków"

    const val CHANNEL_ID_SAVINGS = "savings_channel"
    const val CHANNEL_NAME_SAVINGS = "Przypomnienia celów oszczędnościowych"

    const val CHANNEL_ID_BUDGET = "budget_channel"
    const val CHANNEL_NAME_BUDGET = "Powiadomienia o budżecie"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Kanał rachunków
            val billsChannel = NotificationChannel(
                CHANNEL_ID_BILLS,
                CHANNEL_NAME_BILLS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Powiadomienia o nadchodzących rachunkach"
            }
            manager.createNotificationChannel(billsChannel)

            // Kanał celów oszczędnościowych
            val savingsChannel = NotificationChannel(
                CHANNEL_ID_SAVINGS,
                CHANNEL_NAME_SAVINGS,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Powiadomienia o zbliżających się terminach celów oszczędnościowych"
            }
            manager.createNotificationChannel(savingsChannel)

            // Kanał budżetu dashboardu
            val budgetChannel = NotificationChannel(
                CHANNEL_ID_BUDGET,
                CHANNEL_NAME_BUDGET,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Powiadomienia o przekroczeniu budżetu miesięcznego"
            }
            manager.createNotificationChannel(budgetChannel)
        }
    }

    private fun defaultPendingIntent(context: Context, target: Class<*>): PendingIntent {
        val intent = Intent(context, target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun buildBillNotification(context: Context, title: String, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_BILLS)
            .setSmallIcon(drawable.ic_dialog_info) // zmień na swój ikon resource
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(defaultPendingIntent(context, BillsPlannerActivity::class.java))
            .setAutoCancel(true)
            .build()
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, buildBillNotification(context, title, text))
    }

    fun buildSavingsNotification(context: Context, title: String, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_SAVINGS)
            .setSmallIcon(drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(defaultPendingIntent(context, SavingsActivity::class.java))
            .setAutoCancel(true)
            .build()
    }

    fun notifySavings(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, buildSavingsNotification(context, title, text))
    }

    fun buildBudgetNotification(context: Context, title: String, text: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(defaultPendingIntent(context, DashboardActivity::class.java))
            .setAutoCancel(true)
            .build()
    }

    fun notifyBudget(context: Context, id: Int, title: String, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, buildBudgetNotification(context, title, text))
    }
}