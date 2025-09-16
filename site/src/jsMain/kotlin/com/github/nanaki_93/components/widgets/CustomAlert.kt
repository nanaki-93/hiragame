package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import com.github.nanaki_93.components.styles.*
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.cssRem

@Composable
fun CustomAlert(
    isVisible: Boolean = true,
    title: String = "Session Expired",
    message: String,
    onConfirm: () -> Unit,
    confirmText: String = "OK",
    onCancel: (() -> Unit)? = null,
    cancelText: String = "Cancel"
) {
    if (!isVisible) return

    Box(CustomAlertStyles.Overlay.toModifier()) {
        CenterColumn(1.5.cssRem) {
            CustomAlertStyles.Dialog.toModifier()

            TitleText(title)
            BaseText(message, CustomAlertStyles.Message.toModifier())
            CenteredButtonRow {

                if (onCancel != null) {
                    ResetButton(text = cancelText, onClick = { onCancel() })
                }

                ActionButton(text = confirmText, onClick = { onConfirm() })
            }

        }
    }
}

@Composable
fun SessionExpiredAlert(
    message: String,
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