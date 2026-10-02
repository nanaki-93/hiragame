package com.github.nanaki_93.storage

/** Raw text is deliberately not decoded here: protected/unsupported saves must remain recoverable. */
sealed interface StoreReadResult {
    data object Missing : StoreReadResult
    data class Raw(val value: String) : StoreReadResult
    data class Failure(val reason: StoreFailure) : StoreReadResult
}

/** A conflict includes the observed text (null means the key was removed). */
sealed interface StoreWriteResult {
    data object Written : StoreWriteResult
    data class Conflict(val observedRaw: String?) : StoreWriteResult
    data class Failure(val reason: StoreFailure) : StoreWriteResult
}

enum class StoreFailure { DENIED, QUOTA, OTHER }

fun interface StoreSubscription {
    fun dispose()
}

/** One complete value per write. The expected raw value is null only when the key is missing. */
interface ProgressStore {
    fun read(): StoreReadResult
    fun write(expectedRaw: String?, replacementRaw: String): StoreWriteResult
    fun subscribe(onExternalChange: () -> Unit): StoreSubscription
}
