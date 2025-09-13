package com.github.nanaki_93.controller


import com.github.nanaki_93.models.ACCESS_TOKEN
import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRegisterRequest
import com.github.nanaki_93.models.REFRESH_TOKEN
import com.github.nanaki_93.models.UserData
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.JWTService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse


import org.slf4j.Logger
import org.slf4j.LoggerFactory
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


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AuthController::class.java)

    }

    @PostMapping("/login")
    fun login(@RequestBody loginRegisterRequest: LoginRegisterRequest, httpRes: HttpServletResponse): ResponseEntity<String?> =
        authService.login(loginRegisterRequest).also {
            addTokensCookies(httpRes, it)
        }.let {
            ResponseEntity.ok("Login successful")
        }


    @PostMapping("/register")
    fun register(@RequestBody registerRequest: LoginRegisterRequest, httpRes: HttpServletResponse): ResponseEntity<String?> =
        authService.register(registerRequest).also {
            addTokensCookies(httpRes, it)
        }.let {
            ResponseEntity.ok("User registered successfully")
        }


    @GetMapping("/refresh-token")
    fun refreshToken(httpReq: HttpServletRequest, httpRes: HttpServletResponse) :ResponseEntity<String?> =
        authService.refreshToken(
            jwtService.extractTokenFromRequest(httpReq, REFRESH_TOKEN) ?: ""
        ).also {
            addTokensCookies(httpRes, it)
        }.let {
            ResponseEntity.ok("Token refreshed successfully")
        }


    @GetMapping("/logout")
    fun logout(httpRes: HttpServletResponse) {
        addCookie(httpRes, cookieName = ACCESS_TOKEN)
        addCookie(httpRes, cookieName = REFRESH_TOKEN)
    }

    @GetMapping("/me")
    fun me(httpReq: HttpServletRequest): ResponseEntity<UserData> {

        jwtService.extractTokenFromRequest(httpReq, ACCESS_TOKEN)?.let {

            val username = jwtService.extractUsername(it)
            if (jwtService.validateToken(it)) {
                val user = authService.getUserByUsername(username) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                logger.info("User data retrieved: $user")
                return ResponseEntity.ok(UserData(user.id.toString(), user.username))
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }


    @GetMapping("/is-authenticated")
    fun isAuthenticated(httpReq: HttpServletRequest): ResponseEntity<Boolean> {
        val token = jwtService.extractTokenFromRequest(httpReq, ACCESS_TOKEN)
        val isValid = token != null && jwtService.validateToken(token)
        return ResponseEntity.ok(isValid)
    }


    private fun addTokensCookies(httpRes: HttpServletResponse, authResponse: AuthResponse) {
        addCookie(httpRes, authResponse.token, ACCESS_TOKEN)
        addCookie(httpRes, authResponse.refreshToken, REFRESH_TOKEN)
    }

    private fun addCookie(httpRes: HttpServletResponse, jwt: String = "", cookieName: String) {
        httpRes.addCookie(Cookie(cookieName, jwt).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7 days in seconds
            secure = false
            domain = "localhost"
        })
    }

}