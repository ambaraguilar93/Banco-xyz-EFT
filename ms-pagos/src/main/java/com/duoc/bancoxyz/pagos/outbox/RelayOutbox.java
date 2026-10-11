package com.duoc.bancoxyz.pagos.outbox;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.duoc.bancoxyz.pagos.mensajeria.PublicadorEventos;

/**
 * Publica en Kafka los eventos pendientes de la tabla outbox_pagos.
 *
 */
@Component
public class RelayOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelayOutbox.class);

    private record Pendiente(UUID eventId, String topico, String clave, String tipo, String correlationId,
            String payload) {
    }

    private final JdbcClient jdbcClient;
    private final PublicadorEventos publicador;

    private boolean avisoKafkaCaido;

    public RelayOutbox(JdbcClient jdbcClient, PublicadorEventos publicador) {
        this.jdbcClient = jdbcClient;
        this.publicador = publicador;
    }

    @Scheduled(fixedDelayString = "${banco.outbox.intervalo-ms:1000}", initialDelayString = "${banco.outbox.retraso-inicial-ms:10000}")
    @Transactional
    public void publicarPendientes() {
        List<Pendiente> pendientes = jdbcClient.sql("""
                SELECT event_id, topico, clave, tipo, correlation_id, payload FROM outbox_pagos
                WHERE publicado_en IS NULL ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED
                """)
                .query(Pendiente.class)
                .list();
        for (Pendiente evento : pendientes) {
            try {
                publicador.publicarCrudo(evento.topico(), evento.clave(), evento.tipo(), evento.correlationId(),
                        evento.payload());
                jdbcClient.sql("UPDATE outbox_pagos SET publicado_en = now() WHERE event_id = :id")
                        .param("id", evento.eventId())
                        .update();
                if (avisoKafkaCaido) {
                    log.info("[OUTBOX] Kafka disponible otra vez: se reanuda la publicacion de eventos pendientes");
                    avisoKafkaCaido = false;
                }
            } catch (CallNotPermittedException e) {
                if (!avisoKafkaCaido) {
                    log.warn("[OUTBOX] Circuit Breaker 'kafka' ABIERTO: los eventos quedan guardados en outbox_pagos");
                    avisoKafkaCaido = true;
                }
                return;
            } catch (RuntimeException e) {
                jdbcClient.sql(
                        "UPDATE outbox_pagos SET intentos = intentos + 1, ultimo_error = :error WHERE event_id = :id")
                        .param("error", recortar(e.getMessage()))
                        .param("id", evento.eventId())
                        .update();
                log.warn("[OUTBOX] No se pudo publicar {} ({}), queda pendiente: {}", evento.tipo(), evento.eventId(),
                        e.getMessage());
                return;
            }
        }
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.substring(0, Math.min(texto.length(), 500));
    }
}
