package com.duoc.bancoxyz.discovery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Protege el registro de Eureka con Basic Auth:
 * solo clientes que conozcan usuario/clave (en application.properties)
 * pueden registrarse o consultar el registro.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/eureka/**").authenticated()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"));
        return http.build();
    }

}
