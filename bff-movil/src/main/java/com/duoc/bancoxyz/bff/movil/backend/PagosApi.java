package com.duoc.bancoxyz.bff.movil.backend;

import java.util.Arrays;
import java.util.List;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.bff.movil.backend.Modelos.Operacion;
import com.duoc.bancoxyz.bff.movil.comun.AccesoDenegadoException;
import com.duoc.bancoxyz.bff.movil.comun.ReglaNegocioException;

/** ms-pagos (balanceado por Eureka). Circuit Breaker "msPagos". */
@Component
public class PagosApi {

    private final RestClient restClient;

    public PagosApi(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-pagos").build();
    }

    /** POST /api/{pagos|transferencias|depositos|retiros} con su Idempotency-Key. */
    @CircuitBreaker(name = "msPagos")
    public Operacion operar(String token, String ruta, String idempotencyKey, Object solicitud) {
        return restClient.post().uri("/api/" + ruta)
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", idempotencyKey);
                })
                .body(solicitud).retrieve().body(Operacion.class);
    }

    @CircuitBreaker(name = "msPagos")
    public Operacion operacion(String token, String id) {
        return restClient.get().uri("/api/operaciones/{id}", id).headers(h -> h.setBearerAuth(token))
                .retrieve().body(Operacion.class);
    }

    @CircuitBreaker(name = "msPagos")
    public List<Operacion> operacionesDeCuenta(String token, Long cuentaId, int limite) {
        Operacion[] operaciones = restClient.get().uri("/api/operaciones?cuentaId={id}&limite={l}", cuentaId, limite)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Operacion[].class);
        return operaciones == null ? List.of() : Arrays.asList(operaciones);
    }

    /**
     * El Idempotency-Key repetido devuelve la operacion ya registrada: se confirma que sea del
     * mismo cliente antes de mostrarla (no se filtran operaciones ajenas).
     */
    public Operacion verificarPropia(Operacion operacion, Long clienteId, Long cuentaOrigenId) {
        boolean ajena = operacion == null || !cuentaOrigenId.equals(operacion.cuentaOrigenId())
                || (operacion.clienteOrigenId() != null && !operacion.involucraA(clienteId));
        if (ajena) {
            throw new ReglaNegocioException("El Idempotency-Key ya fue usado en otra operacion: genere una clave nueva");
        }
        return operacion;
    }

    /** Una operacion solo la puede ver un cliente que participa en ella (origen o destino). */
    @CircuitBreaker(name = "msPagos")
    public Operacion operacionPropia(String token, String id, Long clienteId) {
        Operacion operacion = operacion(token, id);
        if (operacion == null || !operacion.involucraA(clienteId)) {
            throw new AccesoDenegadoException("La operacion " + id + " no pertenece al cliente autenticado");
        }
        return operacion;
    }
}
