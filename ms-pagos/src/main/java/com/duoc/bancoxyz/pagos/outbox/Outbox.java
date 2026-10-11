package com.duoc.bancoxyz.pagos.outbox;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import com.duoc.bancoxyz.pagos.mensajeria.Evento;

/**
 * Transactional Outbox: en vez de publicar en Kafka directamente desde la
 * logica de negocio, el evento se guarda en la tabla outbox_pagos dentro de la
 * misma transaccion que los datos.
 *
 */
@Component
public class Outbox {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public Outbox(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Debe llamarse dentro de una transaccion existente (la de la operacion de
     * negocio).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String topico, String clave, Evento evento) {
        jdbcClient.sql("""
                INSERT INTO outbox_pagos (event_id, topico, clave, tipo, correlation_id, payload)
                VALUES (:eventId, :topico, :clave, :tipo, :correlationId, :payload)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("eventId", UUID.fromString(evento.eventId()))
                .param("topico", topico)
                .param("clave", clave)
                .param("tipo", evento.tipo())
                .param("correlationId", evento.correlationId())
                .param("payload", jsonMapper.writeValueAsString(evento))
                .update();
    }
}
