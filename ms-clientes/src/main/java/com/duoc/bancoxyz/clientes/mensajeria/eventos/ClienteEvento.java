package com.duoc.bancoxyz.clientes.mensajeria.eventos;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.duoc.bancoxyz.clientes.cliente.Cliente;
import com.duoc.bancoxyz.clientes.comun.CorrelationId;
import com.duoc.bancoxyz.clientes.mensajeria.Evento;

/** ClienteRegistrado, ClienteActualizado, ClienteDesactivado o ClienteReactivado. */
public record ClienteEvento(String eventId, String tipo, Long clienteId, String nombre, String estado,
        String correlationId, OffsetDateTime fecha) implements Evento {

    public static ClienteEvento de(String tipo, Cliente cliente) {
        return new ClienteEvento(UUID.randomUUID().toString(), tipo, cliente.id(), cliente.nombre(), cliente.estado(),
                CorrelationId.actual(), OffsetDateTime.now());
    }
}
