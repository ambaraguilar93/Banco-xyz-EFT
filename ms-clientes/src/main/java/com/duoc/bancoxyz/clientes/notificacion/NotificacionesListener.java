package com.duoc.bancoxyz.clientes.notificacion;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duoc.bancoxyz.clientes.mensajeria.EventoInvalidoException;
import com.duoc.bancoxyz.clientes.mensajeria.EventosRecibidos;
import com.duoc.bancoxyz.clientes.mensajeria.LectorEventos;
import com.duoc.bancoxyz.clientes.mensajeria.Topicos;
import com.duoc.bancoxyz.clientes.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.clientes.mensajeria.eventos.CuentaEvento;
import com.duoc.bancoxyz.clientes.mensajeria.eventos.TransaccionCompletada;

/**
 * Consumidores de eventos de otros microservicios (grupo ms-clientes). Cada evento queda en
 * el historial del cliente (notificaciones_cliente) y se "envia" la notificacion (log que
 * simula correo/push). Las alertas de seguridad ALTA se destacan en el log.
 */
@Component
public class NotificacionesListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesListener.class);

    private final LectorEventos lector;
    private final EventosRecibidos recibidos;
    private final NotificacionRepository notificaciones;

    public NotificacionesListener(LectorEventos lector, EventosRecibidos recibidos,
            NotificacionRepository notificaciones) {
        this.lector = lector;
        this.recibidos = recibidos;
        this.notificaciones = notificaciones;
    }

    @KafkaListener(id = "clientes-transacciones", topics = Topicos.TRANSACCIONES_COMPLETADAS, groupId = "ms-clientes",
            concurrency = "${clientes.kafka.concurrencia:3}")
    @Transactional
    public void transaccion(ConsumerRecord<String, String> registro) {
        TransaccionCompletada t = lector.leer(registro.value(), TransaccionCompletada.class);
        if (t.eventId() == null || t.operacionId() == null) {
            throw new EventoInvalidoException("TransaccionCompletada incompleta: " + registro.value());
        }
        if (!recibidos.registrarSiEsNuevo(t.eventId(), t.tipo())) {
            return;
        }
        String titulo = nombre(t.tipoOperacion()) + " " + t.estado() + " por " + pesos(t.monto());
        String detalle = switch (t.tipoOperacion()) {
            case "TRANSFERENCIA" -> "Desde la cuenta " + t.cuentaOrigenId() + " a la cuenta " + t.cuentaDestinoId();
            case "DEPOSITO" -> "En la cuenta " + t.cuentaDestinoId();
            default -> "Desde la cuenta " + t.cuentaOrigenId() + (t.descripcion() == null ? "" : " (" + t.descripcion() + ")");
        } + (t.motivo() == null ? "" : ". Motivo: " + t.motivo());
        // Quien origina la operacion (en un deposito no hay origen: es el titular de la cuenta destino)
        Long titular = t.clienteOrigenId() != null ? t.clienteOrigenId() : t.clienteDestinoId();
        notificar(t.eventId(), titular, "TRANSACCION", "INFO", titulo, detalle, t.operacionId(), "ms-pagos");
        // En una transferencia completada, el destinatario tambien recibe aviso
        if ("COMPLETADA".equals(t.estado()) && t.clienteOrigenId() != null && t.clienteDestinoId() != null
                && !Objects.equals(t.clienteDestinoId(), t.clienteOrigenId())) {
            notificar(t.eventId(), t.clienteDestinoId(), "TRANSACCION", "INFO",
                    "Recibiste " + pesos(t.monto()) + " (" + nombre(t.tipoOperacion()).toLowerCase(Locale.ROOT) + ")",
                    detalle, t.operacionId(), "ms-pagos");
        }
    }

    @KafkaListener(id = "clientes-cuentas", topics = Topicos.CUENTAS_EVENTOS, groupId = "ms-clientes",
            concurrency = "${clientes.kafka.concurrencia:3}")
    @Transactional
    public void cuenta(ConsumerRecord<String, String> registro) {
        CuentaEvento c = lector.leer(registro.value(), CuentaEvento.class);
        if (c.eventId() == null || c.cuentaId() == null) {
            throw new EventoInvalidoException("CuentaEvento incompleto: " + registro.value());
        }
        if (!recibidos.registrarSiEsNuevo(c.eventId(), c.tipo())) {
            return;
        }
        String titulo = switch (c.tipo()) {
            case "CuentaAbierta" -> "Nueva cuenta " + c.tipoCuenta() + " N° " + c.cuentaId();
            case "CuentaBloqueada" -> "Cuenta N° " + c.cuentaId() + " bloqueada";
            case "CuentaDesbloqueada" -> "Cuenta N° " + c.cuentaId() + " desbloqueada";
            case "CuentaCerrada" -> "Cuenta N° " + c.cuentaId() + " cerrada";
            default -> c.tipo() + " cuenta N° " + c.cuentaId();
        };
        notificar(c.eventId(), c.clienteId(), "CUENTA", "INFO", titulo, c.motivo(), String.valueOf(c.cuentaId()),
                "ms-cuentas");
    }

    @KafkaListener(id = "clientes-alertas", topics = Topicos.ALERTAS_SEGURIDAD, groupId = "ms-clientes",
            concurrency = "${clientes.kafka.concurrencia:3}")
    @Transactional
    public void alerta(ConsumerRecord<String, String> registro) {
        AlertaSeguridad a = lector.leer(registro.value(), AlertaSeguridad.class);
        if (a.eventId() == null || a.codigo() == null) {
            throw new EventoInvalidoException("AlertaSeguridad incompleta: " + registro.value());
        }
        if (!recibidos.registrarSiEsNuevo(a.eventId(), a.tipo())) {
            return;
        }
        notificar(a.eventId(), a.clienteId(), "ALERTA", a.nivel(), "Alerta de seguridad " + a.codigo(),
                a.descripcion(), a.operacionId() != null ? a.operacionId() : (a.cuentaId() == null ? null : a.cuentaId().toString()), a.origen());
        if ("ALTA".equals(a.nivel())) {
            log.warn("[SEGURIDAD] ALERTA ALTA para el cliente {}: {} - {}", a.clienteId(), a.codigo(), a.descripcion());
        }
    }

    private void notificar(String eventId, Long clienteId, String categoria, String nivel, String titulo,
            String detalle, String referencia, String origen) {
        if (clienteId == null) {
            log.info("[NOTIFICACION] Evento {} sin cliente asociado: se registra solo en el log ({})", eventId, titulo);
            return;
        }
        notificaciones.insertar(eventId, clienteId, categoria, nivel, titulo, detalle, referencia, origen);
        log.info("[NOTIFICACION] Cliente {} [{}/{}] {}{}", clienteId, categoria, nivel, titulo,
                detalle == null ? "" : " - " + detalle);
    }

    private static String nombre(String tipoOperacion) {
        return switch (tipoOperacion) {
            case "TRANSFERENCIA" -> "Transferencia";
            case "DEPOSITO" -> "Deposito";
            case "RETIRO" -> "Retiro en cajero";
            default -> "Pago";
        };
    }

    private static String pesos(BigDecimal monto) {
        return monto == null ? "$?" : "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(monto);
    }
}
