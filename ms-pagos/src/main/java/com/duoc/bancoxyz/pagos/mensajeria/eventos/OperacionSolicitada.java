package com.duoc.bancoxyz.pagos.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.duoc.bancoxyz.pagos.comun.CorrelationId;
import com.duoc.bancoxyz.pagos.mensajeria.Evento;
import com.duoc.bancoxyz.pagos.operacion.Operacion;

/** Pide a ms-cuentas aplicar una operacion. tipoOperacion: PAGO, RETIRO, TRANSFERENCIA o DEPOSITO. */
public record OperacionSolicitada(String eventId, String tipo, String operacionId, String tipoOperacion,
        Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto, String descripcion, String correlationId,
        OffsetDateTime fecha) implements Evento {

    public static OperacionSolicitada de(Operacion operacion) {
        return new OperacionSolicitada(UUID.randomUUID().toString(), "OperacionSolicitada", operacion.id(),
                operacion.tipo(), operacion.cuentaOrigenId(), operacion.cuentaDestinoId(), operacion.monto(),
                operacion.descripcion(), CorrelationId.actual(), OffsetDateTime.now());
    }
}
