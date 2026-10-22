package com.github.nanaki_93.components.layouts

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.widgets.LearningNavigation
import com.github.nanaki_93.components.widgets.LocalSaveStatus
import com.github.nanaki_93.offline.OfflineStatus
import com.varabyte.kobweb.core.layout.Layout

/** Router-backed links require the PageContext that Kobweb provides to layouts and pages. */
@Layout
@Composable
fun PageLayout(content: @Composable () -> Unit) {
    LearningNavigation()
    LocalSaveStatus()
    OfflineStatus()
    content()
}
