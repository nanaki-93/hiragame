package com.github.nanaki_93.service

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

class SessionExpiredException(message: String) : Exception(message)

suspend inline fun <T>authenticatedRequest(
    name: String,
    crossinline refreshToken: suspend () -> Unit,
    crossinline block: suspend () -> T
): T {
    return try {
        println("Authenticated request for $name")
        block()
    } catch (e: ClientRequestException) {
        println("ClientRequestException: ${e.response.status}")


        if (e.response.status == HttpStatusCode.Unauthorized || name == "isAuthenticated") {
            refreshToken()
            block() // retry after refresh
        } else {
            throw SessionExpiredException("Session expired")
        }
    } catch (_: Exception){
        throw SessionExpiredException("Session expired")
    }
}


// Extension function to handle SessionExpiredException in coroutines
suspend inline fun <T> handleSessionExpiration(crossinline block: suspend () -> T): T? {
    return try {
        block()
    } catch (_: SessionExpiredException) {
        SessionManager.handleSessionExpired()
        null
    }
}
