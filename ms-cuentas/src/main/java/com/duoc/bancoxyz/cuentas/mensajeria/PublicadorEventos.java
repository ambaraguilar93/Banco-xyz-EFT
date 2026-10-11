package com.duoc.bancoxyz.cuentas.mensajeria;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.cuentas.comun.CorrelationId;

/**
 * Publica eventos en Kafka como JSON, con los headers "tipo" y "correlationId".
 *
 * El envio es aincrono, si Kafka no responde
 * se lanza una excepcion y el evento queda pendiente en el outbox. Asi nunca se
 * da por enviado un evento que no llego.
 *
 * Circuit Breaker "kafka": si muchos envios fallan seguidos, el circuito se
 * abre y los siguientes intentos fallan al instante en vez de
 * esperar el timeout del broker en cada evento.
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PublicadorEventos(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica un evento ya serializado (lo usa el relay del outbox).
     *
     * @param clave clave del mensaje: Kafka garantiza el orden de los mensajes con
     *              la misma
     *              clave (van a la misma particion). Se usa el id de la cuenta,
     *              cliente u operacion.
     */
    @CircuitBreaker(name = "kafka")
    public void publicarCrudo(String topico, String clave, String tipo, String correlationId, String payload) {
        ProducerRecord<String, String> registro = new ProducerRecord<>(topico, clave, payload);
        registro.headers().add("tipo", tipo.getBytes(StandardCharsets.UTF_8));
        registro.headers().add(CorrelationId.HEADER_KAFKA, correlationId.getBytes(StandardCharsets.UTF_8));
        try {
            SendResult<String, String> resultado = kafkaTemplate.send(registro).get(10, TimeUnit.SECONDS);
            log.info("[KAFKA] {} publicado en {}-{} offset {} (clave {})", tipo, topico,
                    resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), clave);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacion interrumpida de " + tipo, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka no confirmo el evento " + tipo + " en " + topico + ": "
                    + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        }
    }
}
