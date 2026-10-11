package com.duoc.bancoxyz.pagos.mensajeria;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor idempotente: Kafka entrega al menos una vez, asi que un mismo
 * evento puede llegar dos veces. Antes de procesarlo se registra su eventId en
 * eventos_recibidos_pagos dentro de la misma transaccion: si ya estaba, se
 * descarta.
 */
@Component
public class EventosRecibidos {

    private final JdbcClient jdbcClient;

    public EventosRecibidos(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * @return true si el evento es nuevo (hay que procesarlo), false si es un
     *         duplicado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registrarSiEsNuevo(String eventId, String tipo) {
        return jdbcClient.sql("""
                INSERT INTO eventos_recibidos_pagos (event_id, tipo) VALUES (:eventId, :tipo)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("eventId", UUID.fromString(eventId))
                .param("tipo", tipo)
                .update() == 1;
    }
}
