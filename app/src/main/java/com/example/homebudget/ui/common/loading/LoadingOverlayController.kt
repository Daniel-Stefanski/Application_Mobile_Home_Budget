package com.example.homebudget.ui.common.loading

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.homebudget.R

class LoadingOverlayController(
    private val activity: AppCompatActivity
) {
    private var overlayRoot: FrameLayout? = null
    private var overlayText: TextView? = null
    private var activeRequests = 0

    fun show(message: String = "Ładowanie danych...") {
        if (activity.isFinishing || activity.isDestroyed) return

        activeRequests += 1
        ensureOverlay()
        overlayText?.text = message
        overlayRoot?.visibility = View.VISIBLE
        overlayRoot?.bringToFront()
    }

    fun hide() {
        if (activeRequests > 0) {
            activeRequests -= 1
        }

        if (activeRequests == 0) {
            overlayRoot?.visibility = View.GONE
        }
    }

    fun forceHide() {
        activeRequests = 0
        overlayRoot?.visibility = View.GONE
    }

    private fun ensureOverlay() {
        if (overlayRoot != null) return

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#66000000"))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        val card = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setBackgroundResource(R.drawable.bg_summary_card)
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            elevation = 12f
        }

        val loadingView = activity.layoutInflater.inflate(R.layout.dialog_loading, card, false)
        overlayText = loadingView.findViewById(R.id.loadingText)
        card.addView(loadingView)
        container.addView(card)
        contentRoot.addView(container)

        overlayRoot = container
    }
}
