package com.duoc.bancoxyz.clientes.cuentas;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Cuentas de un cliente, consultadas a ms-cuentas (balanceado por
 * Eureka/LoadBalancer). Circuit Breaker "msCuentas" con COMPORTAMIENTO
 * ALTERNATIVO: si ms-cuentas no responde, el perfil se entrega igual, sin
 * cuentas y con el aviso "cuentasDisponibles=false".
 */
@Component
public class CuentasClient {

    private static final Logger log = LoggerFactory.getLogger(CuentasClient.class);

    public record Resultado(boolean disponible, List<CuentaResumen> cuentas) {
    }

    private final RestClient restClient;

    public CuentasClient(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-cuentas").build();
    }

    @CircuitBreaker(name = "msCuentas", fallbackMethod = "sinCuentas")
    public Resultado cuentasDe(Long clienteId) {
        CuentaResumen[] cuentas = restClient.get().uri("/api/cuentas?clienteId={id}", clienteId).retrieve()
                .body(CuentaResumen[].class);
        return new Resultado(true, cuentas == null ? List.of() : Arrays.asList(cuentas));
    }

    @SuppressWarnings("unused")
    private Resultado sinCuentas(Long clienteId, Throwable e) {
        log.warn("[RESILIENCIA] ms-cuentas no disponible ({}): perfil del cliente {} sin cuentas", e.toString(),
                clienteId);
        return new Resultado(false, List.of());
    }
}
