package com.duoc.bancoxyz.bff.cajero.http;

import java.time.Duration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.duoc.bancoxyz.bff.cajero.comun.CorrelationId;

/**
 * Clientes HTTP hacia el backend. Con @LoadBalanced, "http://ms-cuentas" se
 * resuelve en Eureka y las llamadas se reparten entre las instancias.
 * El token no se agrega aca: cada llamada lleva explicitamente el token
 * DELEGADO del canal.
 */
@Configuration
public class ClienteHttpConfig {

    /** Builder sin balanceo para el propio cliente de Eureka (ver ms-pagos). */
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
        fabrica.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder()
                .requestFactory(fabrica)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(CorrelationId.HEADER_HTTP, CorrelationId.actual());
                    return execution.execute(request, body);
                });
    }
}
