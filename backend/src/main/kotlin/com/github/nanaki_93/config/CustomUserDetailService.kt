package com.github.nanaki_93.config

import com.github.nanaki_93.repository.UserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.*

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(userId: String): UserDetails {
        val user = try {
            val uuid = UUID.fromString(userId)
            userRepository.findById(uuid.toString())
        } catch (e: IllegalArgumentException) {
            // If userId is not a valid UUID, try to find by name
            userRepository.findByName(userId)
        }.orElseThrow { UsernameNotFoundException("User not found with id: $userId") }

        return User.builder()
            .username(user.id.toString())
            .password(user.password)
            .authorities(emptyList())
            .build()
    }
}