package com.github.nanaki_93.ai

import androidx.compose.runtime.*
import com.github.nanaki_93.content.Lesson
import com.github.nanaki_93.components.widgets.*
import kotlinx.coroutines.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.*

@Composable
fun OptionalAiConversation(lesson: Lesson) {
    val scope = rememberCoroutineScope()
    var enabled by remember(lesson.id) { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("http://127.0.0.1:11434") }
    var model by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(emptyList<ConversationTurn>()) }
    var suggestion by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun cancel() { generation++; job?.cancel(); busy = false }
    DisposableEffect(lesson.id) { onDispose { cancel() } }
    Section {
        H3 { Text("Optional local AI practice") }
        P { Text("Authored conversation is the default. Local AI is experimental: replies and correction suggestions can be wrong. It never changes lesson grades or adds approved content.") }
        SecondaryButton(if (enabled) "Disable AI and discard conversation" else "Opt in to local AI for this activity", onClick = {
            cancel(); enabled = !enabled; history = emptyList(); suggestion = ""; draft = ""
        })
        if (enabled) {
            P { Text("Use a downloaded local Ollama model with cloud features disabled. Nothing downloads automatically. Prompts contain this lesson and your response; avoid confidential information. Model size and latency depend on your hardware. No Japanese model recommendation has been verified for this release.") }
            Label(attrs = { attr("for", "local-model-endpoint") }) { Text("Loopback endpoint") }
            Input(InputType.Text, attrs = { id("local-model-endpoint"); value(endpoint); onInput { cancel(); endpoint = it.value; history = emptyList() } })
            Label(attrs = { attr("for", "local-model-name") }) { Text("Installed model name") }
            Input(InputType.Text, attrs = { id("local-model-name"); value(model); onInput { cancel(); model = it.value; history = emptyList() } })
            SecondaryButton("Check installed local models", enabled = !busy, onClick = {
                val token = ++generation; busy = true
                job = scope.launch {
                    try {
                        val models = installedModels(endpoint, BrowserModelTransport)
                        if (token == generation) status = if (models.isEmpty()) "No downloaded local models were found." else models.joinToString { "${it.name} (${it.bytes / 1_000_000} MB)" }
                    } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e; if (token == generation) status = "Model check timed out. Guided practice remains available." }
                    catch (_: Exception) { if (token == generation) status = "Cannot connect. Check Ollama/CORS or use the optional loopback helper described in README. Guided practice remains available." }
                    finally { if (token == generation) busy = false }
                }
            })
            history.forEach { turn -> P(attrs = { attr("lang", "ja") }) { Text("You: ${turn.learner}") }; P(attrs = { attr("lang", "ja") }) { Text("AI coworker (unreviewed): ${turn.reply}") } }
            if (suggestion.isNotBlank()) P { Text("Uncertain AI suggestion: $suggestion") }
            if (history.size < 6) {
                Label(attrs = { attr("for", "ai-response") }) { Text("Your response for this situation") }
                JapaneseResponseArea(draft, { draft = it }, "ai-response")
                PrimaryButton("Send to local model", enabled = !busy && draft.isNotBlank() && localModelName(model), onClick = {
                    val token = ++generation; val input = draft; val prior = history; busy = true
                    job = scope.launch {
                        try {
                            val result = OllamaConversationProvider(endpoint, model, BrowserModelTransport).reply(lesson, prior, input)
                            if (token == generation) { history = prior + ConversationTurn(input, result.reply); suggestion = result.suggestion; draft = ""; status = "Unreviewed reply received. Compare with authored examples." }
                        } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e; if (token == generation) status = "Reply timed out. Continue the authored conversation." }
                        catch (_: Exception) { if (token == generation) status = "The local model failed or returned an invalid reply. Continue the authored conversation above." }
                        finally { if (token == generation) busy = false }
                    }
                })
            } else P { Text("Six-turn limit reached. Restart or return to the authored activity.") }
            if (busy) SecondaryButton("Cancel model request", onClick = { cancel(); status = "Request cancelled." })
            SecondaryButton("Restart local conversation", onClick = { cancel(); history = emptyList(); suggestion = ""; draft = "" })
            P(attrs = { attr("role", "status") }) { Text(if (busy) "Waiting for the local model…" else status) }
            P { Text("This conversation, endpoint and model selection are session-local and excluded from backups. Browser CORS, secure-context and local-network restrictions may prevent a connection.") }
        }
    }
}
