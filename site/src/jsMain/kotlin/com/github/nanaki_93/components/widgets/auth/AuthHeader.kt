package com.github.nanaki_93.components.widgets.auth

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.widgets.CenterColumn
import com.github.nanaki_93.components.widgets.SubTitleText
import com.github.nanaki_93.components.widgets.TitleText

import org.jetbrains.compose.web.css.cssRem

@Composable
fun AuthHeader(isLogin: Boolean) {
    CenterColumn(1.cssRem) {
        TitleText("ひらがな Master")
        SubTitleText(if (isLogin) "Welcome Back!" else "Create Account")
    }
}