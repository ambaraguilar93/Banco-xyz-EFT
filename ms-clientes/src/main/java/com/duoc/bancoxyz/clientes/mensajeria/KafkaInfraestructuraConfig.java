package com.duoc.bancoxyz.clientes.mensajeria;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.duoc.bancoxyz.clientes.comun.CorrelationId;

/**
 * Tolerancia a fallos del lado consumidor.
 */
@Configuration
public class KafkaInfraestructuraConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaInfraestructuraConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer haciaDlt = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler manejador = new DefaultErrorHandler((registro, error) -> {
            log.error("[KAFKA] Mensaje de {}-{} offset {} enviado al DLT: {}", registro.topic(), registro.partition(),
                    registro.offset(), error.getMessage());
            haciaDlt.accept(registro, error);
        }, new FixedBackOff(2000L, 3L));
        manejador.addNotRetryableExceptions(EventoInvalidoException.class);
        manejador.setBackOffFunction((registro, error) -> esFallaDeInfraestructura(error) ? esperaSinLimite() : null);
        return manejador;
    }

    private static boolean esFallaDeInfraestructura(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof DataAccessException || causa instanceof CallNotPermittedException) {
                return true;
            }
        }
        return false;
    }

    private static BackOff esperaSinLimite() {
        ExponentialBackOff espera = new ExponentialBackOff(1000L, 2.0);
        espera.setMaxInterval(30000L);
        return espera;
    }

    @Bean
    public RecordInterceptor<Object, Object> correlationIdInterceptor() {
        return new RecordInterceptor<>() {

            @Override
            public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> registro,
                    Consumer<Object, Object> consumidor) {
                Header header = registro.headers().lastHeader(CorrelationId.HEADER_KAFKA);
                CorrelationId.establecer(header == null ? null : new String(header.value(), StandardCharsets.UTF_8));
                return registro;
            }

            @Override
            public void afterRecord(ConsumerRecord<Object, Object> registro, Consumer<Object, Object> consumidor) {
                CorrelationId.limpiar();
            }
        };
    }
}
