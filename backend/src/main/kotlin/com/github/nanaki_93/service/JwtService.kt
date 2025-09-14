package com.github.nanaki_93.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jws
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JWTService {

    @Value($$"${jwt.secret:your-super-secret-key-here-make-it-long-and-random-at-least-256-bits}")
    private lateinit var secretKey: String

    @Value($$"${jwt.expiration:86400000}")
    private var jwtExpiration: Long = 86400000 // 24 hours

    @Value($$"${jwt.refresh.expiration:604800000}")
    private var refreshExpiration: Long = 604800000 // 7 days

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }


    fun generateAccessToken(userId: String, name: String): String = generateToken(userId, name, jwtExpiration)

    fun generateRefreshToken(userId: String, name: String): String = generateToken(userId, name, refreshExpiration)

    private fun generateToken(userId: String, username: String, expiration: Long): String =
        Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .issuedAt(Date())
            .expiration(Date(Date().time + expiration))
            .signWith(key)
            .compact()

    fun extractUserId(token: String): String = extractClaim(token) { claims -> claims.subject }
    fun extractUsername(token: String): String = extractClaim(token) { claims -> claims.get("username", String::class.java) }
    fun extractExpiration(token: String): Date = extractClaim(token) { claims -> claims.expiration }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T = claimsResolver(extractAllClaims(token))
    private fun extractAllClaims(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload

    private fun extractJwt(token: String): Jws<Claims> = Jwts.parser().verifyWith(key).build().parseSignedClaims(token)

    fun isTokenExpired(token: String): Boolean {
        val jwt = extractJwt(token)
        if (extractExpiration(token).before(Date())) {
            throw ExpiredJwtException(jwt.header, jwt.payload, "Token expired")
        }
        return false
    }

    //todo check if it can be remover
    fun validateToken(token: String, username: String): Boolean = try {
        (extractUsername(token) == username && !isTokenExpired(token))
    } catch (_: Exception) {
        false
    }

    fun validateToken(token: String): Boolean = try {
        !isTokenExpired(token)
    } catch (_: Exception) {
        false
    }


    fun extractTokenFromRequest(request: HttpServletRequest, type: String): String? {
        // First, try to get JWT from cookie
        request.cookies?.find { it.name == type }?.value?.let { return it }

        // Fallback to Authorization header (for backward compatibility or API clients)
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        return null
    }
}