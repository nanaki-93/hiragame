package com.github.nanaki_93.controller


import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRequest
import com.github.nanaki_93.models.RegisterRequest
import com.github.nanaki_93.service.AuthService
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = ["http://localhost:8081"])
class AuthController (private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: LoginRequest): Result<AuthResponse>  {
        return authService.login(loginRequest)
    }

    @PostMapping("/register")
    fun register(@RequestBody registerRequest: RegisterRequest): Result<AuthResponse> {
        return authService.register(registerRequest)
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody refreshToken: String): Result<AuthResponse> {
        return authService.refreshToken(refreshToken)
    }
}