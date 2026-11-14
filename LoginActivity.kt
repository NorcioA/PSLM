package com.example.samplee.ui

import android.content.Intent
import android.os.Bundle
import com.example.samplee.databinding.ActivityLoginBinding

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in? Skip straight to the map.
        if (session.isLoggedIn) {
            openDashboard()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (email.isEmpty()) {
            binding.tilEmail.error = "Enter your email"
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Enter your password"
            return
        }

        val user = db.loginUser(email, password)
        if (user == null) {
            binding.tilPassword.error = "Wrong email or password"
            return
        }
        session.saveUser(user)
        openDashboard()
    }

    private fun openDashboard() {
        startActivity(
            Intent(this, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
