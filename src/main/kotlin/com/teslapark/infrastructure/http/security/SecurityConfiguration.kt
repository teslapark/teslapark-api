package com.teslapark.infrastructure.http.security

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("teslapark.security")
class SecurityConfiguration {
    var enabled: Boolean = false
    var tokens: Map<String, String> = emptyMap()

    fun scopesOf(token: String): List<String>? = tokens[token]?.split(",")?.map { it.trim() }
}
