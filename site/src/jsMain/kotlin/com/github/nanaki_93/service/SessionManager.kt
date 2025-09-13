package com.github.nanaki_93.service


object SessionManager {
    var onSessionExpired: (() -> Unit)? = null

    fun handleSessionExpired() {
        // Show popup
        kotlinx.browser.window.alert("Your session has expired. You will be redirected to the login page.")

        // Call the callback if set
        onSessionExpired?.invoke()

        // Redirect to login
        kotlinx.browser.window.location.href = "/hiragame/login"
    }
}


