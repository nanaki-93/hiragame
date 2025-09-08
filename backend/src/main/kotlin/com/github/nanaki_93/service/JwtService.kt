
package com.github.nanaki_93.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JWTService {

    @Value("\${jwt.secret:your-super-secret-key-here-make-it-long-and-random-at-least-256-bits}")
    private lateinit var secretKey: String

    @Value("\${jwt.expiration:86400000}")
    private var jwtExpiration: Long = 86400000 // 24 hours

    @Value("\${jwt.refresh-expiration:604800000}")
    private var refreshExpiration: Long = 604800000 // 7 days

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray())
    }


    fun generateAccessToken(userId: String, name: String): String {
        return generateToken(userId, name, jwtExpiration)
    }

    fun generateRefreshToken(userId: String, name: String): String {
        return generateToken(userId, name, refreshExpiration)
    }

    private fun generateToken(userId: String, name: String, expiration: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(userId)
            .claim("name", name)
            .claim("userId", userId)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    fun extractUserId(token: String): String {
        return extractClaim(token) { claims -> claims.subject }
    }

    fun extractName(token: String): String {
        return extractClaim(token) { claims -> claims.get("name", String::class.java) }
    }

    fun extractExpiration(token: String): Date {
        return extractClaim(token) { claims -> claims.expiration }
    }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }


    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun isTokenExpired(token: String): Boolean {
        return extractExpiration(token).before(Date())
    }

    fun validateToken(token: String, userId: String): Boolean {
        val tokenUserId = extractUserId(token)
        return (tokenUserId == userId && !isTokenExpired(token))
    }

    fun validateToken(token: String): Boolean {
        return try {
            !isTokenExpired(token)
        } catch (e: Exception) {
            false
        }
    }
}