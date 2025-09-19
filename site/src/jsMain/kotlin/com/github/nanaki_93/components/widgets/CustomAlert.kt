package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.Styles
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem

@Composable
fun CustomAlert(
    isVisible: Boolean = true,
    title: String = "Alert",
    message: String,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
    onCancel: (() -> Unit)? = null,
    cancelText: String = "Cancel"
) {
    if (!isVisible) return

    // Modal overlay
    Box(Styles.ModalOverlay.toModifier()) {
        // Dialog box
        Column(
            modifier = Styles.ModalDialog.toModifier(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.cssRem)
        ) {
            // Title
            SpanText(title, Styles.Title.toModifier())

            // Message
            SpanText(message, Styles.FeedbackMessage.toModifier())

            // Buttons
            CenteredButtonRow {
                // Cancel button (if provided)
                onCancel?.let { cancelAction ->
                    StyledButton(
                        text = cancelText,
                        onClick = cancelAction,
                        isPrimary = false
                    )
                }

                // Confirm button
                StyledButton(
                    text = confirmText,
                    onClick = onConfirm,
                    isPrimary = true
                )
            }
        }
    }
}

@Composable
fun SessionExpiredAlert(
    message: String = "Your session has expired. Please log in again.",
    onClose: () -> Unit
) {
    CustomAlert(
        isVisible = true,
        title = "Session Expired",
        message = message,
        onConfirm = onClose,
        confirmText = "OK",
        onCancel = null
    )
}

@Composable
fun ConfirmationAlert(
    title: String = "Confirm Action",
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel"
) {
    CustomAlert(
        isVisible = true,
        title = title,
        message = message,
        onConfirm = onConfirm,
        onCancel = onCancel,
        confirmText = confirmText,
        cancelText = cancelText
    )
}

@Composable
fun ErrorAlert(
    message: String,
    onClose: () -> Unit,
    title: String = "Error"
) {
    CustomAlert(
        isVisible = true,
        title = title,
        message = message,
        onConfirm = onClose,
        confirmText = "OK",
        onCancel = null
    )
}