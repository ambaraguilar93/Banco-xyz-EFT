package com.duoc.bancoxyz.pagos.cuentas;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import com.duoc.bancoxyz.pagos.comun.RecursoNoEncontradoException;

/**
 * Llamada sincrona a ms-cuentas para validar una cuenta antes de registrar la
 * operacion.
 * 
 */
@Component
public class CuentasClient {

    private static final Logger log = LoggerFactory.getLogger(CuentasClient.class);

    private final RestClient restClient;

    public CuentasClient(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://ms-cuentas").build();
    }

    /**
     * @return la cuenta, o vacio si ms-cuentas no esta disponible (validacion
     *         diferida).
     */
    @Retry(name = "msCuentas", fallbackMethod = "sinValidacion")
    @CircuitBreaker(name = "msCuentas")
    public Optional<CuentaResumen> consultar(Long cuentaId) {
        try {
            CuentaResumen cuenta = restClient.get().uri("/api/cuentas/{id}", cuentaId).retrieve()
                    .body(CuentaResumen.class);
            return Optional.ofNullable(cuenta);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecursoNoEncontradoException("La cuenta " + cuentaId + " no existe");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new IllegalStateException("ms-cuentas rechazo el token delegado (" + e.getStatusCode() + ")");
            }
            throw e;
        }
    }

    /** La cuenta no existe: es una respuesta valida de ms-cuentas, no una falla. */
    @SuppressWarnings("unused")
    private Optional<CuentaResumen> sinValidacion(Long cuentaId, RecursoNoEncontradoException e) {
        throw e;
    }

    @SuppressWarnings("unused")
    private Optional<CuentaResumen> sinValidacion(Long cuentaId, CallNotPermittedException e) {
        log.warn("[RESILIENCIA] Circuit Breaker msCuentas ABIERTO: la cuenta {} se validara de forma diferida",
                cuentaId);
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    private Optional<CuentaResumen> sinValidacion(Long cuentaId, Exception e) {
        log.warn("[RESILIENCIA] ms-cuentas no respondio tras los reintentos ({}): la cuenta {} se validara de "
                + "forma diferida", e.getMessage(), cuentaId);
        return Optional.empty();
    }
}
