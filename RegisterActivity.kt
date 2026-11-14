package com.example.samplee.ui

import android.os.Bundle
import android.util.Patterns
import com.example.samplee.databinding.ActivityRegisterBinding

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvGoLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        when {
            name.length < 2 -> binding.tilFullName.error = "Enter your full name"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> binding.tilEmail.error = "Enter a valid email"
            phone.length < 7 -> binding.tilPhone.error = "Enter a valid phone number"
            password.length < 6 -> binding.tilPassword.error = "Use at least 6 characters"
            password != confirm -> binding.tilConfirmPassword.error = "Passwords do not match"
            else -> {
                if (db.registerUser(name, email, phone, password)) {
                    toast("Account created. Please log in.")
                    finish()
                } else {
                    binding.tilEmail.error = "This email is already registered"
                }
            }
        }
    }
}
