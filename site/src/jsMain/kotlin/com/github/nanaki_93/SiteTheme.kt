package com.github.nanaki_93

import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.color

@InitSilk
fun initTheme(ctx: InitSilkContext) {
    ctx.theme.palettes.light.background = Color.rgb(ThemeTokens.LIGHT_BG)
    ctx.theme.palettes.light.color = Color.rgb(ThemeTokens.LIGHT_TEXT)
    ctx.theme.palettes.dark.background = Color.rgb(ThemeTokens.DARK_BG)
    ctx.theme.palettes.dark.color = Color.rgb(ThemeTokens.DARK_TEXT)
}
