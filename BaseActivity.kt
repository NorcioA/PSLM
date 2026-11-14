package com.example.samplee.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.samplee.db.DatabaseHelper
import com.example.samplee.util.SessionManager

/** Keys used to pass data between screens. */
object Extras {
    const val SPACE_ID = "extra_space_id"
    const val BOOKING_ID = "extra_booking_id"
}

/**
 * INHERITANCE: every screen shares the database, the login session and toast().
 * Written once here, reused by all activities.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val db: DatabaseHelper by lazy { DatabaseHelper.getInstance(this) }
    protected val session: SessionManager by lazy { SessionManager(this) }

    protected fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/** A screen that only a logged-in user may open. Sends everyone else to Login. */
abstract class AuthenticatedActivity : BaseActivity() {

    protected val userId: Long get() = session.userId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.isLoggedIn) goToLogin()
    }

    protected fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
