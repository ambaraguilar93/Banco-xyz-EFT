package com.duoc.bancoxyz.bff.movil.backend;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.bff.movil.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Movimiento;
import com.duoc.bancoxyz.bff.movil.comun.AccesoDenegadoException;

/** ms-cuentas (balanceado por Eureka). Circuit Breaker "msCuentas". */
@Component
public class CuentasApi {

    private final RestClient restClient;

    public CuentasApi(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-cuentas").build();
    }

    @CircuitBreaker(name = "msCuentas")
    public Cuenta cuenta(String token, Long id) {
        return restClient.get().uri("/api/cuentas/{id}", id).headers(h -> h.setBearerAuth(token))
                .retrieve().body(Cuenta.class);
    }

    @CircuitBreaker(name = "msCuentas")
    public List<Cuenta> cuentasDe(String token, Long clienteId) {
        Cuenta[] cuentas = restClient.get().uri("/api/cuentas?clienteId={id}", clienteId)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Cuenta[].class);
        return cuentas == null ? List.of() : Arrays.asList(cuentas);
    }

    @CircuitBreaker(name = "msCuentas")
    public List<Movimiento> movimientos(String token, Long cuentaId, int limite) {
        Movimiento[] movimientos = restClient.get().uri("/api/cuentas/{id}/movimientos?limite={l}", cuentaId, limite)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Movimiento[].class);
        return movimientos == null ? List.of() : Arrays.asList(movimientos);
    }

    @CircuitBreaker(name = "msCuentas")
    public Cuenta abrir(String token, Map<String, Object> solicitud) {
        return restClient.post().uri("/api/cuentas").headers(h -> h.setBearerAuth(token)).body(solicitud)
                .retrieve().body(Cuenta.class);
    }

    /**
     * El microservicio valida el token y el scope, pero es el BFF el
     * que sabe quien es el usuario del canal. Una cuenta de otro cliente responde
     * 403.
     */
    @CircuitBreaker(name = "msCuentas")
    public Cuenta cuentaPropia(String token, Long cuentaId, Long clienteId) {
        Cuenta cuenta = cuenta(token, cuentaId);
        if (cuenta == null || !cuenta.clienteId().equals(clienteId)) {
            throw new AccesoDenegadoException("La cuenta " + cuentaId + " no pertenece al cliente autenticado");
        }
        return cuenta;
    }
}
