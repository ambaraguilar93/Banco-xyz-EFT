package com.duoc.bancoxyz.bff.movil.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Seguridad del canal MOVIL.
 *
 * - Solo HTTPS (certificado propio del BFF movil).
 * - Autenticacion: JWT del login del usuario (mismo proveedor que la web) + dispositivo
 *   identificado: cada request trae X-Device-Id (la app lo genera al instalarse). Queda en
 *   el log de cada operacion para poder rastrear o bloquear un dispositivo.
 * - Autorizacion: el token delegado del canal movil tiene permisos REDUCIDOS (sin
 *   cuentas.write ni clientes.write) y las transferencias tienen un tope por canal.
 * - Sin CORS: una app nativa no es un navegador.
 */
@Configuration
public class SecurityConfig {

    public static final String HEADER_DISPOSITIVO = "X-Device-Id";

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
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(h -> h.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/**").permitAll()
                        .requestMatchers("/api/movil/**").access((autenticacion, contexto) ->
                                new AuthorizationDecision(esUsuarioDelBanco(autenticacion.get())))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
        return http.build();
    }

    /**
     * ETag: el cliente movil reenvia If-None-Match y, si nada cambio, recibe 304 sin cuerpo
     * (ahorra datos moviles). Junto con gzip (server.compression) reduce el trafico.
     */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etag() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registro = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/api/movil/*");
        return registro;
    }
}
