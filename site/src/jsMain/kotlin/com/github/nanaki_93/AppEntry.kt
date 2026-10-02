package com.github.nanaki_93

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxHeight
import com.varabyte.kobweb.compose.ui.modifiers.scrollBehavior
import com.varabyte.kobweb.core.App
import com.varabyte.kobweb.silk.SilkApp
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.init.InitSilk
import com.varabyte.kobweb.silk.init.InitSilkContext
import com.varabyte.kobweb.silk.init.registerStyleBase
import com.varabyte.kobweb.silk.style.common.SmoothColorStyle
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.systemPreference
import com.github.nanaki_93.progress.SavedColorMode
import com.github.nanaki_93.storage.BrowserProgressStore
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.readLegacyColorMode

/** The InitSilk hook runs before the app composition. Transfer its single startup read to
 * AppEntry, where the same owner is provided to every page and disposed with the app.
 */
private var initializingOwner: LocalProgressOwner? = null

val LocalProgress = staticCompositionLocalOf<LocalProgressOwner> {
    error("Progress owner must be provided by AppEntry")
}

private fun createProgressOwner() = LocalProgressOwner(BrowserProgressStore(), ::readLegacyColorMode)

internal fun initialSilkMode(
    owner: LocalProgressOwner,
    systemMode: () -> ColorMode = { ColorMode.systemPreference },
): ColorMode = when (owner.state.value.snapshot.preferences.colorMode) {
    SavedColorMode.LIGHT -> ColorMode.LIGHT
    SavedColorMode.DARK -> ColorMode.DARK
    SavedColorMode.SYSTEM -> try { systemMode() } catch (_: Throwable) { ColorMode.LIGHT }
}

@InitSilk
fun initColorMode(ctx: InitSilkContext) {
    initializingOwner?.dispose()
    val owner = createProgressOwner()
    initializingOwner = owner
    ctx.config.initialColorMode = initialSilkMode(owner)
}

@InitSilk
fun initStyles(ctx: InitSilkContext) {
    ctx.stylesheet.apply {
        registerStyleBase("html, body") { Modifier.fillMaxHeight() }
    }
}

@App
@Composable
fun AppEntry(content: @Composable () -> Unit) {
    val owner = remember { initializingOwner ?: createProgressOwner().also { initializingOwner = it } }
    DisposableEffect(owner) {
        onDispose {
            if (initializingOwner === owner) initializingOwner = null
            owner.dispose()
        }
    }
    SilkApp {
        CompositionLocalProvider(LocalProgress provides owner) {
            val mode = ColorMode.current
            SideEffect { kotlinx.browser.document.documentElement?.setAttribute("data-theme", if (mode == ColorMode.DARK) "dark" else "light") }
            Surface(Modifier.fillMaxHeight()) {
                com.github.nanaki_93.components.widgets.LearningNavigation()
                com.github.nanaki_93.components.widgets.LocalSaveStatus()
                content()
            }
        }
    }
}
