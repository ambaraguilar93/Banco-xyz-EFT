package com.duoc.bancoxyz.bff.cajero.cajero;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuestas del CAJERO: minimas y orientadas a lo que muestra la pantalla o
 * imprime el comprobante. Nunca incluyen datos personales ni el numero completo
 * de cuenta o tarjeta.
 */
public final class VistasCajero {

    private VistasCajero() {
    }

    /** Sesion iniciada: token de 2 minutos ligado a esta cuenta y a este cajero. */
    public record Sesion(String token, long expiraEnSegundos, String cuenta, String tipoCuenta) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SesionRechazada(String mensaje, Integer intentosRestantes) {
    }

    public record Saldo(String cuenta, BigDecimal saldoDisponible, OffsetDateTime fechaHora) {
    }

    /**
     * resultado: APROBADO (entregar el dinero), RECHAZADO (no se entrega) o
     * EN_PROCESO (no se entrega, el cargo se resolvera y se puede consultar el
     * saldo).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultadoRetiro(String resultado, BigDecimal monto, BigDecimal saldoRestante, String comprobante,
            String mensaje, String terminal, OffsetDateTime fechaHora) {
    }
}
