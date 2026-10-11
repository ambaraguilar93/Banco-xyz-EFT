package com.duoc.bancoxyz.cuentas.operacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import tools.jackson.databind.json.JsonMapper;

import com.duoc.bancoxyz.cuentas.comun.CorrelationId;
import com.duoc.bancoxyz.cuentas.cuenta.Cuenta;
import com.duoc.bancoxyz.cuentas.cuenta.CuentaRepository;
import com.duoc.bancoxyz.cuentas.cuenta.MovimientoRepository;
import com.duoc.bancoxyz.cuentas.mensajeria.EventosRecibidos;
import com.duoc.bancoxyz.cuentas.mensajeria.Topicos;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.OperacionProcesada;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.OperacionSolicitada;
import com.duoc.bancoxyz.cuentas.outbox.Outbox;

/**
 * Aplica las operaciones solicitadas por ms-pagos.
 *
 */
@Service
public class ProcesadorOperaciones {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorOperaciones.class);

    private static final Set<String> TIPOS = Set.of("PAGO", "RETIRO", "TRANSFERENCIA", "DEPOSITO");

    /**
     * Rechazo de negocio: la operacion no se aplica, pero se responde a ms-pagos.
     */
    private static final class Rechazo extends RuntimeException {

        private final Cuenta cuentaAfectada;

        Rechazo(String motivo, Cuenta cuentaAfectada) {
            super(motivo);
            this.cuentaAfectada = cuentaAfectada;
        }
    }

    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final OperacionProcesadaRepository procesadas;
    private final EventosRecibidos recibidos;
    private final Outbox outbox;
    private final JsonMapper jsonMapper;
    private final String instancia;

    public ProcesadorOperaciones(CuentaRepository cuentas, MovimientoRepository movimientos,
            OperacionProcesadaRepository procesadas, EventosRecibidos recibidos, Outbox outbox, JsonMapper jsonMapper,
            @Value("${spring.application.name}:${HOSTNAME:local}:${server.port}") String instancia) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.procesadas = procesadas;
        this.recibidos = recibidos;
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.instancia = instancia;
    }

    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public void procesar(OperacionSolicitada solicitud) {
        if (!recibidos.registrarSiEsNuevo(solicitud.eventId(), solicitud.tipo())) {
            log.info("[IDEMPOTENCIA] Evento {} de la operacion {} ya recibido, se descarta", solicitud.eventId(),
                    solicitud.operacionId());
            return;
        }

        Optional<String> previo = procesadas.payloadDe(solicitud.operacionId());
        if (previo.isPresent()) {
            // ms-pagos volvio a pedir una operacion ya procesada (no recibio la respuesta):
            // se reenvia el MISMO resultado, sin aplicar nada de nuevo.
            OperacionProcesada anterior = jsonMapper.readValue(previo.get(), OperacionProcesada.class);
            outbox.registrar(Topicos.OPERACIONES_RESULTADOS, solicitud.operacionId(), reenvio(anterior, solicitud));
            log.info("[IDEMPOTENCIA] Operacion {} ya estaba {}: se reenvia el resultado", solicitud.operacionId(),
                    anterior.resultado());
            return;
        }

        OperacionProcesada resultado;
        try {
            resultado = aplicar(solicitud);
            log.info("[OPERACION] {} {} APLICADA por ${} (origen {} -> saldo {}, destino {} -> saldo {})",
                    solicitud.tipoOperacion(), solicitud.operacionId(), solicitud.monto(), solicitud.cuentaOrigenId(),
                    resultado.saldoOrigen(), solicitud.cuentaDestinoId(), resultado.saldoDestino());
        } catch (Rechazo rechazo) {
            resultado = respuesta(solicitud, OperacionProcesada.RECHAZADA, rechazo.getMessage(), null, null, null,
                    null);
            log.info("[OPERACION] {} {} RECHAZADA: {}", solicitud.tipoOperacion(), solicitud.operacionId(),
                    rechazo.getMessage());
            Cuenta afectada = rechazo.cuentaAfectada;
            if (afectada != null && !afectada.activa()) {
                outbox.registrar(Topicos.ALERTAS_SEGURIDAD, afectada.clienteId().toString(),
                        AlertaSeguridad.de("ALTA", "OPERACION_SOBRE_CUENTA_" + afectada.estado(),
                                "Se intento operar sobre la cuenta " + afectada.id() + " que esta " + afectada.estado(),
                                afectada.clienteId(), afectada.id(), solicitud.operacionId()));
            }
        }
        procesadas.insertar(resultado, jsonMapper.writeValueAsString(resultado));
        outbox.registrar(Topicos.OPERACIONES_RESULTADOS, solicitud.operacionId(), resultado);
    }

    private OperacionProcesada aplicar(OperacionSolicitada s) {
        validarSolicitud(s);
        String descripcion = s.descripcion() == null ? s.tipoOperacion() : s.descripcion();
        switch (s.tipoOperacion()) {
            case "DEPOSITO" -> {
                Cuenta destino = bloquear(s.cuentaDestinoId(), "destino");
                exigirActiva(destino, "destino");
                BigDecimal saldo = cuentas.acreditar(destino.id(), s.monto());
                movimientos.insertar(s.operacionId(), destino.id(), "CREDITO", s.tipoOperacion(), s.monto(), saldo,
                        descripcion);
                return respuesta(s, OperacionProcesada.APLICADA, null, null, destino.clienteId(), null, saldo);
            }
            // RETIRO (cajero automatico): mismo debito que un pago, queda con su propio
            // tipo en la cartola
            case "PAGO", "RETIRO" -> {
                Cuenta origen = bloquear(s.cuentaOrigenId(), "origen");
                exigirOrigenValido(origen);
                BigDecimal saldo = debitar(origen, s.monto());
                movimientos.insertar(s.operacionId(), origen.id(), "DEBITO", s.tipoOperacion(), s.monto(), saldo,
                        descripcion);
                return respuesta(s, OperacionProcesada.APLICADA, null, origen.clienteId(), null, saldo, null);
            }
            default -> {
                // TRANSFERENCIA: se bloquean ambas cuentas en orden de id (evita deadlocks)
                boolean origenPrimero = s.cuentaOrigenId() < s.cuentaDestinoId();
                Cuenta primera = bloquear(origenPrimero ? s.cuentaOrigenId() : s.cuentaDestinoId(),
                        origenPrimero ? "origen" : "destino");
                Cuenta segunda = bloquear(origenPrimero ? s.cuentaDestinoId() : s.cuentaOrigenId(),
                        origenPrimero ? "destino" : "origen");
                Cuenta origen = origenPrimero ? primera : segunda;
                Cuenta destino = origenPrimero ? segunda : primera;
                exigirOrigenValido(origen);
                exigirActiva(destino, "destino");
                BigDecimal saldoOrigen = debitar(origen, s.monto());
                BigDecimal saldoDestino = cuentas.acreditar(destino.id(), s.monto());
                movimientos.insertar(s.operacionId(), origen.id(), "DEBITO", s.tipoOperacion(), s.monto(), saldoOrigen,
                        descripcion);
                movimientos.insertar(s.operacionId(), destino.id(), "CREDITO", s.tipoOperacion(), s.monto(),
                        saldoDestino, descripcion);
                return respuesta(s, OperacionProcesada.APLICADA, null, origen.clienteId(), destino.clienteId(),
                        saldoOrigen, saldoDestino);
            }
        }
    }

    /**
     * Datos incompletos o incoherentes: se rechaza (se responde a ms-pagos en vez
     * de ir al DLT).
     */
    private static void validarSolicitud(OperacionSolicitada s) {
        if (s.tipoOperacion() == null || !TIPOS.contains(s.tipoOperacion())) {
            throw new Rechazo("Tipo de operacion invalido: " + s.tipoOperacion(), null);
        }
        if (s.monto() == null || s.monto().signum() <= 0) {
            throw new Rechazo("El monto debe ser mayor que 0", null);
        }
        boolean necesitaOrigen = !"DEPOSITO".equals(s.tipoOperacion());
        boolean necesitaDestino = !"PAGO".equals(s.tipoOperacion()) && !"RETIRO".equals(s.tipoOperacion());
        if ((necesitaOrigen && s.cuentaOrigenId() == null) || (necesitaDestino && s.cuentaDestinoId() == null)) {
            throw new Rechazo("Faltan cuentas para una operacion " + s.tipoOperacion(), null);
        }
        if ("TRANSFERENCIA".equals(s.tipoOperacion()) && s.cuentaOrigenId().equals(s.cuentaDestinoId())) {
            throw new Rechazo("La cuenta de origen y destino no pueden ser la misma", null);
        }
    }

    private Cuenta bloquear(Long id, String rol) {
        return cuentas.buscarParaActualizar(id)
                .orElseThrow(() -> new Rechazo("La cuenta de " + rol + " " + id + " no existe", null));
    }

    private static void exigirActiva(Cuenta cuenta, String rol) {
        if (!cuenta.activa()) {
            throw new Rechazo("La cuenta de " + rol + " " + cuenta.id() + " esta " + cuenta.estado(), cuenta);
        }
    }

    private static void exigirOrigenValido(Cuenta origen) {
        exigirActiva(origen, "origen");
        if (Cuenta.PRESTAMO.equals(origen.tipo())) {
            throw new Rechazo("No se puede debitar desde la cuenta de prestamo " + origen.id(), null);
        }
    }

    private BigDecimal debitar(Cuenta origen, BigDecimal monto) {
        return cuentas.debitarSiAlcanza(origen.id(), monto)
                .orElseThrow(() -> new Rechazo("Saldo insuficiente en la cuenta " + origen.id() + ": disponible $"
                        + origen.saldo().toPlainString() + ", solicitado $" + monto.toPlainString(), null));
    }

    private OperacionProcesada respuesta(OperacionSolicitada s, String resultado, String motivo, Long clienteOrigen,
            Long clienteDestino, BigDecimal saldoOrigen, BigDecimal saldoDestino) {
        return new OperacionProcesada(eventIdResultado(s), OperacionProcesada.TIPO, s.operacionId(),
                s.tipoOperacion(), resultado, motivo, s.cuentaOrigenId(), s.cuentaDestinoId(), clienteOrigen,
                clienteDestino, s.monto(), saldoOrigen, saldoDestino, instancia, CorrelationId.actual(),
                OffsetDateTime.now());
    }

    private OperacionProcesada reenvio(OperacionProcesada anterior, OperacionSolicitada s) {
        return new OperacionProcesada(eventIdResultado(s), anterior.tipo(), anterior.operacionId(),
                anterior.tipoOperacion(), anterior.resultado(), anterior.motivo(), anterior.cuentaOrigenId(),
                anterior.cuentaDestinoId(), anterior.clienteOrigenId(), anterior.clienteDestinoId(), anterior.monto(),
                anterior.saldoOrigen(), anterior.saldoDestino(), anterior.instancia(), CorrelationId.actual(),
                OffsetDateTime.now());
    }

    /**
     * Determinista por solicitud: si el mismo resultado se publica dos veces,
     * ms-pagos lo detecta.
     */
    private static String eventIdResultado(OperacionSolicitada s) {
        return UUID.nameUUIDFromBytes(("resultado:" + s.eventId()).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
