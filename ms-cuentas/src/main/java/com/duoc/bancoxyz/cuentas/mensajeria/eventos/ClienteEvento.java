package com.duoc.bancoxyz.cuentas.mensajeria.eventos;

import java.time.OffsetDateTime;

import com.duoc.bancoxyz.cuentas.mensajeria.Evento;

/** Evento de ms-clientes: ClienteRegistrado, ClienteActualizado, ClienteDesactivado, ClienteReactivado. */
public record ClienteEvento(String eventId, String tipo, Long clienteId, String nombre, String estado,
        String correlationId, OffsetDateTime fecha) implements Evento {
}
