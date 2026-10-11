package com.duoc.bancoxyz.cuentas.operacion;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.cuentas.mensajeria.EventoInvalidoException;
import com.duoc.bancoxyz.cuentas.mensajeria.LectorEventos;
import com.duoc.bancoxyz.cuentas.mensajeria.Topicos;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.OperacionSolicitada;

/**
 * Consumidor de banco.operaciones.solicitadas (grupo ms-cuentas). Con varias
 * instancias de ms-cuentas, Kafka reparte las 3 particiones entre ellas,
 * los mensajes de una misma cuenta van siempre a la misma particion y se
 * procesan en orden.
 */
@Component
public class OperacionesListener {

    private static final Logger log = LoggerFactory.getLogger(OperacionesListener.class);

    private final LectorEventos lector;
    private final ProcesadorOperaciones procesador;

    public OperacionesListener(LectorEventos lector, ProcesadorOperaciones procesador) {
        this.lector = lector;
        this.procesador = procesador;
    }

    @KafkaListener(id = "cuentas-operacionesSolicitadas", topics = Topicos.OPERACIONES_SOLICITADAS, groupId = "ms-cuentas", concurrency = "${cuentas.kafka.concurrencia:3}")
    public void recibir(ConsumerRecord<String, String> registro) {
        OperacionSolicitada solicitud = lector.leer(registro.value(), OperacionSolicitada.class);
        if (solicitud.eventId() == null || solicitud.operacionId() == null) {
            throw new EventoInvalidoException("OperacionSolicitada sin eventId u operacionId: " + registro.value());
        }
        log.info("[KAFKA] OperacionSolicitada {} {} recibida (particion {}, offset {})", solicitud.tipoOperacion(),
                solicitud.operacionId(), registro.partition(), registro.offset());
        procesador.procesar(solicitud);
    }
}
