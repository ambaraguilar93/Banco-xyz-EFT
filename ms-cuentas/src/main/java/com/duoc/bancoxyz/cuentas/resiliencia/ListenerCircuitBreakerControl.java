package com.duoc.bancoxyz.cuentas.resiliencia;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Vincula los consumidores Kafka al Circuit Breaker de la base de datos : si
 * "cuentasDb" se abre, se pausa el consumo, los mensajes
 * esperan intactos en Kafka en vez de fallar uno tras otro.
 */
@Component
public class ListenerCircuitBreakerControl {

    private static final Logger log = LoggerFactory.getLogger(ListenerCircuitBreakerControl.class);
    private static final String CIRCUITO = "cuentasDb";

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final KafkaListenerEndpointRegistry listenerRegistry;

    public ListenerCircuitBreakerControl(CircuitBreakerRegistry circuitBreakerRegistry,
            KafkaListenerEndpointRegistry listenerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.listenerRegistry = listenerRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrar() {
        CircuitBreaker circuito = circuitBreakerRegistry.circuitBreaker(CIRCUITO);
        circuito.getEventPublisher().onStateTransition(evento -> {
            CircuitBreaker.State nuevo = evento.getStateTransition().getToState();
            switch (nuevo) {
                case OPEN, FORCED_OPEN -> cambiar(false, nuevo);
                case HALF_OPEN, CLOSED -> cambiar(true, nuevo);
                default -> {
                }
            }
        });
        log.info("[RESILIENCIA] Consumidores Kafka vinculados al Circuit Breaker '{}'", CIRCUITO);
    }

    private void cambiar(boolean reanudar, CircuitBreaker.State estado) {
        CompletableFuture.runAsync(() -> {
            for (MessageListenerContainer contenedor : listenerRegistry.getListenerContainers()) {
                if (reanudar) {
                    contenedor.resume();
                } else {
                    contenedor.pause();
                }
            }
            if (reanudar) {
                log.info("[RESILIENCIA] Circuito {} en {} -> consumo Kafka REANUDADO", CIRCUITO, estado);
            } else {
                log.warn("[RESILIENCIA] Circuito {} en {} -> consumo Kafka PAUSADO (los mensajes esperan en Kafka)",
                        CIRCUITO, estado);
            }
        });
    }
}
