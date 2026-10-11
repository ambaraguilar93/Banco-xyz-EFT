package com.duoc.bancoxyz.bff.cajero.backend;

import java.util.Map;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Verificacion de tarjeta + PIN en ms-cuentas, con el token de servicio del
 * BFF.
 */
@Component
public class TarjetasApi {

    public record Verificacion(Boolean valida, String motivo, Integer intentosRestantes, Long cuentaId,
            Long clienteId, String tipoCuenta) {
    }

    private final RestClient restClient;

    public TarjetasApi(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-cuentas").build();
    }

    @CircuitBreaker(name = "msCuentas")
    public Verificacion verificar(String tokenServicio, String numeroTarjeta, String pin) {
        return restClient.post().uri("/api/tarjetas/verificacion").headers(h -> h.setBearerAuth(tokenServicio))
                .body(Map.of("numeroTarjeta", numeroTarjeta, "pin", pin))
                .retrieve().body(Verificacion.class);
    }
}
