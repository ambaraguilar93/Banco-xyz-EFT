package com.duoc.bancoxyz.cuentas.cliente;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duoc.bancoxyz.cuentas.cuenta.Cuenta;
import com.duoc.bancoxyz.cuentas.cuenta.CuentaRepository;
import com.duoc.bancoxyz.cuentas.mensajeria.EventosRecibidos;
import com.duoc.bancoxyz.cuentas.mensajeria.LectorEventos;
import com.duoc.bancoxyz.cuentas.mensajeria.Topicos;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.ClienteEvento;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.CuentaEvento;
import com.duoc.bancoxyz.cuentas.outbox.Outbox;

/**
 * Mantiene la replica de clientes con los eventos de ms-clientes. Si un cliente
 * se desactiva, sus cuentas activas se bloquean.
 */
@Component
public class ClientesListener {

    private static final Logger log = LoggerFactory.getLogger(ClientesListener.class);

    private final LectorEventos lector;
    private final EventosRecibidos recibidos;
    private final ClienteReplicaRepository replica;
    private final CuentaRepository cuentas;
    private final Outbox outbox;

    public ClientesListener(LectorEventos lector, EventosRecibidos recibidos, ClienteReplicaRepository replica,
            CuentaRepository cuentas, Outbox outbox) {
        this.lector = lector;
        this.recibidos = recibidos;
        this.replica = replica;
        this.cuentas = cuentas;
        this.outbox = outbox;
    }

    @KafkaListener(id = "cuentas-clientesEventos", topics = Topicos.CLIENTES_EVENTOS, groupId = "ms-cuentas")
    @Transactional
    public void recibir(ConsumerRecord<String, String> registro) {
        ClienteEvento evento = lector.leer(registro.value(), ClienteEvento.class);
        if (evento.clienteId() == null || evento.estado() == null) {
            throw new com.duoc.bancoxyz.cuentas.mensajeria.EventoInvalidoException(
                    "ClienteEvento sin clienteId o estado: " + registro.value());
        }
        if (!recibidos.registrarSiEsNuevo(evento.eventId(), evento.tipo())) {
            log.info("[KAFKA] {} {} duplicado, se descarta", evento.tipo(), evento.eventId());
            return;
        }
        replica.guardar(evento.clienteId(), evento.nombre(), evento.estado());
        log.info("[REPLICA] Cliente {} actualizado por {} (estado {})", evento.clienteId(), evento.tipo(),
                evento.estado());

        if ("ClienteDesactivado".equals(evento.tipo())) {
            String motivo = "Cliente " + evento.clienteId() + " desactivado en ms-clientes";
            List<Cuenta> bloqueadas = cuentas.bloquearActivasDeCliente(evento.clienteId(), motivo);
            for (Cuenta cuenta : bloqueadas) {
                outbox.registrar(Topicos.CUENTAS_EVENTOS, cuenta.id().toString(),
                        CuentaEvento.de("CuentaBloqueada", cuenta, motivo));
            }
            if (!bloqueadas.isEmpty()) {
                outbox.registrar(Topicos.ALERTAS_SEGURIDAD, evento.clienteId().toString(),
                        AlertaSeguridad.de("ALTA", "CUENTAS_BLOQUEADAS_POR_DESACTIVACION",
                                bloqueadas.size() + " cuenta(s) bloqueada(s) por desactivacion del cliente",
                                evento.clienteId(), null, null));
                log.info("[REPLICA] {} cuenta(s) del cliente {} BLOQUEADAS", bloqueadas.size(), evento.clienteId());
            }
        }
    }
}
