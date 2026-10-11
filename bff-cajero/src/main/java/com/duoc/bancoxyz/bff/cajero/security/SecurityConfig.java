package com.duoc.bancoxyz.bff.cajero.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del canal CAJERO AUTOMATICO:
 *
 * 1. mTLS: solo se conectan equipos con certificado emitido por la CA de
 * cajeros.
 * 2. Tarjeta + PIN (POST /api/cajero/sesion): el BFF los valida contra
 * ms-cuentas
 * (3 intentos fallidos bloquean la tarjeta).
 * 3. Sesion: el auth-server emite un token delegado de 2 minutos con
 * canal=cajero, la
 * cuenta de la tarjeta y el terminal. Para saldo y retiro se exige ese token y
 * que el terminal del token sea el mismo que presenta el certificado (un token
 * robado no sirve en otro cajero).
 */
@Configuration
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint entryPoint;
    private final JsonAccessDeniedHandler accessDenied;

    public SecurityConfig(JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDenied) {
        this.entryPoint = entryPoint;
        this.accessDenied = accessDenied;
    }

    static boolean sesionDeEsteCajero(Authentication autenticacion, HttpServletRequest request) {
        if (!(autenticacion instanceof JwtAuthenticationToken jwtAuth)) {
            return false;
        }
        Jwt jwt = jwtAuth.getToken();
        Object terminal = request.getAttribute(TerminalCajeroFilter.ATRIBUTO_TERMINAL);
        return "cajero".equals(jwt.getClaimAsString("canal"))
                && jwt.getClaim("cuentaId") instanceof Number
                && terminal != null && terminal.equals(jwt.getClaimAsString("terminal"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/**").permitAll()
                        // tarjeta + PIN (el terminal ya se autentico con su certificado)
                        .requestMatchers(HttpMethod.POST, "/api/cajero/sesion").permitAll()
                        .requestMatchers("/api/cajero/**")
                        .access((autenticacion, contexto) -> new AuthorizationDecision(
                                sesionDeEsteCajero(autenticacion.get(), contexto.getRequest())))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
        return http.build();
    }
}
