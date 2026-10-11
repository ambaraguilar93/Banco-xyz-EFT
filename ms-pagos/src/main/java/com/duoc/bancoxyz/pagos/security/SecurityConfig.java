package com.duoc.bancoxyz.pagos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth 2.0 Resource Server: cada request debe traer un JWT del auth-server.
 * Permisos por scope: consultar operaciones con pagos.read, registrar
 * operaciones con pagos.write.
 */
@Configuration
public class SecurityConfig {

        private final JsonAuthenticationEntryPoint entryPoint;
        private final JsonAccessDeniedHandler accessDenied;

        public SecurityConfig(JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDenied) {
                this.entryPoint = entryPoint;
                this.accessDenied = accessDenied;
        }

        private static boolean esDelCajero(Authentication autenticacion) {
                return autenticacion instanceof JwtAuthenticationToken jwt
                                && "cajero".equals(jwt.getToken().getClaimAsString("canal"))
                                && jwt.getAuthorities().stream()
                                                .anyMatch(a -> "SCOPE_pagos.write".equals(a.getAuthority()));
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(
                                                sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/error", "/actuator/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/operaciones",
                                                                "/api/operaciones/**")
                                                .hasAuthority("SCOPE_pagos.read")
                                                .requestMatchers(HttpMethod.POST, "/api/pagos", "/api/transferencias",
                                                                "/api/depositos")
                                                .hasAuthority("SCOPE_pagos.write")
                                                // EFT Parte 2: los retiros solo llegan desde el BFF del cajero (token
                                                // delegado canal=cajero)
                                                .requestMatchers(HttpMethod.POST, "/api/retiros")
                                                .access((autenticacion, contexto) -> new AuthorizationDecision(
                                                                esDelCajero(autenticacion.get())))
                                                .anyRequest().denyAll())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(Customizer.withDefaults())
                                                .authenticationEntryPoint(entryPoint)
                                                .accessDeniedHandler(accessDenied))
                                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                                                .accessDeniedHandler(accessDenied));
                return http.build();
        }
}
