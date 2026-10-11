package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.cuentas.cliente.ClienteReplica;
import com.duoc.bancoxyz.cuentas.cliente.ClienteReplicaRepository;
import com.duoc.bancoxyz.cuentas.comun.RecursoNoEncontradoException;
import com.duoc.bancoxyz.cuentas.comun.ReglaNegocioException;
import com.duoc.bancoxyz.cuentas.mensajeria.Topicos;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.AlertaSeguridad;
import com.duoc.bancoxyz.cuentas.mensajeria.eventos.CuentaEvento;
import com.duoc.bancoxyz.cuentas.outbox.Outbox;

/**
 * Ciclo de vida de las cuentas: apertura, bloqueo, desbloqueo y cierre.
 * Cada cambio guarda su evento en el outbox dentro de la misma transaccion.
 * Circuit Breaker "cuentasDb": si la BD falla repetidamente, se responde 503 al instante.
 */
@Service
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    private final CuentaRepository cuentas;
    private final MovimientoRepository movimientos;
    private final ClienteReplicaRepository clientes;
    private final Outbox outbox;

    public CuentaService(CuentaRepository cuentas, MovimientoRepository movimientos,
            ClienteReplicaRepository clientes, Outbox outbox) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.clientes = clientes;
        this.outbox = outbox;
    }

    @CircuitBreaker(name = "cuentasDb")
    public Cuenta obtener(Long id) {
        return cuentas.buscar(id).orElseThrow(() -> new RecursoNoEncontradoException("La cuenta " + id + " no existe"));
    }

    @CircuitBreaker(name = "cuentasDb")
    public List<Cuenta> listarPorCliente(Long clienteId) {
        return cuentas.listarPorCliente(clienteId);
    }

    @CircuitBreaker(name = "cuentasDb")
    public List<Movimiento> movimientos(Long id, int limite) {
        obtener(id);
        return movimientos.listarPorCuenta(id, limite);
    }

    /**
     * El cliente se valida contra la replica local (alimentada por los eventos de ms-clientes),
     * no con una llamada HTTP: la apertura funciona aunque ms-clientes este caido.
     */
    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public Cuenta abrir(AperturaCuentaRequest solicitud) {
        ClienteReplica cliente = clientes.buscar(solicitud.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente " + solicitud.clienteId()
                        + " no existe (o aun no se sincroniza desde ms-clientes)"));
        if (!cliente.activo()) {
            throw new ReglaNegocioException("El cliente " + cliente.clienteId() + " esta " + cliente.estado()
                    + ": no se pueden abrir cuentas");
        }
        BigDecimal saldoInicial = solicitud.saldoInicial() == null ? BigDecimal.ZERO : solicitud.saldoInicial();
        Cuenta cuenta = cuentas.insertar(cliente.clienteId(), solicitud.tipo(), saldoInicial);
        outbox.registrar(Topicos.CUENTAS_EVENTOS, cuenta.id().toString(),
                CuentaEvento.de("CuentaAbierta", cuenta, "Apertura de cuenta " + cuenta.tipo()));
        log.info("[CUENTA] Cuenta {} {} abierta para el cliente {} con saldo {}", cuenta.id(), cuenta.tipo(),
                cuenta.clienteId(), cuenta.saldo());
        return cuenta;
    }

    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public Cuenta bloquear(Long id, String motivo) {
        Cuenta cuenta = buscarParaActualizar(id);
        if (!cuenta.activa()) {
            throw new ReglaNegocioException("Solo se puede bloquear una cuenta ACTIVA (la cuenta " + id + " esta "
                    + cuenta.estado() + ")");
        }
        Cuenta bloqueada = cuentas.cambiarEstado(id, Cuenta.BLOQUEADA, motivo);
        outbox.registrar(Topicos.CUENTAS_EVENTOS, id.toString(), CuentaEvento.de("CuentaBloqueada", bloqueada, motivo));
        outbox.registrar(Topicos.ALERTAS_SEGURIDAD, bloqueada.clienteId().toString(),
                AlertaSeguridad.de("MEDIA", "CUENTA_BLOQUEADA", "La cuenta " + id + " fue bloqueada: " + motivo,
                        bloqueada.clienteId(), id, null));
        log.info("[CUENTA] Cuenta {} BLOQUEADA: {}", id, motivo);
        return bloqueada;
    }

    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public Cuenta desbloquear(Long id) {
        Cuenta cuenta = buscarParaActualizar(id);
        if (!Cuenta.BLOQUEADA.equals(cuenta.estado())) {
            throw new ReglaNegocioException("La cuenta " + id + " no esta bloqueada (esta " + cuenta.estado() + ")");
        }
        Cuenta activa = cuentas.cambiarEstado(id, Cuenta.ACTIVA, null);
        outbox.registrar(Topicos.CUENTAS_EVENTOS, id.toString(),
                CuentaEvento.de("CuentaDesbloqueada", activa, "Cuenta reactivada"));
        log.info("[CUENTA] Cuenta {} DESBLOQUEADA", id);
        return activa;
    }

    /** Regla: una cuenta solo se cierra con saldo 0 (el saldo debe transferirse antes). */
    @Transactional
    @CircuitBreaker(name = "cuentasDb")
    public Cuenta cerrar(Long id, String motivo) {
        Cuenta cuenta = buscarParaActualizar(id);
        if (Cuenta.CERRADA.equals(cuenta.estado())) {
            throw new ReglaNegocioException("La cuenta " + id + " ya esta cerrada");
        }
        if (cuenta.saldo().signum() != 0) {
            throw new ReglaNegocioException("La cuenta " + id + " tiene saldo $" + cuenta.saldo().toPlainString()
                    + ": debe quedar en $0 (transfiera o pague el saldo) antes de cerrarla");
        }
        Cuenta cerrada = cuentas.cambiarEstado(id, Cuenta.CERRADA, motivo);
        outbox.registrar(Topicos.CUENTAS_EVENTOS, id.toString(), CuentaEvento.de("CuentaCerrada", cerrada, motivo));
        log.info("[CUENTA] Cuenta {} CERRADA: {}", id, motivo);
        return cerrada;
    }

    private Cuenta buscarParaActualizar(Long id) {
        return cuentas.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta " + id + " no existe"));
    }
}
