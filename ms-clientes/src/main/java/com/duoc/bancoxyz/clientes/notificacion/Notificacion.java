package com.duoc.bancoxyz.clientes.notificacion;

import java.time.OffsetDateTime;

/**
 * Entrada del historial del cliente. categoria: TRANSACCION, CUENTA o ALERTA.
 * nivel: INFO (o BAJA/MEDIA/ALTA para las alertas de seguridad).
 */
public record Notificacion(Long id, Long clienteId, String categoria, String nivel, String titulo, String detalle,
        String referencia, String origen, OffsetDateTime fecha) {
}
