package com.duoc.bancoxyz.auth.config;

import com.duoc.bancoxyz.auth.security.OAuth2LoginFailureHandler;
import com.duoc.bancoxyz.auth.security.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del auth-server.
 *
 * Publico: la informacion de login, el inicio del flujo con GitHub, el
 * callback, la clave publica (JWKS) y el health de Actuator.
 * Lo demas requiere sesion.
 */
@Configuration
public class SecurityConfig {

        private final OAuth2LoginSuccessHandler successHandler;
        private final OAuth2LoginFailureHandler failureHandler;

        public SecurityConfig(OAuth2LoginSuccessHandler successHandler, OAuth2LoginFailureHandler failureHandler) {
                this.successHandler = successHandler;
                this.failureHandler = failureHandler;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/api/auth/**",
                                                                "/oauth2/**",
                                                                "/login/**",
                                                                "/.well-known/jwks.json",
                                                                "/actuator/health",
                                                                "/actuator/health/**",
                                                                "/actuator/info",
                                                                "/error")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(successHandler)
                                                .failureHandler(failureHandler))
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable);

                return http.build();
        }
}
