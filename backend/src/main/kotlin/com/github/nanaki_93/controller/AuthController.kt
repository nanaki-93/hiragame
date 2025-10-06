package com.github.nanaki_93.controller


import com.github.nanaki_93.models.ACCESS_TOKEN
import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRegisterRequest
import com.github.nanaki_93.models.REFRESH_TOKEN
import com.github.nanaki_93.models.UserData
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.JWTService
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse



import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService, private val jwtService: JWTService) {



    @PostMapping("/login")
    fun login(
        @RequestBody loginRegisterRequest: LoginRegisterRequest,
        httpRes: HttpServletResponse
    ): ResponseEntity<String?> =
        authService.login(loginRegisterRequest)
            .handleAuthResponse(httpRes, "Login Successsful")

    @PostMapping("/register")
    fun register(
        @RequestBody registerRequest: LoginRegisterRequest,
        httpRes: HttpServletResponse
    ): ResponseEntity<String?> =
        authService.register(registerRequest)
            .handleAuthResponse(httpRes, "User registered successfully")


    @GetMapping("/refresh-token")
    fun refreshToken(httpReq: HttpServletRequest, httpRes: HttpServletResponse): ResponseEntity<String?> =
        authService.refreshToken(
            jwtService.extractTokenFromRequest(httpReq, REFRESH_TOKEN) ?: ""
        ).handleAuthResponse(httpRes, "Token refreshed successfully")


    @GetMapping("/logout")
    fun logout(httpReq: HttpServletRequest,httpRes: HttpServletResponse) : ResponseEntity<String> {
        jwtService.extractTokenFromRequest(httpReq, ACCESS_TOKEN)?.let {
            authService.logout(it)
        }
        addCookie(httpRes, cookieName = ACCESS_TOKEN)
        addCookie(httpRes, cookieName = REFRESH_TOKEN)
        return ResponseEntity.ok("Logout successful")
    }

    @GetMapping("/me")
    fun me(httpReq: HttpServletRequest): ResponseEntity<UserData> {

        jwtService.extractTokenFromRequest(httpReq, ACCESS_TOKEN)?.let {

            val username = jwtService.extractUsername(it)
            if (jwtService.validateToken(it)) {
                val user = authService.getUserByUsername(username) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                return ResponseEntity.ok(UserData(user.id.toString(), user.username))
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }


    @GetMapping("/is-authenticated")
    fun isAuthenticated(httpReq: HttpServletRequest): ResponseEntity<Boolean> =
        jwtService.extractTokenFromRequest(httpReq, ACCESS_TOKEN).let {
            it != null && jwtService.validateToken(it)
        }.let { ResponseEntity.ok(it) }


    private fun addTokensCookies(httpRes: HttpServletResponse, authResponse: AuthResponse) {
        addCookie(httpRes, authResponse.token, ACCESS_TOKEN)
        addCookie(httpRes, authResponse.refreshToken, REFRESH_TOKEN)
    }

    private fun addCookie(httpRes: HttpServletResponse, jwt: String = "", cookieName: String) {
        val cookie =Cookie(cookieName, jwt).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7 days in seconds
            secure = true
        }
        val cookieHeader = "${cookie.name}=${cookie.value}; Path=${cookie.path}; Max-Age=${cookie.maxAge}; HttpOnly; Secure; SameSite=None"
        httpRes.addHeader("Set-Cookie", cookieHeader)
    }

    private fun AuthResponse.handleAuthResponse(
        httpRes: HttpServletResponse,
        message: String
    ): ResponseEntity<String?> {
        return this.also {
            addTokensCookies(httpRes, it)
        }.let {
            ResponseEntity.ok(message)
        }
    }

}