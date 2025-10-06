package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import com.github.nanaki_93.components.styles.Styles
import com.github.nanaki_93.components.widgets.*
import com.github.nanaki_93.config.ConfigLoader
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.SessionManager
import com.github.nanaki_93.util.launchSafe
import com.varabyte.kobweb.compose.css.AlignItems
import com.varabyte.kobweb.compose.css.JustifyContent
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import io.ktor.http.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Form

@Page("/login")
@Composable
fun LoginPage() {
    var appConfig by remember { mutableStateOf(ConfigLoader.getDefaultConfig()) }
    var isConfigLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appConfig = ConfigLoader.loadConfig()
        isConfigLoaded = true
    }
    val authService = remember(appConfig) { AuthService(appConfig) }

    // Check if already logged in
    LaunchedEffect(isConfigLoaded) {
        if (!isConfigLoaded) return@LaunchedEffect
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

    Box(
        Styles.GameContainer.toModifier()
            .display(DisplayStyle.Flex)
            .alignItems(AlignItems.Center)
            .justifyContent(JustifyContent.Center)
            .minHeight(100.vh)
    ) {
        CenterColumn(
            spacing = 1.5.cssRem,
            modifier = Styles.Card.toModifier()
                .width(400.px)
                .padding(2.cssRem)
                .boxShadow(0.px, 8.px, 32.px, color = rgba(0, 0, 0, 0.10))
        ) {
            CenterColumn(1.cssRem) {
                TitleText("ひらがな Master")
                SubTitleText(if (isLogin) "Welcome Back!" else "Create Account")
            }

            Form {

                SpacedColumn(1.cssRem) {
                    FormField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                        placeholder = "Enter your name"
                    )

                    FormField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        placeholder = "Enter your password",
                        isPassword = true,
                        errorMessage = errorMessage
                    )

                    ActionButton(
                        text = if (isLogin) "Login" else "Register",
                        onClick = {
                            coroutineScope.launchSafe {
                                if (isLogin) handleLogin() else handleRegister()
                            }
                        },
                        isLoading = isLoading,
                        enabled = !isLoading && name.isNotEmpty() && password.isNotEmpty()
                    )
                }
            }


            CenterRow {
                SpanText(
                    if (isLogin) "Don't have an account? " else "Already have an account? ",
                    Styles.ToggleText.toModifier()
                )
                BaseButton(
                    onClick = { isLogin = !isLogin },
                    text = if (isLogin) "Register" else "Login"
                )
            }

        }
    }
}