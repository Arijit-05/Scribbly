package com.arijit.notes.utils

interface AuthListener {
    fun onRegisterSubmit(name: String, email: String, pin: String)
    fun onLoginSubmit(email: String, pin: String)
}