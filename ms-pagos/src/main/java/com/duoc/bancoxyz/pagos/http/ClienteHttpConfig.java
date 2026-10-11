package com.duoc.bancoxyz.pagos.http;

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
 * Un servicio lento no bloquea a quien lo llama (el Circuit Breaker
 * cuenta esos timeouts como fallas).
 */
@Configuration
public class ClienteHttpConfig {

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
