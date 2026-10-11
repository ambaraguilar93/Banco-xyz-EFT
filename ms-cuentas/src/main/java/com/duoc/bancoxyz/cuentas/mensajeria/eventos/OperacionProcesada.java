package com.duoc.bancoxyz.cuentas.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.duoc.bancoxyz.cuentas.mensajeria.Evento;

/** Respuesta a ms-pagos. resultado: APLICADA o RECHAZADA (con motivo). */
public record OperacionProcesada(String eventId, String tipo, String operacionId, String tipoOperacion,
        String resultado, String motivo, Long cuentaOrigenId, Long cuentaDestinoId, Long clienteOrigenId,
        Long clienteDestinoId, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, String instancia,
        String correlationId, OffsetDateTime fecha) implements Evento {

    public static final String TIPO = "OperacionProcesada";
    public static final String APLICADA = "APLICADA";
    public static final String RECHAZADA = "RECHAZADA";
}
