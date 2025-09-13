package com.github.nanaki_93.config

import com.github.nanaki_93.models.ACCESS_TOKEN
import com.github.nanaki_93.service.JWTService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


@Component
class JwtAuthenticationFilter(
    private val jwtService: JWTService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {


    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            if (request.servletPath.startsWith("/api/auth")) {
                filterChain.doFilter(request, response)
                return
            }

            val jwt = jwtService.extractTokenFromRequest(request, ACCESS_TOKEN)

            if (jwt == null) {
                filterChain.doFilter(request, response)
                return
            }

            if (SecurityContextHolder.getContext().authentication == null) {
                authenticateUser(request, jwt)
            }
        } catch (e: Exception) {
            logger.warn("JWT authentication failed", e)
        }

        filterChain.doFilter(request, response)
    }


    private fun authenticateUser(request: HttpServletRequest, jwt: String) {
        val username = jwtService.extractUsername(jwt)
        if (jwtService.validateToken(jwt)) {

            userDetailsService.loadUserByUsername(username).let {
                UsernamePasswordAuthenticationToken(it, null, it.authorities)
            }.apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = this
            }

            logger.debug("Successfully authenticated user: $username")
        }
    }

}