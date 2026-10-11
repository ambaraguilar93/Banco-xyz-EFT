package com.duoc.bancoxyz.cuentas.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.duoc.bancoxyz.cuentas.mensajeria.Evento;

/** ms-pagos pide aplicar una operacion. tipoOperacion: PAGO, RETIRO, TRANSFERENCIA o DEPOSITO. */
public record OperacionSolicitada(String eventId, String tipo, String operacionId, String tipoOperacion,
        Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto, String descripcion, String correlationId,
        OffsetDateTime fecha) implements Evento {
}
