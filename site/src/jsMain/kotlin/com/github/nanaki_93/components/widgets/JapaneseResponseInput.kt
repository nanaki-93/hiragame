package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.TextArea
import org.w3c.dom.events.Event

/** Composition events belong to one prompt, never the save or the reducer. */
internal class JapaneseSubmissionGuard {
    private var composing = false

    fun compositionStarted() { composing = true }
    fun compositionEnded() { composing = false } // Never submit here; wait for a deliberate action.

    fun canSubmit(nativeComposing: Boolean) = !composing && !nativeComposing
    fun canSubmitOnEnter(nativeComposing: Boolean, keyCode: Int, repeat: Boolean) =
        canSubmit(nativeComposing) && keyCode != 229 && !repeat
}

/** Use the same guard for Enter and the caller's submit button. No implicit form submit. */
@Composable
internal fun JapaneseAnswerInput(
    draft: String, onDraft: (String) -> Unit, guard: JapaneseSubmissionGuard, submit: () -> Unit,
    inputId: String = "practice-response",
) {
    Input(type = InputType.Text, attrs = {
        id(inputId)
        classes("practice-answer")
        attr("maxlength", "200")
        value(draft)
        onInput {
            if (it.nativeEvent.asDynamic().isComposing == true) guard.compositionStarted()
            onDraft(it.value)
        }
        ref { input ->
            val started: (Event) -> Unit = { guard.compositionStarted() }
            val ended: (Event) -> Unit = { guard.compositionEnded() }
            input.addEventListener("compositionstart", started)
            input.addEventListener("compositionend", ended)
            onDispose {
                input.removeEventListener("compositionstart", started)
                input.removeEventListener("compositionend", ended)
            }
        }
        onKeyDown { event ->
            val native = event.nativeEvent.asDynamic()
            if (event.key == "Enter" && guard.canSubmitOnEnter(
                    native.isComposing == true, (native.keyCode as? Int) ?: 0, native.repeat == true,
                )) {
                event.preventDefault()
                submit()
            }
        }
    })
}

/** Multiline production input: Enter is a newline, never a submission shortcut. */
@Composable
internal fun JapaneseResponseArea(draft: String, onDraft: (String) -> Unit, inputId: String = "practice-response") {
    TextArea(value = draft, attrs = {
        id(inputId)
        classes("practice-answer")
        attr("maxlength", "1000")
        onInput { onDraft(it.value) }
    })
}
