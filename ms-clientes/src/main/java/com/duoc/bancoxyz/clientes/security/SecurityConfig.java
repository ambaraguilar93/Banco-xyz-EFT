package com.duoc.bancoxyz.clientes.security;

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
 * Permisos por scope: lectura con clientes.read, cambios con clientes.write.
 * El perfil reenvia el mismo token a ms-cuentas (token relay), que exige
 * cuentas.read.
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
                        .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/**")
                        .hasAuthority("SCOPE_clientes.read")
                        .requestMatchers(HttpMethod.POST, "/api/clientes").hasAuthority("SCOPE_clientes.write")
                        .requestMatchers(HttpMethod.PATCH, "/api/clientes/**").hasAuthority("SCOPE_clientes.write")
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasAuthority("SCOPE_clientes.write")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
        return http.build();
    }
}
