package com.duoc.bancoxyz.clientes.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.duoc.bancoxyz.clientes.mensajeria.Evento;

/** De ms-pagos: cierre de un pago, transferencia o deposito (COMPLETADA o RECHAZADA). */
public record TransaccionCompletada(String eventId, String tipo, String operacionId, String tipoOperacion,
        String estado, String motivo, Long cuentaOrigenId, Long cuentaDestinoId, Long clienteOrigenId,
        Long clienteDestinoId, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, String descripcion,
        String correlationId, OffsetDateTime fecha) implements Evento {
}
