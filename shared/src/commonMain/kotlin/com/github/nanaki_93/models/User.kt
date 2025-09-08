package com.github.nanaki_93.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val name: String = "",
    val password: String = "", // This will be hashed
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
