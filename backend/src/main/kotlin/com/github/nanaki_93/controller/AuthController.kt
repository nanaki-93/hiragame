package com.github.nanaki_93.controller


import com.github.nanaki_93.models.AuthResponse
import com.github.nanaki_93.models.LoginRequest
import com.github.nanaki_93.models.RegisterRequest
import com.github.nanaki_93.models.UserData
import com.github.nanaki_93.service.AuthService
import com.github.nanaki_93.service.JWTService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = ["http://localhost:8081"], allowCredentials = "true")
class AuthController(private val authService: AuthService, private val jwtService: JWTService) {


    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AuthController::class.java)
        const val AUTH_COOKIE_NAME = "jwt"
    }

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: LoginRequest, httpRes: HttpServletResponse): Result<AuthResponse> {

        val response = authService.login(loginRequest).getOrThrow()

        // Set CORS headers first
        httpRes.setHeader("Access-Control-Allow-Origin", "http://localhost:8081")
        httpRes.setHeader("Access-Control-Allow-Credentials", "true")


        httpRes.addCookie(Cookie(AUTH_COOKIE_NAME, response.token).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7 days in seconds
            secure = false
            setAttribute("SameSite", "Lax")

        })
        val cookieValue = "${AUTH_COOKIE_NAME}=${response.token}; Path=/; Max-Age=${7 * 24 * 60 * 60}; HttpOnly; SameSite=Lax"
        httpRes.addHeader("Set-Cookie", cookieValue)

        logger.info("Login successful - Cookie header set: $cookieValue")
        logger.info("All response headers: ${httpRes.headerNames?.map { "$it=${httpRes.getHeaders(it)}" }}")


        logger.info("Login successful")
        // Also set the header manually to ensure it's sent
        httpRes.setHeader("Set-Cookie", "${AUTH_COOKIE_NAME}=${response.token}; Path=/; Max-Age=${7 * 24 * 60 * 60}; HttpOnly; SameSite=Lax")



        return Result.success(response.copy(token = "", message = "Login successful"))
    }

    @PostMapping("/register")
    fun register(@RequestBody registerRequest: RegisterRequest, httpRes: HttpServletResponse): Result<AuthResponse> {

        val response = authService.register(registerRequest).getOrThrow()

        httpRes.addCookie(Cookie(AUTH_COOKIE_NAME, response.token).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7 days in seconds
            secure = false
            setAttribute("SameSite", "Lax")

        })

        return Result.success(response.copy(token = "", message = "User registered successfully"))
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody refreshToken: String, httpRes: HttpServletResponse): Result<AuthResponse> {
        val response = authService.refreshToken(refreshToken).getOrThrow()

        httpRes.addCookie(Cookie(AUTH_COOKIE_NAME, response.token).apply {
            isHttpOnly = true
            path = "/"
            maxAge = 7 * 24 * 60 * 60  // 7 days in seconds
            secure = false
            setAttribute("SameSite", "Lax")

        })

        return Result.success(response.copy(token = "", message = "Login successful"))
    }

    @GetMapping("/logout")
    fun logout(httpRes: HttpServletResponse): Result<Unit> {

        httpRes.addCookie(Cookie(AUTH_COOKIE_NAME, "").apply {
            isHttpOnly = true
            path = "/"
            maxAge = 0
            secure = false
            setAttribute("SameSite", "Lax")
        })
        return Result.success(Unit)
    }

    @GetMapping("/me")
    fun me(httpReq: HttpServletRequest): Result<UserData> {

        jwtService.extractJwtFromRequest(httpReq).let {
            if (it == null) {
                return Result.failure(Exception("Invalid JWT"))
            }
            val username = jwtService.extractUserId(it)
            if (jwtService.validateToken(it, username)) {
                val user = authService.getUserByUsername(username) ?: return Result.failure(Exception("User not found"))
                return Result.success(UserData(user.id.toString(), user.username))
            }
            return Result.failure(Exception("Invalid JWT"))
        }
    }

    @GetMapping("/is-authenticated")
    fun isAuthenticated(httpReq: HttpServletRequest): Result<Boolean> =
        Result.success(jwtService.extractJwtFromRequest(httpReq).let { it != null && jwtService.validateToken(it) })
}