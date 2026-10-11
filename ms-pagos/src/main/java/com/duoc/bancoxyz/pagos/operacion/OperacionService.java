package com.duoc.bancoxyz.pagos.operacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.duoc.bancoxyz.pagos.comun.CorrelationId;
import com.duoc.bancoxyz.pagos.comun.RecursoNoEncontradoException;
import com.duoc.bancoxyz.pagos.comun.ReglaNegocioException;
import com.duoc.bancoxyz.pagos.cuentas.CuentaResumen;
import com.duoc.bancoxyz.pagos.cuentas.CuentasClient;
import com.duoc.bancoxyz.pagos.mensajeria.Topicos;
import com.duoc.bancoxyz.pagos.mensajeria.eventos.OperacionSolicitada;
import com.duoc.bancoxyz.pagos.outbox.Outbox;

/**
 * Registro de operaciones (inicio de la Saga por coreografia)
 * 
 */
@Service
public class OperacionService {

    private static final Logger log = LoggerFactory.getLogger(OperacionService.class);

    public record Registro(Operacion operacion, boolean nueva) {
    }

    private final OperacionRepository operaciones;
    private final CuentasClient cuentasClient;
    private final Outbox outbox;
    private final TransactionTemplate transaccion;

    public OperacionService(OperacionRepository operaciones, CuentasClient cuentasClient, Outbox outbox,
            TransactionTemplate transaccion) {
        this.operaciones = operaciones;
        this.cuentasClient = cuentasClient;
        this.outbox = outbox;
        this.transaccion = transaccion;
    }

    public Registro registrar(String tipo, Long origenId, Long destinoId, BigDecimal monto, String descripcion,
            String idempotencyKey) {
        Optional<Operacion> previa = operaciones.buscarPorIdempotencyKey(idempotencyKey);
        if (previa.isPresent()) {
            log.info("[IDEMPOTENCIA] Idempotency-Key {} ya usada: se devuelve la operacion {}", idempotencyKey,
                    previa.get().id());
            return new Registro(previa.get(), false);
        }

        Optional<CuentaResumen> origen = origenId == null ? Optional.empty() : validar(origenId, "origen", true);
        Optional<CuentaResumen> destino = destinoId == null ? Optional.empty() : validar(destinoId, "destino", false);
        boolean validacionCompleta = (origenId == null || origen.isPresent())
                && (destinoId == null || destino.isPresent());

        Operacion operacion = new Operacion(UUID.randomUUID().toString(), tipo, origenId, destinoId, monto,
                descripcion, Operacion.PENDIENTE, null, validacionCompleta ? "SINCRONA" : "DIFERIDA",
                origen.map(CuentaResumen::clienteId).orElse(null), destino.map(CuentaResumen::clienteId).orElse(null),
                idempotencyKey, 0, CorrelationId.actual(), OffsetDateTime.now(), OffsetDateTime.now());

        transaccion.executeWithoutResult(estado -> {
            operaciones.insertar(operacion);
            outbox.registrar(Topicos.OPERACIONES_SOLICITADAS, operacion.claveKafka(),
                    OperacionSolicitada.de(operacion));
        });
        log.info("[OPERACION] {} {} registrada PENDIENTE por ${} (validacion {})", tipo, operacion.id(), monto,
                operacion.validacion());
        return new Registro(operaciones.buscar(operacion.id()).orElse(operacion), true);
    }

    public Operacion obtener(String id) {
        return operaciones.buscar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La operacion " + id + " no existe"));
    }

    public List<Operacion> listarPorCuenta(Long cuentaId, int limite) {
        return operaciones.listarPorCuenta(cuentaId, limite);
    }

    /**
     * Validacion temprana: si ms-cuentas responde, los errores evidentes se
     * informan de
     * inmediato (400/404/409) en vez de esperar el resultado asincrono.
     */
    private Optional<CuentaResumen> validar(Long cuentaId, String rol, boolean esOrigen) {
        Optional<CuentaResumen> cuenta;
        try {
            cuenta = cuentasClient.consultar(cuentaId);
        } catch (RecursoNoEncontradoException e) {
            throw new RecursoNoEncontradoException("La cuenta de " + rol + " " + cuentaId + " no existe");
        }
        cuenta.ifPresent(c -> {
            if (!c.activa()) {
                throw new ReglaNegocioException("La cuenta de " + rol + " " + cuentaId + " esta " + c.estado());
            }
            if (esOrigen && c.esPrestamo()) {
                throw new ReglaNegocioException(
                        "No se puede pagar ni transferir desde la cuenta de prestamo " + cuentaId);
            }
        });
        return cuenta;
    }
}
