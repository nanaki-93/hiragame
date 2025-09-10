package com.github.nanaki_93.config

import com.github.nanaki_93.repository.ToUserDetail
import com.github.nanaki_93.repository.UserRepository

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails =
        userRepository.findByUsername(username)
            .orElseThrow { UsernameNotFoundException("User not found with name: $username") }
            .ToUserDetail()


}