package com.duoc.bancoxyz.pagos.operacion;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.duoc.bancoxyz.pagos.comun.CorrelationId;
import com.duoc.bancoxyz.pagos.mensajeria.Topicos;
import com.duoc.bancoxyz.pagos.mensajeria.eventos.OperacionSolicitada;
import com.duoc.bancoxyz.pagos.outbox.Outbox;

/**
 * Consistencia eventual: si una operacion lleva mucho tiempo PENDIENTE, se
 * vuelve a solicitar. Es seguro porque ms-cuentas es idempotente por
 * operacionId: si ya la habia aplicado, solo reenvia el mismo resultado.
 */
@Component
public class ReenvioPendientesScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReenvioPendientesScheduler.class);

    private final OperacionRepository operaciones;
    private final Outbox outbox;
    private final int segundosSinRespuesta;
    private final int maxReenvios;

    public ReenvioPendientesScheduler(OperacionRepository operaciones, Outbox outbox,
            @Value("${pagos.reenvio.segundos-sin-respuesta:60}") int segundosSinRespuesta,
            @Value("${pagos.reenvio.maximo:5}") int maxReenvios) {
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.segundosSinRespuesta = segundosSinRespuesta;
        this.maxReenvios = maxReenvios;
    }

    @Scheduled(fixedDelayString = "${pagos.reenvio.intervalo-ms:30000}", initialDelay = 30000)
    @Transactional
    public void reenviar() {
        List<Operacion> pendientes = operaciones.pendientesSinRespuesta(segundosSinRespuesta, maxReenvios);
        for (Operacion op : pendientes) {
            CorrelationId.establecer(op.correlationId());
            try {
                operaciones.marcarReenvio(op.id());
                outbox.registrar(Topicos.OPERACIONES_SOLICITADAS, op.claveKafka(), OperacionSolicitada.de(op));
                log.warn("[SAGA] Operacion {} sin respuesta hace mas de {} s: se solicita de nuevo (reenvio {}/{})",
                        op.id(), segundosSinRespuesta, op.reenvios() + 1, maxReenvios);
            } finally {
                CorrelationId.limpiar();
            }
        }
    }
}
