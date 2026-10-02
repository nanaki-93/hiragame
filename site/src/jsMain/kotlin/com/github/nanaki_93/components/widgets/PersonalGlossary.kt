package com.github.nanaki_93.components.widgets

import androidx.compose.runtime.*
import com.github.nanaki_93.offline.SessionActivity
import com.github.nanaki_93.progress.*
import com.github.nanaki_93.storage.LocalProgressOwner
import com.github.nanaki_93.storage.ProgressMutationResult
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.*
import kotlin.random.Random

@Composable
fun PersonalGlossary(owner: LocalProgressOwner) {
    val saved by owner.state.collectAsState()
    key(owner.generation) { GlossaryEditor(owner, saved.snapshot.personalGlossary) }
}

@Composable
private fun GlossaryEditor(owner: LocalProgressOwner, entries: List<GlossaryEntry>) {
    val generation = remember { owner.generation }
    var editing by remember { mutableStateOf<GlossaryEntry?>(null) }
    var term by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    fun clear() { editing = null; term = ""; reading = ""; meaning = "" }
    SessionActivity(term.isNotEmpty() || reading.isNotEmpty() || meaning.isNotEmpty())
    Section {
        H2 { Text("Personal glossary") }
        P { Text("Optional personal notes, up to 100 entries. These are unreviewed and never change lesson answers or review grades. Saved entries are included in your backup; avoid employer-confidential information.") }
        P { Text("Describe a real or imaginary project with public details: what it does, your role, and one design choice. Use facts you are comfortable sharing.") }
        entries.forEach { entry ->
            Article {
                H3(attrs = { attr("lang", "ja") }) { Text(entry.term) }
                if (entry.reading.isNotBlank()) P(attrs = { attr("lang", "ja") }) { Text(entry.reading) }
                P { Text(entry.meaning) }
                SecondaryButton("Edit ${entry.term}", onClick = {
                    editing = entry; term = entry.term; reading = entry.reading; meaning = entry.meaning; message = ""
                })
                SecondaryButton("Remove ${entry.term}", onClick = {
                    if (owner.isCurrentGeneration(generation)) {
                        val result = owner.mutate { removeGlossaryEntry(it, entry) }
                        message = if (result is ProgressMutationResult.Rejected) "This entry changed. Review the current list." else "Entry removed from this view. Check the save status above."
                        if (result !is ProgressMutationResult.Rejected && editing?.id == entry.id) clear()
                    }
                })
            }
        }
        H3 { Text(if (editing == null) "Add a personal term" else "Edit personal term") }
        Label(attrs = { attr("for", "glossary-term") }) { Text("Term (up to 256 characters)") }
        Input(InputType.Text, attrs = { id("glossary-term"); attr("lang", "ja"); attr("maxlength", "256"); value(term); onInput { term = it.value } })
        Label(attrs = { attr("for", "glossary-reading") }) { Text("Reading, optional (up to 256 characters)") }
        Input(InputType.Text, attrs = { id("glossary-reading"); attr("lang", "ja"); attr("maxlength", "256"); value(reading); onInput { reading = it.value } })
        Label(attrs = { attr("for", "glossary-meaning") }) { Text("Meaning or personal example (up to 500 characters)") }
        TextArea(value = meaning, attrs = { id("glossary-meaning"); attr("maxlength", "500"); onInput { meaning = it.value } })
        PrimaryButton(if (editing == null) "Save personal term" else "Save edited term",
            enabled = term.isNotBlank() && meaning.isNotBlank() && (editing != null || entries.size < SaveBounds.MAX_GLOSSARY_ENTRIES),
            onClick = {
                if (owner.isCurrentGeneration(generation)) {
                    val entry = GlossaryEntry(editing?.id ?: "glossary_${Random.nextInt().toUInt().toString(16)}_${Random.nextInt().toUInt().toString(16)}", term.trim(), reading.trim(), meaning.trim())
                    val result = owner.mutate { saveGlossaryEntry(it, entry, editing) }
                    if (result is ProgressMutationResult.Rejected) message = "Entry could not be saved. Check the lengths and reload an entry that changed."
                    else { clear(); message = "Personal term added to this view. Check the save status above." }
                }
            })
        SecondaryButton("Discard glossary draft", onClick = { clear(); message = "Draft discarded." })
        if (message.isNotEmpty()) P(attrs = { attr("role", "status") }) { Text(message) }
        P { Text("Try one short lesson and a few due phrases each day, then repeat its role-play with less support. JLPT preparation and speaking practice complement each other; neither alone guarantees workplace fluency.") }
    }
}
