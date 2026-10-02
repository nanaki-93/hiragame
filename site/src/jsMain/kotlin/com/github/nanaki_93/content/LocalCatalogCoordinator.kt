package com.github.nanaki_93.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Only fully validated loader results enter Ready. No progress or view selection is owned here. */
sealed interface LocalCatalogState {
    data object Loading : LocalCatalogState
    data class Ready(val content: BundledContent) : LocalCatalogState
    data class Empty(val reason: CatalogEmptyReason) : LocalCatalogState
    data class Error(val kind: CatalogErrorKind, val safeMessage: String) : LocalCatalogState
}

enum class CatalogEmptyReason { EMPTY_CATALOG, NO_WORKPLACE_LESSONS }
enum class CatalogErrorKind { CONTENT, HTTP, UNEXPECTED }

/** Owns the currently requested load; the caller owns the scope and must dispose on leaving Topics. */
class LocalCatalogCoordinator(
    private val scope: CoroutineScope,
    private val loadContent: suspend () -> CatalogLoad,
) {
    constructor(scope: CoroutineScope, loader: BundledContentLoader) : this(scope, loader::load)

    private val mutableState = MutableStateFlow<LocalCatalogState>(LocalCatalogState.Loading)
    val state: StateFlow<LocalCatalogState> = mutableState
    private var generation = 0L
    private var loadJob: Job? = null
    private var disposed = false

    /** Initial load and explicit retry both invalidate the previous attempt before launching another. */
    fun load() {
        if (disposed) return
        generation++
        loadJob?.cancel()
        val token = generation
        mutableState.value = LocalCatalogState.Loading
        loadJob = scope.launch {
            try {
                val result = loadContent()
                currentCoroutineContext().ensureActive()
                if (disposed || token != generation) return@launch
                mutableState.value = when (result) {
                    is CatalogLoad.Ready -> if (result.content.lessons.isEmpty())
                        LocalCatalogState.Empty(CatalogEmptyReason.NO_WORKPLACE_LESSONS)
                    else LocalCatalogState.Ready(result.content)
                    is CatalogLoad.Empty -> if (result.reason == EmptyContentReason.EMPTY_CATALOG)
                        LocalCatalogState.Empty(CatalogEmptyReason.EMPTY_CATALOG)
                    else LocalCatalogState.Error(CatalogErrorKind.CONTENT, CONTENT_ERROR)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (disposed || token != generation) return@launch
                val bundled = e as? BundledContentException
                val kind = when {
                    e is ContentHttpException || bundled?.cause is ContentHttpException -> CatalogErrorKind.HTTP
                    bundled != null -> CatalogErrorKind.CONTENT
                    else -> CatalogErrorKind.UNEXPECTED
                }
                mutableState.value = LocalCatalogState.Error(kind, when (kind) {
                    CatalogErrorKind.HTTP -> HTTP_ERROR
                    CatalogErrorKind.CONTENT -> CONTENT_ERROR
                    CatalogErrorKind.UNEXPECTED -> UNEXPECTED_ERROR
                })
            }
        }
    }

    fun retryLoad() = load()

    /** Prevent even a cancellation-ignoring source from publishing after the page leaves. */
    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        loadJob?.cancel()
        loadJob = null
    }

    private companion object {
        const val HTTP_ERROR = "Unable to read bundled content from this site. Retry the load."
        const val CONTENT_ERROR = "Unable to load reviewed content. Retry the load."
        const val UNEXPECTED_ERROR = "Unable to load local content. Retry the load."
    }
}
