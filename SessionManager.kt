package com.example.samplee.util

import android.content.Context
import com.example.samplee.model.User

/** Remembers who is logged in (survives app restarts until Logout). */
class SessionManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("parkonspot_session", Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        prefs.edit()
            .putLong(KEY_ID, user.id)
            .putString(KEY_NAME, user.fullName)
            .apply()
    }

    val isLoggedIn: Boolean get() = prefs.getLong(KEY_ID, -1L) != -1L
    val userId: Long get() = prefs.getLong(KEY_ID, -1L)
    val userName: String get() = prefs.getString(KEY_NAME, "") ?: ""

    fun logout() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ID = "user_id"
        const val KEY_NAME = "user_name"
    }
}
