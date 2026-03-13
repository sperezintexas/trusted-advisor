package com.atxbogart.trustedadvisor.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.auth-secret:}") private val authSecret: String,
    @Value("\${app.skip-auth:false}") private val skipAuth: Boolean,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String
) {

    @Bean
    @Order(1)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            // OAuth2 login creates a session so /api/me etc. can use the principal after redirect.
            .oauth2Login { oauth2 ->
                oauth2.loginPage("/login")
                oauth2.defaultSuccessUrl(frontendUrl, true)
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }

        if (skipAuth) {
            http.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/health", "/error").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                    .requestMatchers("/api/**").permitAll()
                    .requestMatchers("/login", "/oauth2/**").permitAll()
                    .anyRequest().permitAll()
            }
        } else {
            val apiKeyFilter = ApiKeyAuthenticationFilter(authSecret)
            http
                .addFilterBefore(apiKeyFilter, AuthorizationFilter::class.java)
                .exceptionHandling { e ->
                    e.defaultAuthenticationEntryPointFor(
                        HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        AntPathRequestMatcher.antMatcher("/api/**")
                    )
                }
                .authorizeHttpRequests { auth ->
                    auth
                        .requestMatchers("/", "/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/chat/config/test").permitAll()
                        .requestMatchers("/api/debug/auth").permitAll()
                        .requestMatchers("/api/stripe/webhook").permitAll()
                        .requestMatchers("/api/logout").permitAll()
                        .requestMatchers("/login", "/oauth2/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                }
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowCredentials = true
            addAllowedOrigin("http://localhost:3000")
            addAllowedOrigin("http://127.0.0.1:3000")
            addAllowedOriginPattern("https://.*")
            addAllowedHeader("*")
            addAllowedMethod("*")
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
