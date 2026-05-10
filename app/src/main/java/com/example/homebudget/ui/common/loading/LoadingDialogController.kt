package com.example.homebudget.ui.common.loading

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.homebudget.R

class LoadingDialogController(
    private val activity: AppCompatActivity
) {
    private var dialog: AlertDialog? = null
    private var activeRequests = 0

    fun show(message: String = "Przetwarzanie...") {
        if (activity.isFinishing || activity.isDestroyed) return

        activeRequests += 1
        if (dialog == null) {
            val loadingView = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
            loadingView.findViewById<TextView>(R.id.loadingText).text = message
            dialog = AlertDialog.Builder(activity)
                .setView(loadingView)
                .setCancelable(false)
                .create()
        } else {
            dialog?.findViewById<TextView>(R.id.loadingText)?.text = message
        }

        if (dialog?.isShowing != true) {
            dialog?.show()
        }
    }

    fun hide() {
        if (activeRequests > 0) {
            activeRequests -= 1
        }

        if (activeRequests == 0) {
            dialog?.dismiss()
            dialog = null
        }
    }

    fun forceHide() {
        activeRequests = 0
        dialog?.dismiss()
        dialog = null
    }
}
