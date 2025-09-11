package com.github.nanaki_93.pages

import androidx.compose.runtime.*
import com.github.nanaki_93.CardStyle
import com.github.nanaki_93.GameContainerStyle
import com.github.nanaki_93.service.AuthService
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*

@Page("/login")
@Composable
fun LoginPage() {
    val authService = remember { AuthService() }

    // Check if already logged in
    LaunchedEffect(Unit) {
        if (authService.isAuthenticated()) {
            kotlinx.browser.window.location.href = "/hiragame"
        }
    }

    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    suspend fun handleLogin() {
        if (name.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }

        isLoading = true
        errorMessage = ""

        try {
            authService.login(name, password)
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

    // Rest of your existing UI code remains the same...
    Box(GameContainerStyle.toModifier()) {
        Column(
            CardStyle.toModifier()
                .width(400.px)
                .padding(2.cssRem),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.cssRem)
        ) {
            SpanText(
                "ひらがな Master",
                Modifier
                    .fontSize(2.5.cssRem)
                    .fontWeight(FontWeight.Bold)
                    .textShadow(2.px, 2.px, 4.px, rgba(0, 0, 0, 0.3))
            )

            SpanText(
                if (isLogin) "Welcome Back!" else "Create Account",
                Modifier.fontSize(1.5.cssRem).fontWeight(FontWeight.Bold)
            )

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.cssRem)
            ) {
                TextInput(
                    text = name,
                    onTextChange = { name = it },
                    placeholder = "Name",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.8.cssRem)
                        .borderRadius(8.px)
                        .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
                        .backgroundColor(rgba(255, 255, 255, 0.1))
                        .color(Colors.White)
                )

                TextInput(
                    text = password,
                    onTextChange = { password = it },
                    placeholder = "Password",
                    password = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.8.cssRem)
                        .borderRadius(8.px)
                        .border(1.px, LineStyle.Solid, rgba(255, 255, 255, 0.3))
                        .backgroundColor(rgba(255, 255, 255, 0.1))
                        .color(Colors.White)
                )

                if (errorMessage.isNotEmpty()) {
                    SpanText(
                        errorMessage,
                        Modifier
                            .color(Colors.Red)
                            .fontSize(0.9.cssRem)
                            .textAlign(TextAlign.Center)
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (isLogin) handleLogin() else handleRegister()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.8.cssRem)
                        .borderRadius(8.px)
                        .backgroundColor(Colors.DeepSkyBlue)
                        .color(Colors.White)
                        .fontWeight(FontWeight.Bold),
                    enabled = !isLoading
                ) {
                    SpanText(
                        if (isLoading) "Loading..." else if (isLogin) "Login" else "Register"
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpanText(
                        if (isLogin) "Don't have an account? " else "Already have an account? ",
                        Modifier.fontSize(0.9.cssRem).opacity(0.8)
                    )
                    Button(
                        onClick = {
                            isLogin = !isLogin
                            errorMessage = ""
                        },
                        modifier = Modifier
                            .backgroundColor(Colors.Transparent)
                            .color(Colors.DeepSkyBlue)
                            .textDecorationLine(TextDecorationLine.Underline)
                            .fontSize(0.9.cssRem)
                    ) {
                        SpanText(if (isLogin) "Register" else "Login")
                    }
                }
            }
        }
    }
}