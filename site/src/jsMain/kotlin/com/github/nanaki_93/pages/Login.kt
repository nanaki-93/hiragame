package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.components.widgets.SessionExpiredAlert
import com.github.nanaki_93.components.widgets.auth.*
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.SessionManager
import com.github.nanaki_93.util.launchSafe
import com.varabyte.kobweb.core.Page
import io.ktor.http.*
import org.jetbrains.compose.web.dom.Form

@Page("/login")
@Composable
fun LoginPage() {
    val authService = remember { AuthService() }

    // Check if already logged in
    LaunchedEffect(Unit) {
        try {
            if (authService.isAuthenticated()) {
                kotlinx.browser.window.location.href = "/hiragame"
            }
        } catch (e: Exception) {
            println("Failed to check authentication: ${e.message}")
        }
    }

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showAlert by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    SessionManager.onSessionExpired = {
        showAlert = true
    }

    suspend fun handleLogin() {
        if (name.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }

        isLoading = true
        errorMessage = ""

        try {
            if (authService.login(name, password) != HttpStatusCode.OK) {
                errorMessage = "Invalid credentials"
                return
            }
            kotlinx.browser.window.location.href = "/hiragame"
        } catch (e: Exception) {
            errorMessage = "Network error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    suspend fun handleRegister() {
        if (name.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }

        isLoading = true
        errorMessage = ""

        try {
            authService.register(name, password)
            kotlinx.browser.window.location.href = "/hiragame"
        } catch (e: Exception) {
            errorMessage = "Network error: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    if (showAlert) {
        SessionExpiredAlert(
            message = "Your session has expired.",
            onClose = {
                coroutineScope.launchSafe {
                    authService.logout()
                    kotlinx.browser.window.location.href = "/hiragame/login"
                    showAlert = false
                }
            }
        )
    }

    LoginContainer {
        AuthHeader(isLogin = isLogin)

        Form {
            LoginForm(
                name = name,
                password = password,
                isLogin = isLogin,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onNameChange = { name = it },
                onPasswordChange = { password = it },
                onSubmit = {
                    coroutineScope.launchSafe {
                        if (isLogin) handleLogin() else handleRegister()
                    }
                }
            )
        }
        AuthToggle(
            isLogin = isLogin,
            onToggle = { isLogin = !isLogin }
        )
    }
}