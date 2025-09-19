package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.components.styles.Colors
import com.github.nanaki_93.components.styles.Styles
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

// Size variants
enum class SpinnerSize(val size: CSSLengthValue, val borderWidth: CSSLengthValue) {
    Small(24.px, 3.px),
    Medium(40.px, 4.px),
    Large(56.px, 5.px),
    ExtraLarge(72.px, 6.px)
}

// Color variants matching your design system
enum class SpinnerColor(val color: CSSColorValue) {
    Primary(Colors.Primary),      // Sakura pink
    Secondary(Colors.Secondary),  // Forest green
    Text(Colors.Text),           // Dark gray
    Border(Colors.Border),       // Subtle gray
    White(Color.white),
    Blue(rgb(59, 130, 246)),
    Green(rgb(34, 197, 94)),
    Red(rgb(239, 68, 68))
}

@Composable
fun Spinner(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    size: SpinnerSize = SpinnerSize.Medium,
    color: SpinnerColor = SpinnerColor.Primary,
    centered: Boolean = true
) {
    if (!isVisible) return

    val spinnerModifier = Styles.Spinner.toModifier()
        .then(modifier)
        .width(size.size)
        .height(size.size)
        .border(size.borderWidth, LineStyle.Solid, rgba(0, 0, 0, 0.1))
        .borderTop(size.borderWidth, LineStyle.Solid, color.color)

    if (centered) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .display(DisplayStyle.Flex)
                .justifyContent(JustifyContent.Center)
                .alignItems(AlignItems.Center)
                .padding(1.cssRem),
            contentAlignment = Alignment.Center
        ) {
            Div(attrs = spinnerModifier.toAttrs())
        }
    } else {
        Div(attrs = spinnerModifier.toAttrs())
    }
}

// Convenience composables for common use cases
@Composable
fun LoadingSpinner(
    isLoading: Boolean = true,
    size: SpinnerSize = SpinnerSize.Medium,
    color: SpinnerColor = SpinnerColor.Primary
) {
    Spinner(
        isVisible = isLoading,
        size = size,
        color = color,
        centered = true
    )
}

@Composable
fun InlineSpinner(
    isVisible: Boolean = true,
    size: SpinnerSize = SpinnerSize.Small,
    color: SpinnerColor = SpinnerColor.Primary
) {
    Spinner(
        isVisible = isVisible,
        size = size,
        color = color,
        centered = false,
        modifier = Modifier.display(DisplayStyle.InlineBlock)
    )
}