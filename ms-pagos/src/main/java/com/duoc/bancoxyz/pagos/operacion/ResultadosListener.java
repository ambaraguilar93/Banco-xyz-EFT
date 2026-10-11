package com.duoc.bancoxyz.pagos.operacion;

import java.math.BigDecimal;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duoc.bancoxyz.pagos.mensajeria.EventoInvalidoException;
import com.duoc.bancoxyz.pagos.mensajeria.EventosRecibidos;
import com.duoc.bancoxyz.pagos.mensajeria.LectorEventos;
import com.duoc.bancoxyz.pagos.mensajeria.Topicos;
import com.duoc.bancoxyz.pagos.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.pagos.mensajeria.eventos.OperacionProcesada;
import com.duoc.bancoxyz.pagos.mensajeria.eventos.TransaccionCompletada;
import com.duoc.bancoxyz.pagos.outbox.Outbox;

/**
 * Cierre de la Saga: recibe el resultado de ms-cuentas, deja la operacion
 * COMPLETADA o RECHAZADA y publica (via outbox) TransaccionCompletada y, si
 * corresponde, alertas de seguridad para ms-clientes.
 *
 */
@Component
public class ResultadosListener {

    private static final Logger log = LoggerFactory.getLogger(ResultadosListener.class);

    private final LectorEventos lector;
    private final EventosRecibidos recibidos;
    private final OperacionRepository operaciones;
    private final Outbox outbox;
    private final BigDecimal umbralAltoMonto;
    private final int maxRechazos;

    public ResultadosListener(LectorEventos lector, EventosRecibidos recibidos, OperacionRepository operaciones,
            Outbox outbox, @Value("${pagos.alertas.umbral-alto-monto:1000000}") BigDecimal umbralAltoMonto,
            @Value("${pagos.alertas.rechazos-repetidos:3}") int maxRechazos) {
        this.lector = lector;
        this.recibidos = recibidos;
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.umbralAltoMonto = umbralAltoMonto;
        this.maxRechazos = maxRechazos;
    }

    @KafkaListener(id = "pagos-operacionesResultados", topics = Topicos.OPERACIONES_RESULTADOS, groupId = "ms-pagos", concurrency = "${pagos.kafka.concurrencia:3}")
    @Transactional
    public void recibir(ConsumerRecord<String, String> registro) {
        OperacionProcesada resultado = lector.leer(registro.value(), OperacionProcesada.class);
        if (resultado.eventId() == null || resultado.operacionId() == null || resultado.resultado() == null) {
            throw new EventoInvalidoException("OperacionProcesada incompleta: " + registro.value());
        }
        if (!recibidos.registrarSiEsNuevo(resultado.eventId(), resultado.tipo())) {
            log.info("[IDEMPOTENCIA] Resultado {} ya recibido, se descarta", resultado.eventId());
            return;
        }

        String estado = resultado.aplicada() ? Operacion.COMPLETADA : Operacion.RECHAZADA;
        Optional<Operacion> cerrada = operaciones.cerrar(resultado.operacionId(), estado, resultado.motivo(),
                resultado.clienteOrigenId(), resultado.clienteDestinoId());
        if (cerrada.isEmpty()) {
            log.info("[SAGA] Operacion {} ya estaba cerrada (o no existe): resultado duplicado ignorado",
                    resultado.operacionId());
            return;
        }

        Operacion op = cerrada.get();
        log.info("[SAGA] Operacion {} {} -> {}{} (procesada por {})", op.tipo(), op.id(), estado,
                op.motivo() == null ? "" : ": " + op.motivo(), resultado.instancia());
        Long clienteClave = op.clienteOrigenId() != null ? op.clienteOrigenId() : op.clienteDestinoId();
        outbox.registrar(Topicos.TRANSACCIONES_COMPLETADAS, String.valueOf(clienteClave),
                TransaccionCompletada.de(op, resultado));
        evaluarAlertas(op, clienteClave);
    }

    private void evaluarAlertas(Operacion op, Long clienteClave) {
        if (Operacion.COMPLETADA.equals(op.estado()) && op.monto().compareTo(umbralAltoMonto) >= 0) {
            outbox.registrar(Topicos.ALERTAS_SEGURIDAD, String.valueOf(clienteClave),
                    AlertaSeguridad.de("MEDIA", "OPERACION_ALTO_MONTO",
                            op.tipo() + " por $" + op.monto().toPlainString() + " (umbral $"
                                    + umbralAltoMonto.toPlainString() + ")",
                            clienteClave, op.cuentaOrigenId() != null ? op.cuentaOrigenId() : op.cuentaDestinoId(),
                            op.id()));
        }
        if (Operacion.RECHAZADA.equals(op.estado()) && op.cuentaOrigenId() != null && op.clienteOrigenId() != null) {
            int rechazos = operaciones.rechazosRecientes(op.cuentaOrigenId(), 10);
            if (rechazos == maxRechazos) {
                outbox.registrar(Topicos.ALERTAS_SEGURIDAD, String.valueOf(op.clienteOrigenId()),
                        AlertaSeguridad.de("ALTA", "RECHAZOS_REPETIDOS",
                                rechazos + " operaciones rechazadas desde la cuenta " + op.cuentaOrigenId()
                                        + " en los ultimos 10 minutos",
                                op.clienteOrigenId(), op.cuentaOrigenId(), op.id()));
                log.warn("[ALERTA] {} rechazos en 10 min desde la cuenta {}", rechazos, op.cuentaOrigenId());
            }
        }
    }
}
