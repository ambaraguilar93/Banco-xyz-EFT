package com.duoc.bancoxyz.clientes.cliente;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.clientes.comun.RecursoNoEncontradoException;
import com.duoc.bancoxyz.clientes.comun.ReglaNegocioException;
import com.duoc.bancoxyz.clientes.mensajeria.Topicos;
import com.duoc.bancoxyz.clientes.mensajeria.eventos.ClienteEvento;
import com.duoc.bancoxyz.clientes.outbox.Outbox;

/**
 * Datos personales de los clientes. Cada cambio publica (via outbox) un evento
 * en banco.clientes.eventos para que ms-cuentas actualice su replica, si un
 * cliente se desactiva, ms-cuentas bloquea sus cuentas.
 */
@Service
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clientes;
    private final Outbox outbox;

    public ClienteService(ClienteRepository clientes, Outbox outbox) {
        this.clientes = clientes;
        this.outbox = outbox;
    }

    @CircuitBreaker(name = "clientesDb")
    public Cliente obtener(Long id) {
        return clientes.buscar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente " + id + " no existe"));
    }

    @CircuitBreaker(name = "clientesDb")
    public List<Cliente> listar(int limite) {
        return clientes.listar(limite);
    }

    @Transactional
    @CircuitBreaker(name = "clientesDb")
    public Cliente registrar(ClienteRequest solicitud) {
        if (clientes.existeRut(solicitud.rut())) {
            throw new ReglaNegocioException("Ya existe un cliente con el RUT " + solicitud.rut());
        }
        Cliente cliente = clientes.insertar(solicitud);
        outbox.registrar(Topicos.CLIENTES_EVENTOS, cliente.id().toString(),
                ClienteEvento.de("ClienteRegistrado", cliente));
        log.info("[CLIENTE] Cliente {} registrado ({})", cliente.id(), cliente.nombre());
        return cliente;
    }

    @Transactional
    @CircuitBreaker(name = "clientesDb")
    public Cliente actualizar(Long id, ActualizacionClienteRequest solicitud) {
        Cliente cliente = clientes.actualizar(id, solicitud)
                .orElseThrow(() -> new RecursoNoEncontradoException("El cliente " + id + " no existe"));
        outbox.registrar(Topicos.CLIENTES_EVENTOS, id.toString(), ClienteEvento.de("ClienteActualizado", cliente));
        log.info("[CLIENTE] Cliente {} actualizado", id);
        return cliente;
    }

    @Transactional
    @CircuitBreaker(name = "clientesDb")
    public Cliente desactivar(Long id, String motivo) {
        Cliente actual = obtener(id);
        if (Cliente.INACTIVO.equals(actual.estado())) {
            throw new ReglaNegocioException("El cliente " + id + " ya esta INACTIVO");
        }
        Cliente cliente = clientes.cambiarEstado(id, Cliente.INACTIVO, motivo);
        outbox.registrar(Topicos.CLIENTES_EVENTOS, id.toString(), ClienteEvento.de("ClienteDesactivado", cliente));
        log.info("[CLIENTE] Cliente {} DESACTIVADO: {}", id, motivo);
        return cliente;
    }

    @Transactional
    @CircuitBreaker(name = "clientesDb")
    public Cliente reactivar(Long id) {
        Cliente actual = obtener(id);
        if (Cliente.ACTIVO.equals(actual.estado())) {
            throw new ReglaNegocioException("El cliente " + id + " ya esta ACTIVO");
        }
        Cliente cliente = clientes.cambiarEstado(id, Cliente.ACTIVO, null);
        outbox.registrar(Topicos.CLIENTES_EVENTOS, id.toString(), ClienteEvento.de("ClienteReactivado", cliente));
        log.info("[CLIENTE] Cliente {} REACTIVADO", id);
        return cliente;
    }
}
