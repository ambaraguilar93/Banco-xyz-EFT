package com.duoc.bancoxyz.clientes.http;

import java.time.Duration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP entre microservicios con BALANCEO DE CARGA y ENRUTAMIENTO por
 * nombre.
 * Con @LoadBalanced, "http://ms-cuentas" se resuelve en Eureka y Spring Cloud
 * LoadBalancer reparte las llamadas entre todas las instancias registradas.
 * Timeouts cortos: un servicio lento no bloquea a quien lo llama (el Circuit
 * Breaker cuenta esos timeouts como fallas).
 */
@Configuration
public class ClienteHttpConfig {

    /**
     * Builder normal, marcado @Primary. Es el que toma el propio
     * cliente de Eureka para registrarse en http://discovery-server:8761.
     * Sin este bean, Eureka usaria el builder @LoadBalanced de abajo, intentaria
     * resolver "discovery-server" como si fuera un microservicio registrado y la
     * instancia nunca apareceria en el dashboard.
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBalanceado() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor(new PropagacionInterceptor());
    }
}
