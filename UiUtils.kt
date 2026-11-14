package com.example.samplee.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.AdapterView
import android.widget.TextView

object UiUtils {

    /** Turns a TextView into a coloured pill (used for status / type badges). */
    fun styleBadge(view: TextView, colorHex: String) {
        val density = view.resources.displayMetrics.density
        val background = GradientDrawable().apply {
            cornerRadius = 40f * density
            setColor(Color.parseColor(colorHex))
        }
        view.background = background
        view.setTextColor(Color.WHITE)
        val h = (10 * density).toInt()
        val v = (4 * density).toInt()
        view.setPadding(h, v, h, v)
    }

    /** Spinner listener that only cares about "something was selected". */
    fun onSelected(action: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            action()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}
