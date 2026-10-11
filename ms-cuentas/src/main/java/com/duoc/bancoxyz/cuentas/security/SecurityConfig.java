package com.duoc.bancoxyz.cuentas.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth 2.0 Resource Server: cada request debe traer un JWT del auth-server.
 * Permisos por scope: lectura con cuentas.read, cambios con cuentas.write.
 */
@Configuration
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint entryPoint;
    private final JsonAccessDeniedHandler accessDenied;

    public SecurityConfig(JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDenied) {
        this.entryPoint = entryPoint;
        this.accessDenied = accessDenied;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cuentas", "/api/cuentas/**")
                        .hasAuthority("SCOPE_cuentas.read")
                        .requestMatchers(HttpMethod.POST, "/api/cuentas").hasAuthority("SCOPE_cuentas.write")
                        // EFT Parte 2: solo el token de servicio del BFF del cajero
                        .requestMatchers(HttpMethod.POST, "/api/tarjetas/verificacion")
                        .hasAuthority("SCOPE_tarjetas.verify")
                        .requestMatchers(HttpMethod.PATCH, "/api/cuentas/**").hasAuthority("SCOPE_cuentas.write")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
        return http.build();
    }
}
