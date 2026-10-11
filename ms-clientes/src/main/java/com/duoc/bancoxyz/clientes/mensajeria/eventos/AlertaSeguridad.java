package com.duoc.bancoxyz.clientes.mensajeria.eventos;

import java.time.OffsetDateTime;

import com.duoc.bancoxyz.clientes.mensajeria.Evento;

/** De ms-pagos o ms-cuentas. nivel: BAJA, MEDIA o ALTA. */
public record AlertaSeguridad(String eventId, String tipo, String nivel, String codigo, String descripcion,
        Long clienteId, Long cuentaId, String operacionId, String origen, String correlationId, OffsetDateTime fecha)
        implements Evento {
}
