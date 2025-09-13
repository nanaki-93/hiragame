package com.github.nanaki_93.config

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
import kotlin.io.writer

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
            val jwt = jwtService.extractJwtFromRequest(request)

            if (jwt == null) {
                filterChain.doFilter(request, response)
                return
            }

            if (SecurityContextHolder.getContext().authentication == null) {
                authenticateUser(request, jwt)
            }
            filterChain.doFilter(request, response)
        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED // 401
        } catch (e: io.jsonwebtoken.JwtException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED // 401
        } catch (e: IllegalArgumentException) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED // 401
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