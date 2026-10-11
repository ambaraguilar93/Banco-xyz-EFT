package com.duoc.bancoxyz.cuentas.mensajeria.eventos;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.duoc.bancoxyz.cuentas.comun.CorrelationId;
import com.duoc.bancoxyz.cuentas.mensajeria.Evento;

/** Alerta de seguridad para el cliente. nivel: BAJA, MEDIA o ALTA. */
public record AlertaSeguridad(String eventId, String tipo, String nivel, String codigo, String descripcion,
        Long clienteId, Long cuentaId, String operacionId, String origen, String correlationId, OffsetDateTime fecha)
        implements Evento {

    public static AlertaSeguridad de(String nivel, String codigo, String descripcion, Long clienteId, Long cuentaId,
            String operacionId) {
        return new AlertaSeguridad(UUID.randomUUID().toString(), "AlertaSeguridad", nivel, codigo, descripcion,
                clienteId, cuentaId, operacionId, "ms-cuentas", CorrelationId.actual(), OffsetDateTime.now());
    }
}
