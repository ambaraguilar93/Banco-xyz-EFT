package com.duoc.bancoxyz.bff.web.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad del canal WEB.
 *
 * - Solo HTTPS.
 * - Autenticacion: JWT del login del usuario.
 * Se exige un token de USUARIO asociado a un cliente.
 * - CORS.
 * - Cabeceras.
 */
@Configuration
public class SecurityConfig {

        private final JsonAuthenticationEntryPoint entryPoint;
        private final JsonAccessDeniedHandler accessDenied;

        public SecurityConfig(JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDenied) {
                this.entryPoint = entryPoint;
                this.accessDenied = accessDenied;
        }

        static boolean esUsuarioDelBanco(Authentication autenticacion) {
                return autenticacion instanceof JwtAuthenticationToken jwt
                                && !jwt.getToken().hasClaim("canal")
                                && jwt.getToken().getClaim("clienteId") instanceof Number;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable) // API sin cookies: el token va en el header
                                                                       // Authorization
                                .cors(Customizer.withDefaults())
                                .sessionManagement(
                                                sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .headers(h -> h
                                                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000))
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'none'; frame-ancestors 'none'"))
                                                .frameOptions(f -> f.deny()))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/error", "/actuator/**").permitAll()
                                                .requestMatchers("/api/web/**")
                                                .access((autenticacion, contexto) -> new AuthorizationDecision(
                                                                esUsuarioDelBanco(autenticacion.get())))
                                                .anyRequest().denyAll())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(Customizer.withDefaults())
                                                .authenticationEntryPoint(entryPoint)
                                                .accessDeniedHandler(accessDenied))
                                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                                                .accessDeniedHandler(accessDenied));
                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource(
                        @Value("${bff.web.cors.origenes:https://localhost:3000}") List<String> origenes) {
                CorsConfiguration cors = new CorsConfiguration();
                cors.setAllowedOrigins(origenes);
                cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
                cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
                cors.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
                cors.setMaxAge(3600L);
                UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
                fuente.registerCorsConfiguration("/api/**", cors);
                return fuente;
        }
}
