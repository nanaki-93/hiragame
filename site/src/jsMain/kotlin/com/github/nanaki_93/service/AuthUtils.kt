package com.github.nanaki_93.service

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

suspend inline fun <T> authenticatedRequest(
    crossinline refreshToken: suspend () -> Unit,
    crossinline block: suspend () -> T
): T {
    return try {
        block()
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.Unauthorized) {
            refreshToken()
            block() // retry after refresh
        } else {
            throw e
        }
    }
}