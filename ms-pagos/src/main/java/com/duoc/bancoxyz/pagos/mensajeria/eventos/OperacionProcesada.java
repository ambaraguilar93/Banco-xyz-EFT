package com.duoc.bancoxyz.pagos.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.duoc.bancoxyz.pagos.mensajeria.Evento;

/** Respuesta de ms-cuentas. resultado: APLICADA o RECHAZADA. */
public record OperacionProcesada(String eventId, String tipo, String operacionId, String tipoOperacion,
        String resultado, String motivo, Long cuentaOrigenId, Long cuentaDestinoId, Long clienteOrigenId,
        Long clienteDestinoId, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, String instancia,
        String correlationId, OffsetDateTime fecha) implements Evento {

    public boolean aplicada() {
        return "APLICADA".equals(resultado);
    }
}
