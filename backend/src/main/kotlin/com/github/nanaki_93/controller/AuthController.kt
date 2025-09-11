package com.github.nanaki_93.controller


import com.github.nanaki_93.models.ACCESS_TOKEN
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
    fun login(@RequestBody loginRegisterRequest: LoginRegisterRequest, httpRes: HttpServletResponse) =
        authService.login(loginRegisterRequest).also {
            addCookie(httpRes, it.token, ACCESS_TOKEN)
            addCookie(httpRes, it.token, REFRESH_TOKEN)
        }.let {
            "Login successful"
        }


    @PostMapping("/register")
    fun register(@RequestBody registerRequest: LoginRegisterRequest, httpRes: HttpServletResponse) =
        authService.register(registerRequest).also {
            addCookie(httpRes, it.token, ACCESS_TOKEN)
            addCookie(httpRes, it.token, REFRESH_TOKEN)
        }.let { "User registered successfully" }


    @GetMapping("/refresh-token")
    fun refreshToken(httpReq: HttpServletRequest, httpRes: HttpServletResponse) =
        authService.refreshToken(
            httpReq.cookies?.find { it.name == REFRESH_TOKEN }?.value ?: ""
        ).also {
            addCookie(httpRes, it.token, ACCESS_TOKEN)
            addCookie(httpRes, it.token, REFRESH_TOKEN)
        }.let { "Token Refreshed" }


    @GetMapping("/logout")
    fun logout(httpRes: HttpServletResponse) {
        addCookie(httpRes, cookieName = ACCESS_TOKEN)
        addCookie(httpRes, cookieName = REFRESH_TOKEN)
    }

    @GetMapping("/me")
    fun me(httpReq: HttpServletRequest): UserData {

        jwtService.extractJwtFromRequest(httpReq).let {

            if (it == null) {
                logger.info("Invalid JWT")
                throw Exception("Invalid JWT")
            }
            val username = jwtService.extractUsername(it)
            if (jwtService.validateToken(it, username)) {
                val user = authService.getUserByUsername(username) ?: throw Exception("User not found")
                logger.info("User data retrieved: $user")
                return UserData(user.id.toString(), user.username)
            }

            logger.info("Invalid JWT")
            throw Exception("Invalid JWT")
        }
    }

    @GetMapping("/is-authenticated")
    fun isAuthenticated(httpReq: HttpServletRequest): Boolean =
        jwtService.extractJwtFromRequest(httpReq).let { it != null && jwtService.validateToken(it) }

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