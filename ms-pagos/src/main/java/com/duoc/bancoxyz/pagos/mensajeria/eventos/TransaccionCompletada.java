package com.duoc.bancoxyz.pagos.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.duoc.bancoxyz.pagos.comun.CorrelationId;
import com.duoc.bancoxyz.pagos.mensajeria.Evento;
import com.duoc.bancoxyz.pagos.operacion.Operacion;

/** Cierre de una operacion (COMPLETADA o RECHAZADA): ms-clientes lo agrega al historial del cliente. */
public record TransaccionCompletada(String eventId, String tipo, String operacionId, String tipoOperacion,
        String estado, String motivo, Long cuentaOrigenId, Long cuentaDestinoId, Long clienteOrigenId,
        Long clienteDestinoId, BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, String descripcion,
        String correlationId, OffsetDateTime fecha) implements Evento {

    public static TransaccionCompletada de(Operacion op, OperacionProcesada resultado) {
        return new TransaccionCompletada(UUID.randomUUID().toString(), "TransaccionCompletada", op.id(), op.tipo(),
                op.estado(), op.motivo(), op.cuentaOrigenId(), op.cuentaDestinoId(), op.clienteOrigenId(),
                op.clienteDestinoId(), op.monto(), resultado.saldoOrigen(), resultado.saldoDestino(), op.descripcion(),
                CorrelationId.actual(), OffsetDateTime.now());
    }
}
