//package com.github.nanaki_93.dto.auth
//
//import jakarta.validation.constraints.NotBlank
//import jakarta.validation.constraints.Size
//
//data class LoginRequest(
//
//    @field:NotBlank(message = "Name is required")
//    @field:Size(min = 6, message = "Name must be at least 6 characters")
//    val name: String,
//
//    @field:NotBlank(message = "Password is required")
//    @field:Size(min = 6, message = "Password must be at least 6 characters")
//    val password: String
//)
//
//data class RegisterRequest(
//    @field:NotBlank(message = "Name is required")
//    @field:Size(min = 6, message = "Name must be at least 6 characters")
//    val name: String,
//
//    @field:NotBlank(message = "Password is required")
//    @field:Size(min = 6, message = "Password must be at least 6 characters")
//    val password: String
//)
//
//data class RefreshTokenRequest(
//    @field:NotBlank(message = "Refresh token is required")
//    val refreshToken: String
//)
//
//
//data class AuthResponse(
//    val token: String,
//    val refreshToken: String,
//    val userId: String,
//    val name: String,
//    val tokenType: String = "Bearer"
//)
//
//data class JWTPayload(
//    val userId: String,
//    val name: String,
//    val exp: Long,
//    val iat: Long
//)
